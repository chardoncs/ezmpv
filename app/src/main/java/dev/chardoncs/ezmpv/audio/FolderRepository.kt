package dev.chardoncs.ezmpv.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FolderRepository"

class FolderRepository(private val context: Context) {

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

    suspend fun scanAudio(folderUri: Uri): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!tree.isDirectory) return@withContext emptyList()
        val out = mutableListOf<AudioTrack>()
        collectAudio(tree, out)
        out.sortedBy { it.title.lowercase() }
    }

    private fun collectAudio(dir: DocumentFile, out: MutableList<AudioTrack>) {
        dir.listFiles().forEach { doc ->
            when {
                doc.isDirectory -> collectAudio(doc, out)
                doc.isFile && doc.type?.startsWith("audio/") == true ->
                    docToTrack(doc.uri, doc.type)?.let(out::add)
            }
        }
    }

    private fun docToTrack(uri: Uri, mimeType: String?): AudioTrack? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment ?: "Unknown"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            AudioTrack(
                sourceUri = uri,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                sizeBytes = 0L,
                mimeType = mimeType,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "metadata extraction failed for $uri", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}