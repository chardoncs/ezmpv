package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dev.chardoncs.ezmpv.player.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FolderRepository"

class FolderRepository(private val context: Context) {

    private val metadataCache = MetadataCache(context)

    suspend fun scanMedia(folderUri: Uri): List<MediaItem> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!tree.isDirectory) return@withContext emptyList()
        val out = mutableListOf<MediaItem>()
        collectMedia(tree, recursive = true, out)
        out.map { enrich(it) }
    }

    suspend fun listMedia(folderUri: Uri, recursive: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!tree.isDirectory) return@withContext emptyList()
        val out = mutableListOf<MediaItem>()
        collectMedia(tree, recursive, out)
        out
    }

    private suspend fun enrich(item: MediaItem): MediaItem {
        metadataCache.get(item)?.let { return it }
        val enriched = loadMetadata(item) ?: item
        metadataCache.put(enriched)
        return enriched
    }

    private fun collectMedia(dir: DocumentFile, recursive: Boolean, out: MutableList<MediaItem>) {
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory && recursive -> collectMedia(doc, recursive, out)
                doc.isFile -> {
                    val mime = doc.type
                    val isVideo = mime?.startsWith("video/") == true
                    val isAudio = mime?.startsWith("audio/") == true
                    if (!isVideo && !isAudio) return@forEach
                    val name = doc.name ?: doc.uri.lastPathSegment ?: "Unknown"
                    out.add(
                        MediaItem(
                            sourceUri = doc.uri,
                            title = name.substringBeforeLast('.'),
                            durationMs = 0L,
                            sizeBytes = doc.length(),
                            mimeType = mime,
                            isVideo = isVideo,
                        )
                    )
                }
            }
        }
    }

    suspend fun loadMetadata(item: MediaItem): MediaItem? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.sourceUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: item.title
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.toIntOrNull()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ?.take(4)?.toIntOrNull()
            val discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')
                ?.trim()?.toIntOrNull()
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')
                ?.trim()?.toIntOrNull()
            item.copy(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                year = year,
                discNumber = discNumber,
                trackNumber = trackNumber,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "metadata extraction failed for ${item.sourceUri}", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}