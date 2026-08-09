package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
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

    suspend fun enrich(item: MediaItem, fileOverride: String? = null): MediaItem {
        if (fileOverride == null) {
            metadataCache.get(item)?.let { return it }
        }
        val enriched = loadMetadata(item, fileOverride) ?: item
        metadataCache.put(enriched)
        return enriched
    }

    private fun collectMedia(dir: DocumentFile, recursive: Boolean, out: MutableList<MediaItem>) {
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory && recursive -> collectMedia(doc, true, out)
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

    suspend fun loadMetadata(item: MediaItem, fileOverride: String? = null): MediaItem? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (fileOverride != null) {
                retriever.setDataSource(fileOverride)
            } else {
                retriever.setDataSource(context, item.sourceUri)
            }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: queryDisplayName(item.sourceUri)?.substringBeforeLast('.')
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

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
                }
        }.getOrNull()
    }
}