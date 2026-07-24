package dev.chardoncs.ezmpv.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPrefs by preferencesDataStore("library_prefs")
private val VIDEO_VIEW_MODE = stringPreferencesKey("video_view_mode")
private val VIDEO_GROUP_BY = stringPreferencesKey("video_group_by")
private val AUDIO_VIEW_MODE = stringPreferencesKey("audio_view_mode")
private val AUDIO_GROUP_BY = stringPreferencesKey("audio_group_by")

enum class ViewMode { LIST, GRID }
enum class GroupBy { LOCATION, ARTIST, ALBUM, YEAR }

enum class LibraryType { AUDIO, VIDEO }

class LibraryPreferences(private val context: Context) {

    fun viewMode(type: LibraryType): Flow<ViewMode> =
        context.libraryPrefs.data.map { p ->
            val key = if (type == LibraryType.VIDEO) VIDEO_VIEW_MODE else AUDIO_VIEW_MODE
            val default = if (type == LibraryType.VIDEO) ViewMode.LIST else ViewMode.GRID
            p[key]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: default
        }

    fun groupBy(type: LibraryType): Flow<GroupBy> =
        context.libraryPrefs.data.map { p ->
            val key = if (type == LibraryType.VIDEO) VIDEO_GROUP_BY else AUDIO_GROUP_BY
            val default = if (type == LibraryType.VIDEO) GroupBy.LOCATION else GroupBy.ALBUM
            p[key]?.let { runCatching { GroupBy.valueOf(it) }.getOrNull() } ?: default
        }

    suspend fun setViewMode(type: LibraryType, mode: ViewMode) {
        context.libraryPrefs.edit {
            it[if (type == LibraryType.VIDEO) VIDEO_VIEW_MODE else AUDIO_VIEW_MODE] = mode.name
        }
    }

    suspend fun setGroupBy(type: LibraryType, group: GroupBy) {
        context.libraryPrefs.edit {
            it[if (type == LibraryType.VIDEO) VIDEO_GROUP_BY else AUDIO_GROUP_BY] = group.name
        }
    }
}