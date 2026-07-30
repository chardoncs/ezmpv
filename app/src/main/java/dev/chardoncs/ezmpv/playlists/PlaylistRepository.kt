package dev.chardoncs.ezmpv.playlists

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.playlistStore by preferencesDataStore("playlist_prefs")
private val PLAYLISTS = stringPreferencesKey("playlists")
private val json = Json { ignoreUnknownKeys = true }
private val playlistsSerializer = ListSerializer(Playlist.serializer())
private const val TAG = "PlaylistRepository"

class PlaylistRepository(private val context: Context) {

    val playlists: Flow<List<Playlist>> = context.playlistStore.data
        .map { decode(it[PLAYLISTS]) }

    private fun decode(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(playlistsSerializer, raw) }
            .onFailure { Log.w(TAG, "decode failed", it) }
            .getOrDefault(emptyList())
    }

    private fun encode(list: List<Playlist>): String =
        json.encodeToString(playlistsSerializer, list)

    private suspend fun write(list: List<Playlist>) {
        context.playlistStore.edit { it[PLAYLISTS] = encode(list) }
    }

    suspend fun ensureFavorites() {
        val current = playlists.first()
        if (current.none { it.id == FAVORITES_PLAYLIST_ID }) {
            write(current + Playlist(
                id = FAVORITES_PLAYLIST_ID,
                name = "",
                isFavorites = true,
                createdAt = System.currentTimeMillis(),
            ))
        }
    }

    suspend fun create(name: String, description: String = ""): Playlist {
        val playlist = Playlist(
            id = "pl_" + System.currentTimeMillis() + "_" + (Math.random().toString().takeLast(6)),
            name = name.trim(),
            description = description.trim(),
            createdAt = System.currentTimeMillis(),
        )
        write(playlists.first() + playlist)
        return playlist
    }

    suspend fun update(id: String, name: String? = null, description: String? = null, coverImageUri: Uri? = null, clearCover: Boolean = false) {
        val list = playlists.first()
        write(list.map { p ->
            if (p.id != id) p else p.copy(
                name = name?.trim() ?: p.name,
                description = description?.trim() ?: p.description,
                coverImageUri = if (clearCover) null else coverImageUri ?: p.coverImageUri,
            )
        })
    }

    suspend fun delete(id: String) {
        val list = playlists.first()
        write(list.filterNot { it.id == id && !it.isFavorites })
    }

    suspend fun addEntries(id: String, entries: List<PlaylistEntry>) {
        if (entries.isEmpty()) return
        val list = playlists.first()
        write(list.map { p ->
            if (p.id != id) p else {
                val existingUris = p.entries.map { it.uri.toString() }.toHashSet()
                val toAdd = entries.filter { it.uri.toString() !in existingUris }
                p.copy(entries = p.entries + toAdd)
            }
        })
    }

    suspend fun removeEntry(id: String, uri: Uri) {
        val list = playlists.first()
        write(list.map { p ->
            if (p.id != id) p else p.copy(entries = p.entries.filterNot { it.uri == uri })
        })
    }

    suspend fun removeEntries(id: String, uris: Set<Uri>) {
        val list = playlists.first()
        write(list.map { p ->
            if (p.id != id) p else p.copy(entries = p.entries.filterNot { it.uri in uris })
        })
    }

    suspend fun toggleFavorite(entry: PlaylistEntry): Boolean {
        val list = playlists.first()
        val fav = list.firstOrNull { it.isFavorites } ?: return false
        val exists = fav.entries.any { it.uri == entry.uri }
        write(list.map { p ->
            if (!p.isFavorites) p else p.copy(
                entries = if (exists) p.entries.filterNot { it.uri == entry.uri }
                else p.entries + entry
            )
        })
        return !exists
    }

    suspend fun isFavorite(uri: Uri): Boolean =
        playlists.first().firstOrNull { it.isFavorites }?.entries?.any { it.uri == uri } == true

    suspend fun getById(id: String): Playlist? =
        playlists.first().firstOrNull { it.id == id }
}