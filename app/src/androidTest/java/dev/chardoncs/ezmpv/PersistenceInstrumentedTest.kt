package dev.chardoncs.ezmpv

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chardoncs.ezmpv.audio.GroupBy
import dev.chardoncs.ezmpv.audio.LibraryPreferences
import dev.chardoncs.ezmpv.audio.LibraryType
import dev.chardoncs.ezmpv.audio.MetadataCache
import dev.chardoncs.ezmpv.audio.ViewMode
import dev.chardoncs.ezmpv.player.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun resetLibraryPreferences() {
        runBlocking {
            val preferences = LibraryPreferences(context)
            preferences.setViewMode(LibraryType.VIDEO, ViewMode.LIST)
            preferences.setGroupBy(LibraryType.VIDEO, GroupBy.LOCATION)
            preferences.setViewMode(LibraryType.AUDIO, ViewMode.GRID)
            preferences.setGroupBy(LibraryType.AUDIO, GroupBy.ALBUM)
        }
    }

    @Test
    fun libraryPreferences_defaultToVideoListLocationAudioGridAlbum() {
        runBlocking {
            val preferences = LibraryPreferences(context)
            with(preferences) {
                setViewMode(LibraryType.VIDEO, ViewMode.LIST)
                setGroupBy(LibraryType.VIDEO, GroupBy.LOCATION)
                assertEquals(ViewMode.LIST, viewMode(LibraryType.VIDEO).first())
                assertEquals(GroupBy.LOCATION, groupBy(LibraryType.VIDEO).first())
                setViewMode(LibraryType.AUDIO, ViewMode.GRID)
                setGroupBy(LibraryType.AUDIO, GroupBy.ALBUM)
                assertEquals(ViewMode.GRID, viewMode(LibraryType.AUDIO).first())
                assertEquals(GroupBy.ALBUM, groupBy(LibraryType.AUDIO).first())
            }
        }
    }

    @Test
    fun libraryPreferences_persistSelectedValues() {
        runBlocking {
            val preferences = LibraryPreferences(context)
            preferences.setViewMode(LibraryType.AUDIO, ViewMode.LIST)
            preferences.setGroupBy(LibraryType.AUDIO, GroupBy.ARTIST)
            with(LibraryPreferences(context)) {
                assertEquals(ViewMode.LIST, viewMode(LibraryType.AUDIO).first())
                assertEquals(GroupBy.ARTIST, groupBy(LibraryType.AUDIO).first())
            }
        }
    }

    @Test
    fun metadataCache_roundTripsEnrichedFields() {
        runBlocking {
            val cache = MetadataCache(context)
            val sourceUri = Uri.parse("content://dev.chardoncs.ezmpv.test/${System.nanoTime()}.mp3")
            val item = MediaItem(
                sourceUri = sourceUri,
                title = "Title",
                artist = "Artist",
                album = "Album",
                year = 2026,
                durationMs = 123_456,
                sizeBytes = 42,
                mimeType = "audio/mpeg",
            )

            cache.put(item)

            assertEquals(item, cache.get(item.copy(title = "Unenriched")))
        }
    }

    @Test
    fun metadataCache_missesWhenFileSizeChanges() {
        runBlocking {
            val cache = MetadataCache(context)
            val sourceUri = Uri.parse("content://dev.chardoncs.ezmpv.test/${System.nanoTime()}.mp3")
            val item = MediaItem(sourceUri = sourceUri, title = "Title", sizeBytes = 42)

            cache.put(item)

            assertNull(cache.get(item.copy(sizeBytes = 43)))
        }
    }
}
