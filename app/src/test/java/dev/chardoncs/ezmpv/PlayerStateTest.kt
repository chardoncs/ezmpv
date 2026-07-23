package dev.chardoncs.ezmpv

import dev.chardoncs.ezmpv.player.PlayerState
import dev.chardoncs.ezmpv.player.playlistVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateTest {
    @Test
    fun playlistVisible_defaultsToFalse() {
        assertFalse(PlayerState().playlistVisible)
    }

    @Test
    fun playlistVisible_isTrueWhenUserExplicitlyShowsIt() {
        assertTrue(PlayerState(playlistUserOverride = true).playlistVisible)
    }

    @Test
    fun playlistVisible_isFalseWhenUserExplicitlyHidesIt() {
        assertFalse(PlayerState(playlistUserOverride = false).playlistVisible)
    }
}
