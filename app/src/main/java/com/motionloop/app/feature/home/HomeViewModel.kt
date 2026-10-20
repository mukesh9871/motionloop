package com.motionloop.app.feature.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentProject(val id: String, val thumbnail: Bitmap?)

data class HomeUiState(
    val recent: List<RecentProject> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
)

sealed interface HomeEvent {
    data class OpenEditor(val projectId: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projects: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun refresh() {
        viewModelScope.launch {
            val list = projects.recent().map { RecentProject(it.id, projects.loadThumbnail(it.id)) }
            _state.update { it.copy(recent = list) }
        }
    }

    fun importPhoto(uri: Uri) {
        if (_state.value.importing) return
        _state.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                val project = projects.createFromImage(uri)
                _events.send(HomeEvent.OpenEditor(project.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not open that photo (${e.message})") }
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun delete(id: String) {
        // Optimistic: drop it from the grid now, then delete the files.
        _state.update { s -> s.copy(recent = s.recent.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                projects.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not delete the project") }
                refresh()
            }
        }
    }
}
