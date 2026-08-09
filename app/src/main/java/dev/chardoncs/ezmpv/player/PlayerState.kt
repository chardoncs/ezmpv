package dev.chardoncs.ezmpv.player

import android.graphics.Bitmap
import android.net.Uri

data class PlayerState(
    val library: List<MediaItem> = emptyList(),
    val playlist: List<MediaItem> = emptyList(),
    val selectedFolders: List<Uri> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentArt: Bitmap? = null,
    val hasVideo: Boolean = false,
    val audioOnly: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val playlistUserOverride: Boolean? = null,
    val playSequence: PlaySequence = PlaySequence.SEQUENCE,
    val inPip: Boolean = false,
)

val PlayerState.playlistVisible: Boolean
    get() = playlistUserOverride ?: false