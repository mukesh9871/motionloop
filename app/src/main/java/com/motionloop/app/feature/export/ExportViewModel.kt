package com.motionloop.app.feature.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.domain.model.ExportAspect
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.data.settings.AppSettings
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.domain.repository.MediaRepository
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.gif.GifExporter
import com.motionloop.app.export.png.PngExporter
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
    /** The project has a sound, so MP4 can include it. */
    val hasSound: Boolean = false,
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
    private val pngExporter: PngExporter,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
    entitlements: Entitlements,
    private val settings: AppSettings,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = entitlements.isPro

    private val projectId: String = checkNotNull(savedState[Routes.ARG_PROJECT_ID])
    private val _state = MutableStateFlow(ExportUiState(settings = ExportSettings(resolution = settings.state.value.defaultResolution)))
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private var job: Job? = null
    // Above init, which sets them.
    private var photoAspect = 1f
    private var resolutionJob: Job? = null

    init {
        viewModelScope.launch {
            val project = projects.load(projectId) ?: return@launch
            photoAspect = project.aspect
            _state.update { it.copy(hasSound = project.audio.hasSound) }
            refreshResolutions()
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
    fun setAspect(a: ExportAspect) {
        updateSettings { it.copy(aspect = a) }
        refreshResolutions()
    }

    /** Offer 4K only where the hardware encoder supports it for the chosen shape. Codec queries are slow. */
    private fun refreshResolutions() {
        resolutionJob?.cancel()
        val aspect = _state.value.settings.aspect.widthOverHeight(photoAspect)
        resolutionJob = viewModelScope.launch {
            val available = withContext(cpu) {
                ExportResolution.entries.filter { it != ExportResolution.UHD || VideoExporter.supportsNominal(aspect, it) }
            }
            _state.update { s ->
                val keep = s.settings.resolution in available
                s.copy(
                    resolutions = available,
                    settings = if (keep) s.settings else s.settings.copy(resolution = ExportResolution.FULL_HD),
                )
            }
        }
    }
    fun setIncludeSound(on: Boolean) = updateSettings { it.copy(includeSound = on) }

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
            val (mime, ext) = when (settings.format) {
                ExportFormat.MP4 -> "video/mp4" to ".mp4"
                ExportFormat.GIF -> "image/gif" to ".gif"
                ExportFormat.PNG -> "image/png" to ".png"
            }
            val name = "MotionLoop_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ext
            val file = media.exportTempFile(name)
            try {
                // Wait for the editor's queued saves, then read the latest state from disk.
                projects.flush()
                val project = projects.load(projectId) ?: error("Project not found")
                val onProgress: (Float) -> Unit = { p -> _state.update { it.copy(progress = p) } }
                when (settings.format) {
                    ExportFormat.MP4 -> videoExporter.export(project, settings, file, onProgress)
                    ExportFormat.GIF -> gifExporter.export(project, settings, file, onProgress)
                    ExportFormat.PNG -> pngExporter.export(project, file, onProgress)
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

    /** Remembers this project for the live wallpaper. The screen then opens the system picker. */
    fun chooseForWallpaper() {
        // Set before the system picker opens, so its preview already shows this project.
        settings.setWallpaperProject(projectId)
        // Once queued edits are on disk, a running wallpaper reloads (also when it already shows this project).
        viewModelScope.launch {
            projects.flush()
            settings.bumpWallpaperRevision()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
