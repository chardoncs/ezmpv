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

    fun grantedFolders(): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }
            .map { it.uri }

    fun grantFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.e(TAG, "grantFolder failed", it) }
    }

    fun revokeFolder(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.e(TAG, "revokeFolder failed", it) }
    }

    suspend fun scanMedia(folderUri: Uri): List<MediaItem> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!tree.isDirectory) return@withContext emptyList()
        val out = mutableListOf<MediaItem>()
        collectMedia(tree, out)
        out.map { enrich(it) }
    }

    private suspend fun enrich(item: MediaItem): MediaItem {
        metadataCache.get(item)?.let { return it }
        val enriched = loadMetadata(item) ?: item
        metadataCache.put(enriched)
        return enriched
    }

    private fun collectMedia(dir: DocumentFile, out: MutableList<MediaItem>) {
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory -> collectMedia(doc, out)
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
            item.copy(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                year = year,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "metadata extraction failed for ${item.sourceUri}", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
