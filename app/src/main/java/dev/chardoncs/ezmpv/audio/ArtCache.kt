package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

private const val TAG = "ArtCache"
private val COVER_NAMES = listOf("cover.jpg", "cover.png", "albumart.jpg", "albumart.png", "folder.jpg")

class ArtCache(private val context: Context, maxSize: Int = 50) {
    private val cache = LruCache<String, Bitmap>(maxSize)

    suspend fun getArt(track: AudioTrack): Bitmap? = withContext(Dispatchers.IO) {
        val key = track.sourceUri.toString()
        cache.get(key)?.let { return@withContext it }
        val bmp = extractEmbedded(track.sourceUri) ?: extractFolderCover(track.sourceUri)
        if (bmp != null) cache.put(key, bmp)
        bmp
    }

    private fun extractEmbedded(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "embedded art extraction failed for $uri", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFolderCover(uri: Uri): Bitmap? {
        val parent = runCatching { uri.toString().substringBeforeLast('/').toUri() }.getOrNull()
            ?: return null
        val parentDoc = DocumentFile.fromTreeUri(context, parent) ?: return null
        for (name in COVER_NAMES) {
            val cover = parentDoc.findFile(name) ?: continue
            return try {
                context.contentResolver.openInputStream(cover.uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "cover decode failed for $name", t)
                null
            }
        }
        return null
    }
}