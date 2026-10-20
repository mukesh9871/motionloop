package com.motionloop.app.feature.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.domain.repository.MediaRepository
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.gif.GifExporter
import com.motionloop.app.export.video.VideoExporter
import com.motionloop.app.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val projects: ProjectRepository,
    private val media: MediaRepository,
    private val videoExporter: VideoExporter,
    private val gifExporter: GifExporter,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedState[Routes.ARG_PROJECT_ID])
    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val project = projects.load(projectId) ?: return@launch
            // Offer 4K only where the hardware encoder really supports it. Codec queries are slow.
            val available = withContext(cpu) {
                ExportResolution.entries.filter {
                    it != ExportResolution.UHD || VideoExporter.supportsNominal(project, it)
                }
            }
            _state.update { it.copy(resolutions = available) }
        }
    }

    /** Called each time the sheet opens, so a finished export from before does not hide the Export button. */
    fun reset() {
        if (_state.value.running) return
        _state.update { it.copy(progress = 0f, resultUri = null, resultMime = null, error = null) }
    }

    fun setFormat(format: ExportFormat) = updateSettings {
        val lengths = if (format == ExportFormat.GIF) ExportSettings.GIF_LENGTHS else ExportSettings.VIDEO_LENGTHS
        it.copy(format = format, lengthSeconds = if (it.lengthSeconds in lengths) it.lengthSeconds else lengths.first())
    }

    fun setLength(seconds: Int) = updateSettings { it.copy(lengthSeconds = seconds) }
    fun setResolution(r: ExportResolution) = updateSettings { it.copy(resolution = r) }

    fun onStoragePermissionDenied() =
        _state.update { it.copy(error = "Storage permission is needed to save to your gallery.") }

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
            val file = media.exportTempFile(name)
            try {
                // Wait for the editor's queued saves, then read the latest state from disk.
                projects.flush()
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
                withContext(NonCancellable + cpu) { file.delete() }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
