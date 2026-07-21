package dev.chardoncs.ezmpv.player

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MpvPlayerAdapter(
    private val controller: PlayerController,
    looper: Looper,
) : SimpleBasePlayer(looper) {

    override fun getState(): State {
        val s = controller.state.value
        val items = s.playlist.mapIndexed { i, item ->
            MediaItemData.Builder(i)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(item.mediaId)
                        .setUri(item.sourceUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(item.title)
                                .setArtist(item.artist)
                                .setAlbumTitle(item.album)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                )
                .setDurationUs(if (item.durationMs > 0) item.durationMs * 1000 else C.TIME_UNSET)
                .setIsSeekable(true)
                .build()
        }
        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(
                s.isPlaying,
                Media3Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackSuppressionReason(Media3Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setPlaybackState(
                if (s.currentIndex in s.playlist.indices) Media3Player.STATE_READY
                else Media3Player.STATE_IDLE
            )
            .setPlaylist(items)
            .setCurrentMediaItemIndex(
                s.currentIndex.coerceIn(0, (s.playlist.size - 1).coerceAtLeast(0))
            )
            .setContentPositionMs(PositionSupplier { controller.player.state.value.positionMs })
            .setIsLoading(s.loading)
            .build()
    }

    fun refresh() = invalidateState()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        controller.setPlaying(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val current = controller.state.value.currentIndex
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != current) {
            controller.selectTrack(mediaItemIndex)
        } else if (positionMs != C.TIME_UNSET) {
            controller.seekTo(positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.setPlaying(false)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    companion object {
        private val AVAILABLE_COMMANDS = Media3Player.Commands.Builder()
            .addAll(
                Media3Player.COMMAND_PLAY_PAUSE,
                Media3Player.COMMAND_STOP,
                Media3Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Media3Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Media3Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Media3Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            )
            .build()
    }
}
