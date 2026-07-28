package dev.chardoncs.ezmpv

import android.app.Application
import dev.chardoncs.ezmpv.browse.BookmarkRepository
import dev.chardoncs.ezmpv.browse.BrowseController
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.playlists.PlaylistController
import dev.chardoncs.ezmpv.playlists.PlaylistRepository

class EzmpvApplication : Application() {
    val bookmarkRepository: BookmarkRepository by lazy { BookmarkRepository(this) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(this) }
    val playerController: PlayerController by lazy { PlayerController(this, bookmarkRepository) }
    val browseController: BrowseController by lazy { BrowseController(this, bookmarkRepository) }
    val playlistController: PlaylistController by lazy { PlaylistController(this, playlistRepository, playerController) }
}