package com.motionloop.app.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.domain.repository.GalleryItem
import com.motionloop.app.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MediaRepository {

    override val canShareApk: Boolean
        // Installs from an app bundle are split into several APKs. base.apk alone would not install.
        get() = context.applicationInfo.splitSourceDirs.isNullOrEmpty()

    override suspend fun shareableApk(): Uri = withContext(io) {
        if (!canShareApk) throw IOException("This install is split into several files and cannot be shared as one")
        val dir = File(context.cacheDir, SHARE_DIR).apply {
            // Only ever one shared copy. StorageSweeper removes it once the receiving app had time to read it.
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, "MotionLoop.apk")
        File(context.applicationInfo.sourceDir).copyTo(target, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    override suspend fun createGalleryItem(mimeType: String, displayName: String): GalleryItem = withContext(io) {
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$ALBUM")
                // Hidden from galleries until published. One left by a killed export expires on its own.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
            PendingItem(uri)
        } else {
            // Android 8 and 9: write the file ourselves (needs WRITE_EXTERNAL_STORAGE), then index it.
            @Suppress("DEPRECATION")
            val album = File(Environment.getExternalStoragePublicDirectory(folder), ALBUM)
            if (!album.isDirectory && !album.mkdirs()) throw IOException("Cannot create $album")
            PublicFileItem(album, displayName, mimeType, collection)
        }
    }

    /** Android 10 and later: a pending MediaStore entry, written through the content resolver. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private inner class PendingItem(private val uri: Uri) : GalleryItem {
        @Volatile private var published = false

        override fun openStream(): OutputStream =
            context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Cannot open $uri")

        override fun openDescriptor(): ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(uri, "rw") ?: throw IOException("Cannot open $uri")

        override suspend fun publish(): Uri = withContext(io) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (context.contentResolver.update(uri, values, null, null) == 0) throw IOException("Cannot publish $uri")
            published = true
            uri
        }

        override suspend fun discard() = withContext(io) {
            if (!published) context.contentResolver.delete(uri, null, null)
            Unit
        }
    }

    /**
     * Android 8 and 9: a hidden file next to its final name, renamed and indexed on publish, so a
     * half-written export never shows in a gallery.
     */
    private inner class PublicFileItem(
        private val album: File,
        private val displayName: String,
        private val mimeType: String,
        private val collection: Uri,
    ) : GalleryItem {
        private val partial = File(album, ".$displayName$PART_SUFFIX")
        @Volatile private var published = false

        override fun openStream(): OutputStream = FileOutputStream(partial)

        override fun openDescriptor(): ParcelFileDescriptor = ParcelFileDescriptor.open(
            partial,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        )

        override suspend fun publish(): Uri = withContext(io) {
            // Two exports in the same second share a name: a rename would replace the earlier file.
            var target = File(album, displayName)
            var n = 1
            while (target.exists()) {
                target = File(album, "${displayName.substringBeforeLast('.')}_${n++}.${displayName.substringAfterLast('.')}")
            }
            if (!partial.renameTo(target)) throw IOException("Cannot move the export into $album")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, target.absolutePath)
            }
            try {
                val uri = context.contentResolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
                published = true
                uri
            } catch (e: Exception) {
                // The export reports a failure: no finished file may stay behind for the scanner to show.
                target.delete()
                throw e
            }
        }

        override suspend fun discard() = withContext(io) {
            if (!published) partial.delete()
            Unit
        }
    }

    companion object {
        const val SHARE_DIR = "share"
        const val ALBUM = "MotionLoop"

        /** Suffix of the hidden file an export writes on Android 8 and 9 before it is published. */
        const val PART_SUFFIX = ".part"
    }
}
