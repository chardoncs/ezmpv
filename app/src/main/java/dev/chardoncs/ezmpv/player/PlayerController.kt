package dev.chardoncs.ezmpv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import dev.chardoncs.ezmpv.audio.ArtCache
import dev.chardoncs.ezmpv.audio.FileCopyCache
import dev.chardoncs.ezmpv.audio.FolderRepository
import dev.chardoncs.ezmpv.audio.LibraryPreferences
import dev.chardoncs.ezmpv.audio.MetadataCache
import dev.chardoncs.ezmpv.audio.SavedSession
import dev.chardoncs.ezmpv.audio.SavedTrack
import dev.chardoncs.ezmpv.audio.SessionStore
import dev.chardoncs.ezmpv.audio.savedNameToPlaySequence
import dev.chardoncs.ezmpv.audio.toMediaItem
import dev.chardoncs.ezmpv.audio.toSavedName
import dev.chardoncs.ezmpv.audio.toSavedTrack
import dev.chardoncs.ezmpv.browse.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerController(
    private val app: Context,
    val bookmarks: BookmarkRepository,
    val prefs: LibraryPreferences,
) {

    private val folderRepo = FolderRepository(app)
    private val artCache = ArtCache(app)
    private val copyCache = FileCopyCache(app)
    private val metadataCache = dev.chardoncs.ezmpv.audio.MetadataCache(app)
    private val sessionStore = SessionStore(app)

    val player = Player(app).apply {
        onTrackEnd = { advanceOnTrackEnd() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var serviceStarted = false
    private var loadJob: Job? = null
    private var unshuffledPlaylist: List<MediaItem>? = null
    @Volatile
    private var restartOnPrevious = false
    private var restoreJob: Job? = null
    private var positionSaveJob: Job? = null
    private var saveDebounceJob: Job? = null
    private var restoredOnce = false

    init {
        scope.launch {
            prefs.restartOnPrevious().collect { restartOnPrevious = it }
        }
        scope.launch {
            bookmarks.bookmarks.collect { list ->
                _state.update { it.copy(selectedFolders = list.map { b -> b.uri }) }
                refreshPlaylist()
            }
        }
        scope.launch {
            player.state.collect { c ->
                val wasPlaying = _state.value.isPlaying
                _state.update { ui ->
                    ui.copy(
                        isPlaying = c.isPlaying,
                        positionMs = c.positionMs,
                        durationMs = c.durationMs,
                        audioOnly = c.audioOnly,
                    )
                }
                if (c.isPlaying && !wasPlaying) startPositionHeartbeat()
                else if (!c.isPlaying && wasPlaying) {
                    positionSaveJob?.cancel()
                    positionSaveJob = null
                    saveSessionSoon()
                }
            }
        }
        restoreJob = scope.launch { restoreSessionIfPresent() }
    }

    private suspend fun restoreSessionIfPresent() {
        val saved = sessionStore.load() ?: return
        val library = _state.value.library
        val resolved = resolveSavedTracks(saved.playlist, library)
        if (resolved.isEmpty()) {
            sessionStore.clear()
            return
        }
        val safeIndex = saved.currentIndex.coerceIn(0, resolved.lastIndex)
        if (saved.currentIndex !in resolved.indices || resolved.getOrNull(saved.currentIndex) == null) {
            sessionStore.clear()
            return
        }
        val current = resolved[safeIndex]
        val file = withContext(Dispatchers.IO) { copyCache.getPlayableFile(current) }
        if (file == null) {
            sessionStore.clear()
            return
        }
        ensureServiceStarted()
        player.start()
        val sequence = runCatching { PlaySequence.valueOf(saved.playSequenceName) }
            .getOrDefault(PlaySequence.SEQUENCE)
        _state.update {
            it.copy(
                playlist = resolved,
                currentIndex = safeIndex,
                playSequence = sequence,
                audioOnly = saved.audioOnly,
                loading = false,
                hasVideo = current.isVideo && !saved.audioOnly,
            )
        }
        player.setPlaylist(resolved)
        player.setAudioOnly(saved.audioOnly)
        player.loadFile(file.absolutePath, safeIndex, resumePositionMs = saved.positionMs)
        restoredOnce = true
    }

    private suspend fun resolveSavedTracks(
        saved: List<SavedTrack>,
        library: List<MediaItem>,
    ): List<MediaItem> {
        val result = ArrayList<MediaItem>(saved.size)
        for (track in saved) {
            val match = library.firstOrNull { item ->
                item.mediaId == track.uri.toString() ||
                    (track.fileName != null && track.parentUri != null &&
                        item.sourceUri.lastPathSegment?.substringAfterLast('/') == track.fileName &&
                        item.sourceUri.toString().substringBeforeLast('/') == track.parentUri)
            }
            if (match != null) {
                result.add(match)
                continue
            }
            if (DocumentsContract.isTreeUri(track.uri)) continue
            val exists = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(track.uri)?.close()
                    true
                }.getOrDefault(false)
            }
            if (!exists) continue
            result.add(track.toMediaItem())
        }
        return result
    }

    private fun startPositionHeartbeat() {
        positionSaveJob?.cancel()
        positionSaveJob = scope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveSessionSoon()
            }
        }
    }

    private fun saveSessionSoon() {
        saveDebounceJob?.cancel()
        saveDebounceJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val s = _state.value
            if (s.playlist.isEmpty() || s.currentIndex !in s.playlist.indices) return@launch
            val session = SavedSession(
                playlist = s.playlist.map { it.toSavedTrack() },
                currentIndex = s.currentIndex,
                positionMs = player.state.value.positionMs.coerceAtLeast(0),
                playSequenceName = s.playSequence.toSavedName(),
                audioOnly = s.audioOnly,
            )
            sessionStore.save(session)
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
            _state.update { it.copy(loading = true) }
            val items = if (folders.isEmpty()) emptyList() else folders.flatMap { folderRepo.scanMedia(it) }
            _state.update { it.copy(library = items, loading = false) }
        }
    }

    fun playFromLibrary(library: List<MediaItem>, index: Int) {
        val item = library.getOrNull(index) ?: return
        ensureServiceStarted()
        player.start()
        loadJob?.cancel()
        loadJob = scope.launch {
            unshuffledPlaylist = null
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
            unshuffledPlaylist = null
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
        unshuffledPlaylist = unshuffledPlaylist?.plus(items)
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
        unshuffledPlaylist = unshuffledPlaylist?.let { base ->
            if (idx in base.indices) base.toMutableList().also { it.addAll(idx + 1, items) }
            else items
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
        saveSessionSoon()
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
        unshuffledPlaylist = null
        player.setPlaylist(listOf(item))
        selectTrack(0)
    }

    suspend fun getArt(item: MediaItem) = artCache.getArt(item)

    suspend fun loadMetadata(item: MediaItem): MediaItem? = folderRepo.loadMetadata(item)

    suspend fun enrichItem(item: MediaItem): MediaItem = folderRepo.enrich(item)

    fun togglePlayPause() = player.playPause()

    fun setPlaying(play: Boolean) {
        if (_state.value.isPlaying != play) player.playPause()
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun next() {
        val s = _state.value
        val target = indexForSkip(s.playSequence, s.playlist.size, s.currentIndex, forward = true)
            ?: return
        selectTrack(target)
    }

    fun previous() {
        val s = _state.value
        if (restartOnPrevious && s.positionMs > 3_000) {
            seekTo(0)
            return
        }
        val target = indexForSkip(s.playSequence, s.playlist.size, s.currentIndex, forward = false)
            ?: return
        selectTrack(target)
    }

    fun setAudioOnly(audioOnly: Boolean) {
        _state.update {
            it.copy(
                audioOnly = audioOnly,
                hasVideo = it.playlist.getOrNull(it.currentIndex)?.isVideo == true && !audioOnly,
            )
        }
        player.setAudioOnly(audioOnly)
        saveSessionSoon()
    }

    fun setPlaylistUserOverride(visible: Boolean?) {
        _state.update { it.copy(playlistUserOverride = visible) }
    }

    fun cyclePlaySequence() {
        setPlaySequence(_state.value.playSequence.next())
    }

    fun setPlaySequence(mode: PlaySequence) {
        val current = _state.value
        if (current.playSequence == mode) return
        val wasShuffled = current.playSequence == PlaySequence.SHUFFLE ||
            current.playSequence == PlaySequence.SHUFFLE_REPEAT
        val willShuffle = mode == PlaySequence.SHUFFLE || mode == PlaySequence.SHUFFLE_REPEAT
        if (willShuffle && !wasShuffled) {
            enterShuffle(current, mode)
        } else if (!willShuffle && wasShuffled) {
            exitShuffle(current, mode)
        } else {
            _state.update { it.copy(playSequence = mode) }
        }
        saveSessionSoon()
    }

    private fun enterShuffle(current: PlayerState, mode: PlaySequence) {
        val original = current.playlist
        if (original.isEmpty()) {
            _state.update { it.copy(playSequence = mode) }
            return
        }
        val playingIdx = current.currentIndex.coerceIn(0, original.lastIndex)
        val playing = original[playingIdx]
        unshuffledPlaylist = original
        val rest = original.toMutableList().also { it.removeAt(playingIdx) }
        rest.shuffle()
        val shuffled = listOf(playing) + rest
        player.syncPlaylist(shuffled, 0)
        _state.update {
            it.copy(playlist = shuffled, currentIndex = 0, playSequence = mode)
        }
    }

    private fun exitShuffle(current: PlayerState, mode: PlaySequence) {
        val original = unshuffledPlaylist ?: current.playlist
        unshuffledPlaylist = null
        val playingId = current.playlist.getOrNull(current.currentIndex)?.mediaId
        val restoredIndex = original.indexOfFirst { it.mediaId == playingId }.let {
            if (it < 0) current.currentIndex.coerceIn(0, original.lastIndex.coerceAtLeast(0)) else it
        }
        player.syncPlaylist(original, restoredIndex)
        _state.update {
            it.copy(playlist = original, currentIndex = restoredIndex, playSequence = mode)
        }
    }

    private fun advanceOnTrackEnd() {
        val s = _state.value
        if (s.playSequence == PlaySequence.REPEAT_ONE) {
            player.replay()
            return
        }
        val next = indexAfterTrackEnd(s.playSequence, s.playlist.size, s.currentIndex) ?: return
        selectTrack(next)
    }

    fun setVideoDecodeEnabled(enabled: Boolean) = player.setVideoDecodeEnabled(enabled)

    fun stopPlayback() {
        restoreJob?.cancel()
        positionSaveJob?.cancel()
        saveDebounceJob?.cancel()
        positionSaveJob = null
        saveDebounceJob = null
        restoredOnce = false
        scope.launch { sessionStore.clear() }
        player.stop()
        serviceStarted = false
        _state.update {
            PlayerState(
                library = it.library,
                selectedFolders = it.selectedFolders,
            )
        }
    }

    fun release() {
        stopPlayback()
        scope.cancel()
    }

    companion object {
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val SAVE_DEBOUNCE_MS = 500L
    }
}