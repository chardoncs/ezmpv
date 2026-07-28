package dev.chardoncs.ezmpv.playlists

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.chardoncs.ezmpv.player.MediaItem
import dev.chardoncs.ezmpv.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResolvedEntry(
    val entry: PlaylistEntry,
    val mediaItem: MediaItem?,
    val available: Boolean,
)

data class ResolvedPlaylist(
    val playlist: Playlist,
    val entries: List<ResolvedEntry>,
)

class PlaylistController(
    context: Context,
    private val repo: PlaylistRepository,
    private val playerController: PlayerController,
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _availabilityCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    init {
        scope.launch { repo.ensureFavorites() }
        scope.launch { repo.playlists.collect { list -> _playlists.value = list } }
    }

    val resolved: StateFlow<List<ResolvedPlaylist>> =
        combine(_playlists, playerController.state, _availabilityCache) { lists, state, cache ->
            val library = state.library
            lists.map { pl -> ResolvedPlaylist(pl, pl.entries.map { resolveEntry(it, library, cache) }) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun resolveEntry(
        entry: PlaylistEntry,
        library: List<MediaItem>,
        cache: Map<String, Boolean>,
    ): ResolvedEntry {
        val match = library.firstOrNull { item ->
            val itemUriStr = item.sourceUri.toString()
            val itemFileName = item.sourceUri.lastPathSegment?.substringAfterLast('/')
            val itemParent = itemUriStr.substringBeforeLast('/').takeIf { it != itemUriStr }
            itemFileName == entry.fileName && itemParent == entry.parentUri
        }
        if (match != null) return ResolvedEntry(entry, match, true)
        if (!DocumentsContract.isTreeUri(entry.uri)) {
            val cached = cache[entry.uri.toString()]
            if (cached != null) {
                return ResolvedEntry(entry, entry.toMediaItem(), cached)
            }
            scheduleAvailabilityCheck(entry)
            return ResolvedEntry(entry, entry.toMediaItem(), true)
        }
        return ResolvedEntry(entry, entry.toMediaItem(), false)
    }

    private fun scheduleAvailabilityCheck(entry: PlaylistEntry) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(entry.uri)?.close()
                    true
                }.getOrDefault(false)
            }
            _availabilityCache.value = _availabilityCache.value + (entry.uri.toString() to ok)
        }
    }

    fun refreshAvailability() {
        _availabilityCache.value = emptyMap()
    }

    fun create(name: String, description: String = "", onCreated: (Playlist) -> Unit = {}) {
        scope.launch { onCreated(repo.create(name, description)) }
    }

    fun update(id: String, name: String? = null, description: String? = null, coverImageUri: Uri? = null, clearCover: Boolean = false) {
        scope.launch { repo.update(id, name, description, coverImageUri, clearCover) }
    }

    fun delete(id: String) {
        scope.launch { repo.delete(id) }
    }

    fun addEntries(id: String, entries: List<PlaylistEntry>) {
        scope.launch { repo.addEntries(id, entries) }
    }

    fun addMediaItems(id: String, items: List<MediaItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val enriched = items.map { item ->
                playerController.loadMetadata(item)?.let { meta ->
                    item.copy(
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        durationMs = meta.durationMs,
                        year = meta.year,
                        discNumber = meta.discNumber,
                        trackNumber = meta.trackNumber,
                    )
                } ?: item
            }
            repo.addEntries(id, enriched.map { it.toPlaylistEntry() })
        }
    }

    fun removeEntry(id: String, uri: Uri) {
        scope.launch { repo.removeEntry(id, uri) }
    }

    fun removeEntries(id: String, uris: Set<Uri>) {
        scope.launch { repo.removeEntries(id, uris) }
    }

    fun toggleFavorite(item: MediaItem, onResult: (Boolean) -> Unit = {}) {
        val entry = item.toPlaylistEntry()
        scope.launch { onResult(repo.toggleFavorite(entry)) }
    }

    fun isFavorite(uri: Uri): Boolean =
        _playlists.value.firstOrNull { it.isFavorites }?.entries?.any { it.uri == uri } == true

    fun isFavorite(item: MediaItem): Boolean = isFavorite(item.sourceUri)

    fun resolvedPlaylist(id: String): ResolvedPlaylist? =
        resolved.value.firstOrNull { it.playlist.id == id }

    fun favoritesId(): String = FAVORITES_PLAYLIST_ID
}