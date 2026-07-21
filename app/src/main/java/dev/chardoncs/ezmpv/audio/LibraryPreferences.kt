package dev.chardoncs.ezmpv.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPrefs by preferencesDataStore("library_prefs")
private val VIEW_MODE = stringPreferencesKey("view_mode")
private val GROUP_BY = stringPreferencesKey("group_by")

enum class ViewMode { LIST, GRID }
enum class GroupBy { LOCATION, ARTIST, ALBUM, YEAR }

class LibraryPreferences(private val context: Context) {
    val viewMode: Flow<ViewMode> = context.libraryPrefs.data
        .map { p -> p[VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST }
    val groupBy: Flow<GroupBy> = context.libraryPrefs.data
        .map { p -> p[GROUP_BY]?.let { runCatching { GroupBy.valueOf(it) }.getOrNull() } ?: GroupBy.LOCATION }

    suspend fun setViewMode(mode: ViewMode) {
        context.libraryPrefs.edit { it[VIEW_MODE] = mode.name }
    }
    suspend fun setGroupBy(group: GroupBy) {
        context.libraryPrefs.edit { it[GROUP_BY] = group.name }
    }
}