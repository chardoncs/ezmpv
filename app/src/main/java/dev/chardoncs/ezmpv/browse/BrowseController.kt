package dev.chardoncs.ezmpv.browse

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseController(context: Context, private val repo: BookmarkRepository) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _bookmarks = MutableStateFlow<List<BrowseBookmark>>(emptyList())
    val bookmarks: StateFlow<List<BrowseBookmark>> = _bookmarks.asStateFlow()

    init {
        scope.launch {
            repo.bookmarks.collect { list -> _bookmarks.value = list }
        }
    }

    fun addBookmark(uri: Uri, title: String, iconType: IconType = IconType.FOLDER) {
        scope.launch {
            repo.addBookmark(BrowseBookmark(uri, title, iconType))
        }
    }

    fun removeBookmark(uri: Uri) {
        scope.launch { repo.removeBookmark(uri) }
    }

    fun isBookmarked(uri: Uri): Boolean = _bookmarks.value.any { it.uri == uri }

    fun toggleBookmark(uri: Uri, title: String) {
        val target = BrowseBookmark(uri, title)
        scope.launch {
            if (repo.contains(uri)) repo.removeBookmark(uri) else repo.addBookmark(target)
        }
    }
}