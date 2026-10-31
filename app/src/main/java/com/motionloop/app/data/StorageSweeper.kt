package com.motionloop.app.data

import android.content.Context
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
        Unit
    }

    internal companion object {
        const val MAX_AGE_MS = 60L * 60 * 1000

        /** Temp files of atomic writes and audio imports, and staged image imports. */
        fun isLeftover(name: String): Boolean = name.endsWith(".tmp") || name.contains("_import_")
    }
}
