package com.motionloop.app.data.project

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.core.image.BitmapDecoder
import com.motionloop.app.domain.model.MaskCodec
import com.motionloop.app.domain.model.MaskKind
import com.motionloop.app.domain.repository.ImageKind
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.ml.AlphaMask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Files live next to project.json: `sky_3.mask`, `sky_photo_1.jpg` and so on. */
@Singleton
class ProjectAssetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProjectAssetRepository {

    // Revision numbers are read then written: one writer at a time keeps them unique.
    private val lock = Mutex()

    override suspend fun saveMask(projectId: String, kind: MaskKind, mask: AlphaMask): Int = lock.withLock {
        withContext(io) {
            val dir = dir(projectId)
            val rev = nextRev(dir, kind.prefix, MASK_EXT)
            val target = File(dir, "${kind.prefix}_$rev$MASK_EXT")
            val tmp = File(dir, "${target.name}.tmp")
            tmp.outputStream().buffered().use { MaskCodec.write(mask, it) }
            if (!tmp.renameTo(target)) throw IOException("Could not store the mask")
            rev
        }
    }

    override suspend fun loadMask(projectId: String, kind: MaskKind, rev: Int): AlphaMask? = withContext(io) {
        if (rev <= 0) return@withContext null
        val file = File(dir(projectId), "${kind.prefix}_$rev$MASK_EXT")
        if (!file.exists()) null else runCatching { file.inputStream().buffered().use(MaskCodec::read) }.getOrNull()
    }

    override suspend fun importImage(projectId: String, kind: ImageKind, source: Uri): Int = lock.withLock {
        withContext(io) {
            val bitmap = BitmapDecoder.decode(context.contentResolver, source, MAX_IMAGE_EDGE)
            try {
                val dir = dir(projectId)
                val rev = nextRev(dir, kind.prefix, IMAGE_EXT)
                val target = File(dir, "${kind.prefix}_$rev$IMAGE_EXT")
                val tmp = File(dir, "${target.name}.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                if (!tmp.renameTo(target)) throw IOException("Could not store the image")
                rev
            } finally {
                bitmap.recycle()
            }
        }
    }

    override suspend fun loadImage(projectId: String, kind: ImageKind, rev: Int, maxEdge: Int): Bitmap? = withContext(io) {
        if (rev <= 0) return@withContext null
        val file = File(dir(projectId), "${kind.prefix}_$rev$IMAGE_EXT")
        if (!file.exists()) null else runCatching { BitmapDecoder.decode(file, maxEdge) }.getOrNull()
    }

    override suspend fun prune(projectId: String, kind: MaskKind, keep: Set<Int>) = lock.withLock {
        withContext(io) {
            dir(projectId).listFiles().orEmpty()
                .filter { revOf(it.name, kind.prefix, MASK_EXT)?.let { rev -> rev !in keep } == true }
                .forEach { it.delete() }
        }
    }

    override suspend fun pruneImages(projectId: String, kind: ImageKind, keep: Set<Int>) = lock.withLock {
        withContext(io) {
            dir(projectId).listFiles().orEmpty()
                .filter { revOf(it.name, kind.prefix, IMAGE_EXT)?.let { rev -> rev !in keep } == true }
                .forEach { it.delete() }
        }
    }

    private fun dir(projectId: String) = ProjectRepositoryImpl.projectDir(context, projectId)

    private fun nextRev(dir: File, prefix: String, ext: String): Int =
        (dir.listFiles().orEmpty().mapNotNull { revOf(it.name, prefix, ext) }.maxOrNull() ?: 0) + 1

    companion object {
        private const val MASK_EXT = ".mask"
        private const val IMAGE_EXT = ".jpg"
        private const val MAX_IMAGE_EDGE = 2048

        /** "sky_12.mask" -> 12 for prefix "sky". "sky_photo_3.jpg" is not a "sky" mask. Internal for tests. */
        fun revOf(name: String, prefix: String, ext: String): Int? {
            if (!name.startsWith("${prefix}_") || !name.endsWith(ext)) return null
            return name.substring(prefix.length + 1, name.length - ext.length).toIntOrNull()?.takeIf { it > 0 }
        }
    }
}
