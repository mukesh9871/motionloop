package com.motionloop.app.data.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.core.image.BitmapDecoder
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
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

    private val root: File get() = File(context.filesDir, "projects").apply { mkdirs() }
    private fun dir(id: String) = File(root, id)

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

    override suspend fun save(project: Project) = withContext(io) {
        writeJson(project.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun recent(limit: Int): List<Project> = withContext(io) {
        root.listFiles().orEmpty()
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

    private fun writeJson(project: Project) {
        // Write then rename, so a crash mid-write never corrupts the project.
        val dir = dir(project.id)
        val tmp = File(dir, "$JSON.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        tmp.renameTo(File(dir, JSON))
    }

    companion object {
        const val MAX_STORED_EDGE = 4096
        private const val THUMB_EDGE = 320
        private const val SOURCE = "source.jpg"
        private const val THUMB = "thumb.jpg"
        private const val JSON = "project.json"
    }
}
