package dev.chardoncs.ezmpv.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.chardoncs.ezmpv.player.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val Context.metadataStore by preferencesDataStore("media_metadata")
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class StoredMetadata(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val durationMs: Long = 0L,
)

class MetadataCache(private val context: Context) {

    suspend fun get(item: MediaItem): MediaItem? {
        val key = stringPreferencesKey(keyFor(item))
        val prefs = context.metadataStore.data.first()
        val raw = prefs[key] ?: return null
        return runCatching { json.decodeFromString<StoredMetadata>(raw) }.getOrNull()
            ?.let { item.copy(title = it.title, artist = it.artist, album = it.album, year = it.year, durationMs = it.durationMs) }
    }

    suspend fun put(item: MediaItem) {
        val key = stringPreferencesKey(keyFor(item))
        val stored = StoredMetadata(item.title, item.artist, item.album, item.year, item.durationMs)
        context.metadataStore.edit { it[key] = json.encodeToString(StoredMetadata.serializer(), stored) }
    }

    private fun keyFor(item: MediaItem): String {
        val seed = item.sourceUri.toString() + "|" + item.sizeBytes
        return MessageDigest.getInstance("SHA-1")
            .digest(seed.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}