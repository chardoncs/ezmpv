package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.security.MessageDigest

private const val TAG = "FileCopyCache"

class FileCopyCache(
    private val context: Context,
    private val maxSize: Int = 5,
) {
    private val order = ArrayDeque<String>()
    private val cacheDir = File(context.filesDir, "audio-cache").apply { mkdirs() }

    fun getPlayableFile(track: AudioTrack): File? {
        val key = hashKey(track.sourceUri)
        val ext = guessExtension(track)
        val cached = File(cacheDir, "$key.$ext")
        if (cached.exists() && cached.length() > 0) {
            // Move to most-recently-used.
            order.remove(key)
            order.addFirst(key)
            return cached
        }
        return try {
            context.contentResolver.openInputStream(track.sourceUri)?.use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            order.remove(key)
            order.addFirst(key)
            while (order.size > maxSize) {
                val evicted = order.removeLast()
                File(cacheDir, "$evicted.*").listFiles()?.forEach { it.delete() }
            }
            cached
        } catch (t: Throwable) {
            Log.e(TAG, "copy failed for ${track.sourceUri}", t)
            null
        }
    }

    private fun hashKey(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun guessExtension(track: AudioTrack): String {
        // Prefer the URI's filename extension (works for most SAF URIs).
        val seg = track.sourceUri.lastPathSegment
        if (seg != null) {
            val dot = seg.lastIndexOf('.')
            if (dot >= 0) {
                val ext = seg.substring(dot + 1).lowercase()
                if (ext.isNotEmpty() && ext.length <= 6) return ext
            }
        }
        // Fall back to the MIME subtype (e.g. "audio/mpeg" -> "mpeg", "audio/mp4" -> "mp4").
        track.mimeType?.substringAfter('/')?.lowercase()?.let { return it }
        return "bin"
    }
}