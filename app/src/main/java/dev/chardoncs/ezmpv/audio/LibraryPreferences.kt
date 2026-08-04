package dev.chardoncs.ezmpv.audio

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.chardoncs.ezmpv.player.PlaySequence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPrefs by preferencesDataStore("library_prefs")
private val VIDEO_VIEW_MODE = stringPreferencesKey("video_view_mode")
private val VIDEO_GROUP_BY = stringPreferencesKey("video_group_by")
private val AUDIO_VIEW_MODE = stringPreferencesKey("audio_view_mode")
private val AUDIO_GROUP_BY = stringPreferencesKey("audio_group_by")
private val RESTART_ON_PREVIOUS = booleanPreferencesKey("restart_track_on_previous")
private val PLAY_SEQUENCE = stringPreferencesKey("play_sequence")

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

    fun restartOnPrevious(): Flow<Boolean> =
        context.libraryPrefs.data.map { it[RESTART_ON_PREVIOUS] ?: false }

    suspend fun setRestartOnPrevious(enabled: Boolean) {
        context.libraryPrefs.edit { it[RESTART_ON_PREVIOUS] = enabled }
    }

    fun playSequence(): Flow<PlaySequence> =
        context.libraryPrefs.data.map { p ->
            p[PLAY_SEQUENCE]?.let { runCatching { PlaySequence.valueOf(it) }.getOrNull() }
                ?: PlaySequence.SEQUENCE
        }

    suspend fun setPlaySequence(mode: PlaySequence) {
        context.libraryPrefs.edit { it[PLAY_SEQUENCE] = mode.name }
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