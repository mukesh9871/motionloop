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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private val saveScope = CoroutineScope(SupervisorJob() + serialIo)
    private val pending = ConcurrentHashMap<String, Project>()

    private fun dir(id: String) = projectDir(context, id)

    override suspend fun createFromImage(source: Uri): Project = withContext(io) {
        val bitmap = BitmapDecoder.decode(context.contentResolver, source, MAX_STORED_EDGE)
        val id = UUID.randomUUID().toString()
        val dir = dir(id).apply { mkdirs() }
        try {
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
        val file = File(dir(id), JSON)
        if (!file.exists()) null else runCatching { json.decodeFromString<Project>(file.readText()) }.getOrNull()
    }

    override fun saveInBackground(project: Project) {
        // Coalesce: a slider drag queues many saves, but each queued task writes the newest
        // pending version and later tasks for the same id find nothing left to do.
        pending[project.id] = project
        saveScope.launch {
            val latest = pending.remove(project.id) ?: return@launch
            try {
                writeJson(latest.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                // Never let a failed save crash the app from this scope. The previous file stays intact.
                Log.e(TAG, "Saving project ${latest.id} failed", e)
            }
        }
    }

    // The serial dispatcher runs queued blocks in order, so this returns after every earlier save.
    override suspend fun flush() = withContext(serialIo) { }

    override suspend fun recent(limit: Int): List<Project> = withContext(io) {
        File(context.filesDir, PROJECTS).listFiles().orEmpty()
            .mapNotNull { d -> File(d, JSON).takeIf { it.exists() } }
            .mapNotNull { runCatching { json.decodeFromString<Project>(it.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    override suspend fun loadImage(id: String, maxEdge: Int): Bitmap = withContext(io) {
        BitmapDecoder.decode(File(dir(id), SOURCE), maxEdge)
    }

    override suspend fun loadThumbnail(id: String): Bitmap? = withContext(io) {
        BitmapFactory.decodeFile(File(dir(id), THUMB).path)
    }

    override suspend fun delete(id: String) {
        pending.remove(id)
        // On the serial writer: every earlier save finishes first, none can run after and recreate files.
        withContext(serialIo) { dir(id).deleteRecursively() }
    }

    private fun writeJson(project: Project) {
        // Write then rename, so a crash mid-write never corrupts the project.
        val dir = dir(project.id)
        val tmp = File(dir, "$JSON.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        if (!tmp.renameTo(File(dir, JSON))) throw IOException("Could not replace $JSON")
    }

    companion object {
        private const val TAG = "ProjectRepository"
        const val MAX_STORED_EDGE = 4096
        private const val PROJECTS = "projects"

        /** Folder holding everything for one project. Shared with other repositories (custom LUT file). */
        fun projectDir(context: Context, id: String) = File(File(context.filesDir, PROJECTS), id)
        private const val THUMB_EDGE = 320
        private const val SOURCE = "source.jpg"
        private const val THUMB = "thumb.jpg"
        private const val JSON = "project.json"
    }
}
