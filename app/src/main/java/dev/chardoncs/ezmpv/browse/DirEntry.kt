package dev.chardoncs.ezmpv.browse

import android.net.Uri

data class DirEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String?,
    val sizeBytes: Long,
) {
    val isMedia: Boolean
        get() = !isDirectory && (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true)
}