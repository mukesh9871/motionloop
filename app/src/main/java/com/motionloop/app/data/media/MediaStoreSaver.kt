package com.motionloop.app.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MediaRepository {

    // No disk access here (called on main). Exporters create the folder on their own thread.
    override fun exportTempFile(name: String): File = File(File(context.cacheDir, "exports"), name)

    override suspend fun saveToGallery(file: File, mimeType: String, displayName: String): Uri = withContext(io) {
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$ALBUM")
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Cannot open $uri")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            uri
        } else {
            // Android 8 and 9: write the file ourselves (needs WRITE_EXTERNAL_STORAGE), then index it.
            val publicDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
            )
            val target = File(publicDir, ALBUM).apply { mkdirs() }.resolve(displayName)
            file.copyTo(target, overwrite = true)
            @Suppress("DEPRECATION")
            values.put(MediaStore.MediaColumns.DATA, target.absolutePath)
            context.contentResolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        }
    }

    private companion object {
        const val ALBUM = "MotionLoop"
    }
}
