package dev.chardoncs.ezmpv.browse

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.browseStore by preferencesDataStore("browse_prefs")
private val BOOKMARKS = stringPreferencesKey("bookmarks")
private val json = Json { ignoreUnknownKeys = true }
private val bookmarksSerializer = ListSerializer(BrowseBookmark.serializer())
private const val TAG = "BookmarkRepository"

class BookmarkRepository(private val context: Context) {

    val bookmarks: Flow<List<BrowseBookmark>> = context.browseStore.data
        .map { decode(it[BOOKMARKS]) }

    private fun decode(raw: String?): List<BrowseBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(bookmarksSerializer, raw) }
            .onFailure { Log.w(TAG, "decode failed", it) }
            .getOrDefault(emptyList())
    }

    suspend fun addBookmark(bookmark: BrowseBookmark) {
        val current = bookmarks.first()
        if (current.any { it.uri == bookmark.uri }) return
        context.browseStore.edit { it[BOOKMARKS] = encode(current + bookmark) }
    }

    suspend fun removeBookmark(uri: Uri) {
        val current = bookmarks.first()
        context.browseStore.edit { it[BOOKMARKS] = encode(current.filterNot { b -> b.uri == uri }) }
    }

    suspend fun contains(uri: Uri): Boolean =
        bookmarks.first().any { it.uri == uri }

    private fun encode(list: List<BrowseBookmark>): String =
        json.encodeToString(bookmarksSerializer, list)
}