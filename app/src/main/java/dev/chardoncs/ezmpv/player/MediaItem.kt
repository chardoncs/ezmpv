package dev.chardoncs.ezmpv.player

import android.net.Uri

data class MediaItem(
    val sourceUri: Uri,
    val mediaId: String = sourceUri.toString(),
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val isVideo: Boolean = false,
)

val MediaItem.isAudio: Boolean get() = !isVideo