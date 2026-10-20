package com.motionloop.app.data.color

import android.content.Context
import android.net.Uri
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.data.project.ProjectRepositoryImpl
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.ColorGrades
import com.motionloop.app.domain.model.CubeLutParser
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.repository.LutRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in grades are baked once per process and cached. A custom grade is the project's
 * `grade.cube`, parsed on IO each time it is resolved (small, and it may have been replaced).
 */
@Singleton
class LutRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
) : LutRepository {

    private val baked = ConcurrentHashMap<String, LutData>()

    override suspend fun resolve(projectId: String, adjustments: Adjustments): LutData? {
        val id = adjustments.gradeId ?: return null
        if (id == Adjustments.CUSTOM_LUT_ID) {
            return withContext(io) {
                val file = cubeFile(projectId)
                if (!file.exists()) null else runCatching { file.reader().use(CubeLutParser::parse) }.getOrNull()
            }
        }
        baked[id]?.let { return it }
        val grade = ColorGrades.byId(id) ?: return null
        return withContext(cpu) { LutData.bake(transform = grade.transform) }.also { baked[id] = it }
    }

    override suspend fun importCube(projectId: String, source: Uri) = withContext(io) {
        val text = context.contentResolver.openInputStream(source)?.use { input ->
            // Real .cube files are at most a few MB (65^3 lines). Refuse anything far larger.
            val bytes = input.readNBytesCompat(MAX_CUBE_BYTES + 1)
            if (bytes.size > MAX_CUBE_BYTES) throw IOException("That file is too large to be a .cube LUT")
            String(bytes, Charsets.UTF_8)
        } ?: throw IOException("Cannot open the file")
        try {
            CubeLutParser.parse(text.reader())
        } catch (e: CubeLutParser.CubeFormatException) {
            throw IOException("Not a valid .cube LUT: ${e.message}", e)
        }
        val target = cubeFile(projectId)
        val tmp = File(target.parentFile, "$CUBE_NAME.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) throw IOException("Could not store the LUT")
    }

    override suspend fun hasCustom(projectId: String): Boolean = withContext(io) { cubeFile(projectId).exists() }

    private fun cubeFile(projectId: String) = File(ProjectRepositoryImpl.projectDir(context, projectId), CUBE_NAME)

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val CUBE_NAME = "grade.cube"
        const val MAX_CUBE_BYTES = 16 * 1024 * 1024
    }
}
