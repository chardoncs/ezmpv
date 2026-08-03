package dev.chardoncs.ezmpv.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.chardoncs.ezmpv.browse.UriSerializer
import dev.chardoncs.ezmpv.player.MediaItem
import dev.chardoncs.ezmpv.player.PlaySequence
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionStore by preferencesDataStore("playback_session")

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class SavedTrack(
    @Serializable(with = UriSerializer::class) val uri: android.net.Uri,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val isVideo: Boolean = false,
    val fileName: String? = null,
    val parentUri: String? = null,
)

@Serializable
data class SavedSession(
    val playlist: List<SavedTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val playSequenceName: String,
    val audioOnly: Boolean,
)

fun SavedTrack.toMediaItem(): MediaItem = MediaItem(
    sourceUri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    isVideo = isVideo,
)

fun MediaItem.toSavedTrack(): SavedTrack {
    val uriStr = sourceUri.toString()
    val fileName = sourceUri.lastPathSegment?.substringAfterLast('/') ?: title
    val parentUri = uriStr.substringBeforeLast('/').takeIf { it.isNotBlank() && it != uriStr }
    return SavedTrack(
        uri = sourceUri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        isVideo = isVideo,
        fileName = fileName,
        parentUri = parentUri,
    )
}

fun PlaySequence.toSavedName(): String = name

fun savedNameToPlaySequence(name: String): PlaySequence =
    runCatching { PlaySequence.valueOf(name) }.getOrDefault(PlaySequence.SEQUENCE)

object SessionCodec {
    fun encode(session: SavedSession): String = json.encodeToString(SavedSession.serializer(), session)
    fun decode(raw: String): SavedSession? =
        runCatching { json.decodeFromString<SavedSession>(raw) }.getOrNull()
}

class SessionStore(private val context: Context) {

    suspend fun save(session: SavedSession) {
        context.sessionStore.edit { it[KEY] = SessionCodec.encode(session) }
    }

    suspend fun load(): SavedSession? {
        val raw = context.sessionStore.data.first()[KEY] ?: return null
        return SessionCodec.decode(raw)
    }

    suspend fun clear() {
        context.sessionStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("session")
    }
}