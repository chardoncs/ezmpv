package dev.chardoncs.ezmpv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import dev.chardoncs.ezmpv.audio.ArtCache
import dev.chardoncs.ezmpv.audio.FileCopyCache
import dev.chardoncs.ezmpv.audio.FolderRepository
import dev.chardoncs.ezmpv.browse.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerController(private val app: Context, val bookmarks: BookmarkRepository) {

    private val folderRepo = FolderRepository(app)
    private val artCache = ArtCache(app)
    private val copyCache = FileCopyCache(app)
    private val metadataCache = dev.chardoncs.ezmpv.audio.MetadataCache(app)

    val player = Player(app).apply {
        onTrackEnd = { nextIndex ->
            nextIndex?.let(::selectTrack)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var serviceStarted = false
    private var loadJob: Job? = null

    init {
        scope.launch {
            bookmarks.bookmarks.collect { list ->
                _state.update { it.copy(selectedFolders = list.map { b -> b.uri }) }
                refreshPlaylist()
            }
        }
        scope.launch {
            player.state.collect { c ->
                _state.update { ui ->
                    ui.copy(
                        isPlaying = c.isPlaying,
                        positionMs = c.positionMs,
                        durationMs = c.durationMs,
                        currentIndex = c.currentIndex,
                        audioOnly = c.audioOnly,
                    )
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun ensureServiceStarted() {
        if (serviceStarted) return
        serviceStarted = true
        ContextCompat.startForegroundService(app, Intent(app, PlayerService::class.java))
    }

    fun refreshPlaylist() {
        scope.launch {
            val folders = _state.value.selectedFolders
            if (folders.isEmpty()) {
                _state.update { it.copy(library = emptyList(), playlist = emptyList(), currentIndex = -1) }
                player.setPlaylist(emptyList())
                return@launch
            }
            _state.update { it.copy(loading = true) }
            val items = folders.flatMap { folderRepo.scanMedia(it) }
            _state.update { it.copy(library = items, loading = false) }
        }
    }

    fun playFromLibrary(library: List<MediaItem>, index: Int) {
        val item = library.getOrNull(index) ?: return
        ensureServiceStarted()
        player.start()
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update {
                it.copy(playlist = library, currentIndex = index, loading = true)
            }
            player.setPlaylist(library)
            loadAndPlay(item, index)
        }
    }

    fun playDirectory(folderUri: Uri, recursive: Boolean, onQueued: () -> Unit = {}) {
        ensureServiceStarted()
        player.start()
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(loading = true) }
            val items = folderRepo.listMedia(folderUri, recursive)
            if (items.isEmpty()) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            _state.update { it.copy(playlist = items, currentIndex = 0, loading = true) }
            player.setPlaylist(items)
            loadAndPlay(items[0], 0)
            onQueued()
        }
    }

    fun appendToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        ensureServiceStarted()
        player.start()
        val newPlaylist = _state.value.playlist + items
        _state.update { it.copy(playlist = newPlaylist) }
        player.setPlaylist(newPlaylist)
    }

    fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        ensureServiceStarted()
        player.start()
        val current = _state.value
        val idx = current.currentIndex
        val newPlaylist = if (idx in current.playlist.indices) {
            current.playlist.toMutableList().also { it.addAll(idx + 1, items) }
        } else {
            items
        }
        _state.update { it.copy(playlist = newPlaylist) }
        player.setPlaylist(newPlaylist)
    }

    private suspend fun loadAndPlay(item: MediaItem, index: Int) {
        val file = withContext(Dispatchers.IO) { copyCache.getPlayableFile(item) } ?: run {
            _state.update { it.copy(loading = false, error = "Failed to copy ${item.title}") }
            return
        }
        currentCoroutineContext().ensureActive()
        player.loadFile(file.absolutePath, index)
        if (!item.isVideo) {
            val art = artCache.getArt(item)
            _state.update {
                it.copy(
                    currentArt = art,
                    loading = false,
                    error = null,
                    hasVideo = false,
                )
            }
        } else {
            _state.update {
                it.copy(
                    currentArt = null,
                    loading = false,
                    error = null,
                    hasVideo = !it.audioOnly,
                )
            }
        }
        val cached = metadataCache.get(item)
        if (cached != null) {
            _state.update { ui ->
                val updated = ui.playlist.toMutableList()
                if (index in updated.indices) {
                    updated[index] = cached
                    ui.copy(playlist = updated)
                } else ui
            }
        }
    }

    fun selectTrack(index: Int) {
        val item = _state.value.playlist.getOrNull(index) ?: return
        ensureServiceStarted()
        player.start()
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(currentIndex = index, loading = true) }
            loadAndPlay(item, index)
        }
    }

    fun playAdhoc(uri: Uri, mimeType: String?) {
        val isVideo = mimeType?.startsWith("video/") == true
        val item = MediaItem(
            sourceUri = uri,
            title = uri.lastPathSegment?.substringAfterLast('/') ?: "Picked file",
            mimeType = mimeType,
            isVideo = isVideo,
        )
        _state.update { it.copy(playlist = listOf(item), currentIndex = 0) }
        player.setPlaylist(listOf(item))
        selectTrack(0)
    }

    suspend fun getArt(item: MediaItem) = artCache.getArt(item)

    fun togglePlayPause() = player.playPause()

    fun setPlaying(play: Boolean) {
        if (_state.value.isPlaying != play) player.playPause()
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun next() {
        val i = _state.value.currentIndex + 1
        if (i in _state.value.playlist.indices) selectTrack(i)
    }

    fun previous() {
        val i = _state.value.currentIndex - 1
        if (i in _state.value.playlist.indices) selectTrack(i)
    }

    fun setAudioOnly(audioOnly: Boolean) {
        _state.update {
            it.copy(
                audioOnly = audioOnly,
                hasVideo = it.playlist.getOrNull(it.currentIndex)?.isVideo == true && !audioOnly,
            )
        }
        player.setAudioOnly(audioOnly)
    }

    fun setPlaylistUserOverride(visible: Boolean?) {
        _state.update { it.copy(playlistUserOverride = visible) }
    }

    fun release() {
        player.stop()
        scope.cancel()
    }
}