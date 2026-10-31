package com.motionloop.app.data.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.core.image.BitmapDecoder
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.motionloop.app.data.writeAtomically
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * One folder per project under filesDir/projects:
 * project.json, source.jpg (upright, max 4096 px), thumb.jpg.
 */
class ProjectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProjectRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // One writer at a time, in call order. Lives as long as the app, not the screen.
    private val serialIo = io.limitedParallelism(1)

    // A slider drag or typing saves on every change: wait for the burst to end, then write once.
    private val saves = SaveQueue<Project>(CoroutineScope(SupervisorJob() + io), serialIo, SAVE_DELAY_MS) { project ->
        try {
            writeJson(project.copy(updatedAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            // Never let a failed save crash the app from this scope. The previous file stays intact.
            Log.e(TAG, "Saving project ${project.id} failed", e)
        }
    }

    private fun dir(id: String) = projectDir(context, id)

    override suspend fun createFromImage(source: Uri): Project = withContext(io) {
        create(BitmapDecoder.decode(context.contentResolver, source, MAX_STORED_EDGE))
    }

    override suspend fun createFromAsset(path: String): Project = withContext(io) {
        create(BitmapDecoder.decode(MAX_STORED_EDGE) { context.assets.open(path) })
    }

    override suspend fun loadAssetThumbnail(path: String, maxEdge: Int): Bitmap? = withContext(io) {
        try {
            BitmapDecoder.decode(maxEdge) { context.assets.open(path) }
        } catch (e: IOException) {
            null
        }
    }

    /** Stores [bitmap] (recycled afterwards) as a new project. Runs on IO. */
    private fun create(bitmap: Bitmap): Project {
        val id = UUID.randomUUID().toString()
        val dir = dir(id).apply { mkdirs() }
        return try {
            File(dir, SOURCE).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val thumb = BitmapDecoder.scaleToLongEdge(bitmap, THUMB_EDGE)
            File(dir, THUMB).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val now = System.currentTimeMillis()
            Project(
                id = id,
                createdAt = now,
                updatedAt = now,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            ).also { writeJson(it) }
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun load(id: String): Project? = withContext(io) {
        val dir = dir(id)
        // A damaged file (power loss mid-save) falls back to the previous version.
        listOf(File(dir, JSON), File(dir, BACKUP)).firstNotNullOfOrNull { file ->
            if (!file.exists()) null else runCatching { json.decodeFromString<Project>(file.readText()) }.getOrNull()
        }
    }

    override fun saveInBackground(project: Project) = saves.submit(project.id, project)

    override fun writePending() = saves.writePending()

    override suspend fun flush() = saves.flush()

    override suspend fun recentIds(limit: Int): List<String> = withContext(io) {
        // Newest first by file time: every save rewrites project.json, so nothing has to be parsed.
        File(context.filesDir, PROJECTS).listFiles().orEmpty()
            .mapNotNull { d ->
                // The backup too: a project whose last write was interrupted still opens from it.
                val file = File(d, JSON).takeIf { it.isFile } ?: File(d, BACKUP).takeIf { it.isFile }
                file?.let { d.name to it.lastModified() }
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    override suspend fun loadImage(id: String, maxEdge: Int): Bitmap = withContext(io) {
        BitmapDecoder.decode(File(dir(id), SOURCE), maxEdge)
    }

    override suspend fun loadThumbnail(id: String): Bitmap? = withContext(io) {
        val path = File(dir(id), THUMB).path
        // Only displayed, never read back: a hardware bitmap keeps the pixels out of the Java heap.
        val hardware = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        BitmapFactory.decodeFile(path, hardware) ?: BitmapFactory.decodeFile(path)
    }

    override suspend fun delete(id: String) {
        saves.cancel(id)
        // On the serial writer: every earlier save finishes first, none can run after and recreate files.
        withContext(serialIo) { dir(id).deleteRecursively() }
    }

    private fun writeJson(project: Project) {
        val dir = dir(project.id)
        val target = File(dir, JSON)
        // The previous version stays as a backup for load() in case the new file is unreadable.
        target.writeAtomically(backup = File(dir, BACKUP)) { it.write(json.encodeToString(Project.serializer(), project).toByteArray()) }
    }

    companion object {
        private const val TAG = "ProjectRepository"
        private const val SAVE_DELAY_MS = 400L
        const val MAX_STORED_EDGE = 4096
        private const val PROJECTS = "projects"
        private const val BACKUP = "project.json.bak"

        /** Folder holding everything for one project. Shared with other repositories (custom LUT file). */
        fun projectDir(context: Context, id: String) = File(File(context.filesDir, PROJECTS), id)
        private const val THUMB_EDGE = 320
        private const val SOURCE = "source.jpg"
        private const val THUMB = "thumb.jpg"
        private const val JSON = "project.json"
    }
}
