package com.motionloop.app.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskKind
import com.motionloop.ml.AlphaMask
import com.motionloop.app.domain.model.Project
import java.io.File

interface ProjectRepository {
    /** Copies the photo into app storage and creates a new project. */
    suspend fun createFromImage(source: Uri): Project

    /** Same, from a bundled asset (sample photos). */
    suspend fun createFromAsset(path: String): Project

    /** Small preview of a bundled asset, null if the asset is missing. */
    suspend fun loadAssetThumbnail(path: String, maxEdge: Int): Bitmap?
    suspend fun load(id: String): Project?

    /**
     * Queues a save. It waits a moment for a newer save of the same project (a slider drag writes once),
     * then runs on an app-wide scope, one at a time in call order: saves never interleave and still
     * finish after the editor screen is closed.
     */
    fun saveInBackground(project: Project)

    /** Starts every waiting save now without suspending, for example when the screen stops. */
    fun writePending()

    /** Writes every queued save now and suspends until all are written. Call before reading a project from disk. */
    suspend fun flush()

    /** Ids of the most recently saved projects, newest first. */
    suspend fun recentIds(limit: Int = 12): List<String>

    /** Decodes the project photo with its long edge at most [maxEdge]. */
    suspend fun loadImage(id: String, maxEdge: Int): Bitmap
    suspend fun loadThumbnail(id: String): Bitmap?

    /** Deletes the project folder. Waits for queued saves so a late save cannot recreate it. */
    suspend fun delete(id: String)
}

/** Per-project masks and extra images, stored as numbered revisions so undo can go back to an older one. */
interface ProjectAssetRepository {
    suspend fun saveMask(projectId: String, kind: MaskKind, mask: AlphaMask): Int
    suspend fun loadMask(projectId: String, kind: MaskKind, rev: Int): AlphaMask?

    /** Copies an image (for example a sky photo) into the project, returns its revision. */
    suspend fun importImage(projectId: String, kind: ImageKind, source: Uri): Int
    suspend fun loadImage(projectId: String, kind: ImageKind, rev: Int, maxEdge: Int): Bitmap?

    /** Copies an audio file into the project as is, returns its revision. */
    suspend fun importAudio(projectId: String, source: Uri): Int
    fun audioFile(projectId: String, rev: Int): File?
    suspend fun pruneAudio(projectId: String, keep: Set<Int>)

    /** Deletes revisions of [kind] that are not in [keep]. */
    suspend fun prune(projectId: String, kind: MaskKind, keep: Set<Int>)
    suspend fun pruneImages(projectId: String, kind: ImageKind, keep: Set<Int>)
}

enum class ImageKind(val prefix: String) { SKY_PHOTO("sky_photo"), BACKGROUND("background") }

interface LutRepository {
    /** Baked LUT for the project grade, or null when no grade is set or the file is missing. */
    suspend fun resolve(projectId: String, adjustments: Adjustments): LutData?

    /** Validates and stores a .cube file as the project custom grade. Throws with a readable message if invalid. */
    suspend fun importCube(projectId: String, source: Uri)

    /** True when the project has an imported grade.cube that can be selected again. */
    suspend fun hasCustom(projectId: String): Boolean
}

interface MediaRepository {
    /** Scratch file for an export in progress. The caller deletes it. */
    fun exportTempFile(name: String): File

    /** False for split installs (from an app bundle), where the APK cannot be shared as one file. */
    val canShareApk: Boolean

    /** Removes camera captures from earlier sessions and makes the capture folder. */
    suspend fun pruneCaptures()

    /** Removes export files left by an interrupted export. */
    suspend fun pruneExports()

    /** A content Uri to the installed app's APK, for "share the app" without a store. */
    suspend fun shareableApk(): Uri

    /** Copies [file] into the shared gallery and returns its content Uri. */
    suspend fun saveToGallery(file: File, mimeType: String, displayName: String): Uri
}
