package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.chardoncs.ezmpv.player.MediaItem
import java.io.File
import java.security.MessageDigest

private const val TAG = "FileCopyCache"

class FileCopyCache(
    private val context: Context,
    private val maxSize: Int = 5,
) {
    private val order = ArrayDeque<String>()
    private val cacheDir = File(context.filesDir, "media-cache").apply { mkdirs() }

    fun getPlayableFile(item: MediaItem): File? {
        val key = hashKey(item.sourceUri)
        val ext = guessExtension(item)
        val cached = File(cacheDir, "$key.$ext")
        if (cached.exists() && cached.length() > 0) {
            order.remove(key)
            order.addFirst(key)
            return cached
        }
        return try {
            context.contentResolver.openInputStream(item.sourceUri)?.use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            order.remove(key)
            order.addFirst(key)
            while (order.size > maxSize) {
                val evicted = order.removeLast()
                cacheDir.listFiles()?.filter { it.name.startsWith("$evicted.") }?.forEach { it.delete() }
            }
            cached
        } catch (t: Throwable) {
            Log.e(TAG, "copy failed for ${item.sourceUri}", t)
            null
        }
    }

    private fun hashKey(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun guessExtension(item: MediaItem): String {
        val seg = item.sourceUri.lastPathSegment
        if (seg != null) {
            val dot = seg.lastIndexOf('.')
            if (dot >= 0) {
                val ext = seg.substring(dot + 1).lowercase()
                if (ext.isNotEmpty() && ext.length <= 6) return ext
            }
        }
        item.mimeType?.substringAfter('/')?.lowercase()?.let { return it }
        return "bin"
    }
}