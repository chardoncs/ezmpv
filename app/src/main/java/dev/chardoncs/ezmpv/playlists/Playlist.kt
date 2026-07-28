package dev.chardoncs.ezmpv.playlists

import android.net.Uri
import dev.chardoncs.ezmpv.browse.UriSerializer
import dev.chardoncs.ezmpv.player.MediaItem
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistEntry(
    @Serializable(with = UriSerializer::class) val uri: Uri,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val isVideo: Boolean = false,
    val fileName: String,
    val parentUri: String? = null,
    val addedAt: Long = 0L,
)

fun PlaylistEntry.toMediaItem(uri: Uri = this.uri): MediaItem = MediaItem(
    sourceUri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    isVideo = isVideo,
)

fun MediaItem.toPlaylistEntry(addedAt: Long = System.currentTimeMillis()): PlaylistEntry {
    val uriStr = sourceUri.toString()
    val fileName = sourceUri.lastPathSegment?.substringAfterLast('/') ?: title
    val parentUri = uriStr.substringBeforeLast('/').takeIf { it.isNotBlank() && it != uriStr }
    return PlaylistEntry(
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
        addedAt = addedAt,
    )
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    @Serializable(with = UriSerializer::class) val coverImageUri: Uri? = null,
    val entries: List<PlaylistEntry> = emptyList(),
    val createdAt: Long = 0L,
    val isFavorites: Boolean = false,
)

const val FAVORITES_PLAYLIST_ID = "favorites"