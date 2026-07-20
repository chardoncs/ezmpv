package dev.chardoncs.ezmpv.audio

import android.graphics.Bitmap
import android.net.Uri

data class AudioTrack(
    val sourceUri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String? = null,
)

data class AudioUiState(
    val selectedFolders: List<Uri> = emptyList(),
    val playlist: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentArt: Bitmap? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val playlistUserOverride: Boolean? = null,
)

val AudioUiState.playlistVisible: Boolean
    get() = when {
        playlistUserOverride != null -> playlistUserOverride
        currentArt == null -> true
        else -> false
    }