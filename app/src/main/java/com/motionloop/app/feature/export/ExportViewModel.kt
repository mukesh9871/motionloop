package com.motionloop.app.feature.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.domain.repository.MediaRepository
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.gif.GifExporter
import com.motionloop.app.export.video.VideoExporter
import com.motionloop.app.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val settings: ExportSettings = ExportSettings(),
    val resolutions: List<ExportResolution> = listOf(ExportResolution.HD, ExportResolution.FULL_HD),
    val running: Boolean = false,
    val progress: Float = 0f,
    val resultUri: Uri? = null,
    val resultMime: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
    private val media: MediaRepository,
    private val videoExporter: VideoExporter,
    private val gifExporter: GifExporter,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedState[Routes.ARG_PROJECT_ID])
    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val project = projects.load(projectId) ?: return@launch
            // Offer 4K only where the hardware encoder really supports it.
            val available = ExportResolution.entries.filter {
                it != ExportResolution.UHD || VideoExporter.supportsNominal(project, it)
            }
            _state.update { it.copy(resolutions = available) }
        }
    }

    fun setFormat(format: ExportFormat) = updateSettings {
        val lengths = if (format == ExportFormat.GIF) ExportSettings.GIF_LENGTHS else ExportSettings.VIDEO_LENGTHS
        it.copy(format = format, lengthSeconds = if (it.lengthSeconds in lengths) it.lengthSeconds else lengths.first())
    }

    fun setLength(seconds: Int) = updateSettings { it.copy(lengthSeconds = seconds) }
    fun setResolution(r: ExportResolution) = updateSettings { it.copy(resolution = r) }

    private fun updateSettings(change: (ExportSettings) -> ExportSettings) {
        if (_state.value.running) return
        _state.update { it.copy(settings = change(it.settings), resultUri = null, error = null) }
    }

    fun start() {
        if (_state.value.running) return
        val settings = _state.value.settings
        _state.update { it.copy(running = true, progress = 0f, resultUri = null, error = null) }
        job = viewModelScope.launch {
            val isGif = settings.format == ExportFormat.GIF
            val mime = if (isGif) "image/gif" else "video/mp4"
            val name = "MotionLoop_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                if (isGif) ".gif" else ".mp4"
            val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, name)
            try {
                // Re-read from disk: the editor saves every edit, so this is the latest state.
                val project = projects.load(projectId) ?: error("Project not found")
                val onProgress: (Float) -> Unit = { p -> _state.update { it.copy(progress = p) } }
                if (isGif) {
                    gifExporter.export(project, settings, file, onProgress)
                } else {
                    videoExporter.export(project, settings, file, onProgress)
                }
                val uri = media.saveToGallery(file, mime, name)
                _state.update { it.copy(running = false, progress = 1f, resultUri = uri, resultMime = mime) }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = false, progress = 0f) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.message ?: "Export failed") }
            } finally {
                file.delete()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
