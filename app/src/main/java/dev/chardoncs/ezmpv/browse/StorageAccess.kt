package dev.chardoncs.ezmpv.browse

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

private const val TAG = "StorageAccess"

object StorageAccess {

    fun listDirectory(context: Context, treeUri: Uri): List<DirEntry> = runCatching {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!tree.isDirectory) return emptyList()
        tree.listFiles()
            .filter { it.exists() }
            .mapNotNull { doc ->
                val name = doc.name ?: doc.uri.lastPathSegment ?: return@mapNotNull null
                DirEntry(
                    uri = doc.uri,
                    name = name,
                    isDirectory = doc.isDirectory,
                    mimeType = doc.type,
                    sizeBytes = doc.length(),
                )
            }
            .sortedWith(compareByDescending<DirEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }.onFailure { Log.e(TAG, "listDirectory failed for $treeUri", it) }
        .getOrDefault(emptyList())

    fun collectMedia(context: Context, treeUri: Uri, recursive: Boolean): List<DirEntry> {
        val out = mutableListOf<DirEntry>()
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!tree.isDirectory) return emptyList()
        collectMediaRec(context, tree, recursive, out)
        return out
    }

    private fun collectMediaRec(
        context: Context,
        dir: DocumentFile,
        recursive: Boolean,
        out: MutableList<DirEntry>,
    ) {
        for (doc in dir.listFiles()) {
            if (!doc.exists()) continue
            if (doc.isDirectory) {
                if (recursive) collectMediaRec(context, doc, recursive, out)
            } else if (doc.isFile) {
                val mime = doc.type
                if (mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true) {
                    val name = doc.name ?: doc.uri.lastPathSegment ?: "Unknown"
                    out.add(
                        DirEntry(
                            uri = doc.uri,
                            name = name,
                            isDirectory = false,
                            mimeType = mime,
                            sizeBytes = doc.length(),
                        )
                    )
                }
            }
        }
    }
}