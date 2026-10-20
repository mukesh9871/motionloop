package com.motionloop.app.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.motionloop.app.domain.model.Project
import java.io.File

interface ProjectRepository {
    /** Copies the photo into app storage and creates a new project. */
    suspend fun createFromImage(source: Uri): Project
    suspend fun load(id: String): Project?
    suspend fun save(project: Project)
    suspend fun recent(limit: Int = 12): List<Project>

    /** Decodes the project photo with its long edge at most [maxEdge]. */
    suspend fun loadImage(id: String, maxEdge: Int): Bitmap
    suspend fun loadThumbnail(id: String): Bitmap?
}

interface MediaRepository {
    /** Copies [file] into the shared gallery and returns its content Uri. */
    suspend fun saveToGallery(file: File, mimeType: String, displayName: String): Uri
}
