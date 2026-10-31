package com.motionloop.app.feature.home

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.domain.model.DemoPhoto
import com.motionloop.app.domain.model.DemoPhotos
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.domain.repository.MediaRepository
import com.motionloop.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentProject(val id: String, val thumbnail: Bitmap?)

data class DemoThumb(val photo: DemoPhoto, val thumbnail: Bitmap)

data class HomeUiState(
    val recent: List<RecentProject> = emptyList(),
    /** Only samples whose asset is present (a local build without the download has none). */
    val demos: List<DemoThumb> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
)

sealed interface HomeEvent {
    data class OpenEditor(val projectId: String) : HomeEvent
    data class ShareApk(val uri: Uri) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val media: MediaRepository,
    entitlements: Entitlements,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val isPro: StateFlow<Boolean> = entitlements.isPro
    val canShareApk: Boolean = media.canShareApk

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Ids deleted in this session. Never cleared on success: a refresh that read the disk before
    // the delete finished must not bring them back. UUIDs, so the set stays tiny.
    private val deleting = mutableSetOf<String>()

    // A thumbnail is written once, when its project is created, so a loaded one stays valid.
    // Main thread only (viewModelScope).
    private val thumbs = HashMap<String, Bitmap>()

    init {
        viewModelScope.launch { media.pruneCaptures() }
        viewModelScope.launch {
            val demos = DemoPhotos.all.mapNotNull { photo ->
                projects.loadAssetThumbnail(photo.assetPath, DEMO_THUMB_EDGE)?.let { DemoThumb(photo, it) }
            }
            _state.update { it.copy(demos = demos) }
        }
    }

    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Back from the editor, its last save may still be on its way: sort by the written files.
            projects.flush()
            val ids = projects.recentIds().filterNot { it in deleting }
            val list = ids.map { id ->
                RecentProject(id, thumbs[id] ?: projects.loadThumbnail(id)?.also { thumbs[id] = it })
            }
            thumbs.keys.retainAll(ids.toSet())
            _state.update { s -> s.copy(recent = list.filterNot { it.id in deleting }) }
        }
    }

    fun importPhoto(uri: Uri) = create("Could not open that photo") { projects.createFromImage(uri).id }

    fun openDemo(photo: DemoPhoto) = create("Could not open the sample") { projects.createFromAsset(photo.assetPath).id }

    private fun create(failure: String, block: suspend () -> String) {
        if (_state.value.importing) return
        _state.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                _events.send(HomeEvent.OpenEditor(block()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, failure, e)
                _state.update { it.copy(error = failure) }
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    /** Copies the installed APK somewhere shareable, then asks the screen to open the share sheet. */
    fun shareApk() {
        if (_state.value.importing) return
        // The copy takes a few seconds for a large APK: reuse the busy overlay.
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            try {
                _events.send(HomeEvent.ShareApk(media.shareableApk()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not prepare the app file", e)
                _state.update { it.copy(error = "Could not prepare the app file.") }
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun delete(id: String) {
        // Optimistic: drop it from the grid now, then delete the files.
        deleting += id
        thumbs.remove(id)
        _state.update { s -> s.copy(recent = s.recent.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                projects.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The files may still be there, so let the next refresh show the project again.
                deleting -= id
                _state.update { it.copy(error = "Could not delete the project") }
                refresh()
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        // Two columns of cards: about half the screen width at high density.
        const val DEMO_THUMB_EDGE = 540
    }
}
