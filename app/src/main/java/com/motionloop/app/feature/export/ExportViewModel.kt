package com.motionloop.app.feature.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.domain.model.ExportAspect
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.data.settings.AppSettings
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.domain.pro.ProFeature
import com.motionloop.app.domain.pro.ProPolicy
import com.motionloop.app.domain.repository.GalleryItem
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
    /** The storage permission was refused: the sheet offers the system settings page. */
    val storageDenied: Boolean = false,
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
    // A 4K default only for those who may use it. Whether the encoder can is checked in start().
    private val _state = MutableStateFlow(
        ExportUiState(
            settings = ExportSettings(
                resolution = settings.state.value.defaultResolution.takeUnless {
                    it == ExportResolution.UHD && ProPolicy.isLocked(ProFeature.EXPORT_4K, entitlements.isPro.value)
                } ?: ExportResolution.FULL_HD,
            ),
        ),
    )
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private var job: Job? = null
    // Above init, which sets them.
    private var photoAspect = 1f
    private var resolutionJob: Job? = null

    private var photoWidth = 0
    private var photoHeight = 0

    init {
        refreshProject()
    }

    /** The project can change between two openings of the sheet (sound added, photo the same). */
    private fun refreshProject() {
        viewModelScope.launch {
            // Editor saves wait a moment for more changes: write them before reading the file.
            projects.flush()
            val project = projects.load(projectId) ?: return@launch
            photoAspect = project.aspect
            photoWidth = project.imageWidth
            photoHeight = project.imageHeight
            _state.update { it.copy(hasSound = project.audio.hasSound) }
            refreshResolutions()
        }
    }

    /** Called each time the sheet opens, so a finished export from before does not hide the Export button. */
    fun reset() {
        if (_state.value.running) return
        _state.update { it.copy(progress = 0f, resultUri = null, resultMime = null, error = null, storageDenied = false) }
        refreshProject()
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
                ExportResolution.entries.filter {
                    it != ExportResolution.UHD || VideoExporter.supportsNominal(aspect, it, photoWidth, photoHeight)
                }
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
        _state.update { it.copy(error = "Storage permission is needed to save to your gallery.", storageDenied = true) }

    private fun updateSettings(change: (ExportSettings) -> ExportSettings) {
        if (_state.value.running) return
        _state.update { it.copy(settings = change(it.settings), resultUri = null, error = null) }
    }

    fun start() {
        if (_state.value.running) return
        // Before the encoder check finishes, the list holds only what every phone can do.
        val chosen = _state.value.settings
        val settings = if (chosen.resolution in _state.value.resolutions) chosen else chosen.copy(resolution = ExportResolution.FULL_HD)
        // Shown as chosen, so the sheet matches the file even after a fallback.
        _state.update { it.copy(settings = settings, running = true, progress = 0f, resultUri = null, error = null, storageDenied = false) }
        job = viewModelScope.launch {
            val (mime, ext) = when (settings.format) {
                ExportFormat.MP4 -> "video/mp4" to ".mp4"
                ExportFormat.GIF -> "image/gif" to ".gif"
                ExportFormat.PNG -> "image/png" to ".png"
            }
            val name = "MotionLoop_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ext
            var item: GalleryItem? = null
            try {
                // Wait for the editor's queued saves, then read the latest state from disk.
                projects.flush()
                val project = projects.load(projectId) ?: error("Project not found")
                // Written straight into the gallery entry, which stays hidden until it is complete.
                // Not cancellable: a cancel landing after the insert would lose the entry's handle.
                val out = withContext(NonCancellable) { media.createGalleryItem(mime, name).also { item = it } }
                val onProgress: (Float) -> Unit = { p -> _state.update { it.copy(progress = p) } }
                when (settings.format) {
                    ExportFormat.MP4 -> videoExporter.export(project, settings, out, onProgress)
                    ExportFormat.GIF -> gifExporter.export(project, settings, out, onProgress)
                    ExportFormat.PNG -> pngExporter.export(project, out, onProgress)
                }
                // Not cancellable: a cancel tapped at the very end must not hide a file that is
                // already in the gallery (and invite a second export of it).
                withContext(NonCancellable) {
                    val uri = out.publish()
                    _state.update { it.copy(running = false, progress = 1f, resultUri = uri, resultMime = mime) }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = false, progress = 0f) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                _state.update { it.copy(running = false, error = "The export failed. Please try again.") }
            } finally {
                // A failed or cancelled export leaves nothing behind. A published one is kept.
                item?.let { pending ->
                    withContext(NonCancellable) {
                        // A provider error here must not crash the app or hide the export's own error.
                        try {
                            pending.discard()
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not remove the unfinished export", e)
                        }
                    }
                }
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

    private companion object {
        const val TAG = "ExportViewModel"
    }
}
