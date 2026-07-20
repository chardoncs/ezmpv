package dev.chardoncs.ezmpv.audio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudioViewModel(app: Application) : AndroidViewModel(app) {

    private val folderRepo = FolderRepository(app)
    private val artCache = ArtCache(app)
    private val copyCache = FileCopyCache(app)
    private val controller = AudioController(app).apply {
        onTrackEnd = { advanceToCurrent() }
    }

    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(selectedFolders = folderRepo.grantedFolders()) }
        // Merge controller's playback state (position, duration, playing, currentIndex)
        // into our UI state. Art/folders/playlist/playlistUserOverride are owned here.
        viewModelScope.launch {
            controller.state.collect { c ->
                _uiState.update { ui ->
                    ui.copy(
                        isPlaying = c.isPlaying,
                        positionMs = c.positionMs,
                        durationMs = c.durationMs,
                        currentIndex = c.currentIndex,
                    )
                }
            }
        }
        refreshPlaylist()
    }

    fun startController() = controller.start()
    fun stopController() = controller.stop()

    fun grantFolder(uri: Uri) {
        folderRepo.grantFolder(uri)
        _uiState.update { it.copy(selectedFolders = folderRepo.grantedFolders()) }
        refreshPlaylist()
    }

    fun revokeFolder(uri: Uri) {
        folderRepo.revokeFolder(uri)
        _uiState.update { it.copy(selectedFolders = folderRepo.grantedFolders()) }
        refreshPlaylist()
    }

    fun refreshPlaylist() {
        val folders = folderRepo.grantedFolders()
        if (folders.isEmpty()) {
            _uiState.update { it.copy(playlist = emptyList(), currentIndex = -1) }
            controller.setPlaylist(emptyList())
            return
        }
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val tracks = folders.flatMap { folderRepo.scanAudio(it) }
            _uiState.update { it.copy(playlist = tracks, loading = false) }
            controller.setPlaylist(tracks)
        }
    }

    fun selectTrack(index: Int) {
        val track = _uiState.value.playlist.getOrNull(index) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(currentIndex = index, loading = true) }
            val file = copyCache.getPlayableFile(track)
            if (file == null) {
                _uiState.update { it.copy(loading = false, error = "Failed to copy ${track.title}") }
                return@launch
            }
            controller.loadFile(file.absolutePath, index)
            val art = artCache.getArt(track)
            _uiState.update { it.copy(currentArt = art, loading = false, error = null) }
        }
    }

    fun togglePlayPause() = controller.playPause()

    fun seekTo(ms: Long) = controller.seekTo(ms)

    fun next() {
        val i = _uiState.value.currentIndex + 1
        if (i in _uiState.value.playlist.indices) selectTrack(i)
    }

    fun previous() {
        val i = _uiState.value.currentIndex - 1
        if (i in _uiState.value.playlist.indices) selectTrack(i)
    }

    fun setPlaylistUserOverride(visible: Boolean?) {
        _uiState.update { it.copy(playlistUserOverride = visible) }
    }

    private fun advanceToCurrent() {
        val i = controller.state.value.currentIndex
        if (i >= 0) selectTrack(i)
    }

    override fun onCleared() {
        controller.stop()
        super.onCleared()
    }
}