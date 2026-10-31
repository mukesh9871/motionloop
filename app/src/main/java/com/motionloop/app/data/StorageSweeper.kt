package com.motionloop.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.data.media.MediaIntents
import com.motionloop.app.data.media.MediaStoreSaver
import com.motionloop.app.data.project.ProjectRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Removes files that only stay behind when the process dies mid-write, plus the shared APK copy.
 * Normal flows clean up after themselves. Run once per app start, off the main thread.
 */
class StorageSweeper @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun sweep() = withContext(io) {
        MediaIntents.pruneCaptures(context)
        // Only old files: a write running right now has a fresh one.
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        File(context.filesDir, ProjectRepositoryImpl.PROJECTS).listFiles().orEmpty().forEach { dir ->
            dir.listFiles().orEmpty()
                .filter { it.isFile && isLeftover(it.name) && it.lastModified() < cutoff }
                .forEach { it.delete() }
        }
        // The app is 100 MB and more. The receiving app has read it long before the cutoff.
        File(context.cacheDir, MediaStoreSaver.SHARE_DIR).listFiles().orEmpty()
            .filter { it.lastModified() < cutoff }
            .forEach { it.delete() }
        // Exports were staged here before they were written straight into the gallery.
        File(context.cacheDir, "exports").deleteRecursively()
        sweepUnfinishedExports(cutoff)
    }

    /**
     * Android 8 and 9: a killed export leaves its hidden file in the public album, where it would
     * outlive the app. Android 10 and later expire unfinished gallery entries on their own.
     */
    private fun sweepUnfinishedExports(cutoff: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (granted != PackageManager.PERMISSION_GRANTED) return
        for (folder in listOf(Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES)) {
            @Suppress("DEPRECATION")
            val album = File(Environment.getExternalStoragePublicDirectory(folder), MediaStoreSaver.ALBUM)
            album.listFiles().orEmpty()
                .filter { it.name.startsWith(".") && it.name.endsWith(MediaStoreSaver.PART_SUFFIX) && it.lastModified() < cutoff }
                .forEach { it.delete() }
        }
    }

    internal companion object {
        const val MAX_AGE_MS = 60L * 60 * 1000

        /** Temp files of atomic writes and audio imports, and staged image imports. */
        fun isLeftover(name: String): Boolean = name.endsWith(".tmp") || name.contains("_import_")
    }
}
