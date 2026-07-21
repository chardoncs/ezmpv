package dev.chardoncs.ezmpv.player

import android.graphics.Bitmap
import android.os.Looper
import java.io.ByteArrayOutputStream
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

    private var artworkBitmap: Bitmap? = null
    private var artworkData: ByteArray? = null

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
                                .setArtworkData(
                                    if (i == s.currentIndex) getArtworkData(s.currentArt) else null,
                                    MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                                )
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
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackSuppressionReason(PLAYBACK_SUPPRESSION_REASON_NONE)
            .setPlaybackState(
                if (s.currentIndex in s.playlist.indices) STATE_READY
                else STATE_IDLE
            )
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(
                s.currentIndex.coerceIn(0, (s.playlist.size - 1).coerceAtLeast(0))
            )
            .setContentPositionMs { controller.player.state.value.positionMs }
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
        when (seekCommand) {
            COMMAND_SEEK_BACK -> controller.seekTo(
                (controller.state.value.positionMs - SEEK_INCREMENT_MS).coerceAtLeast(0)
            )
            COMMAND_SEEK_FORWARD -> controller.seekTo(
                (controller.state.value.positionMs + SEEK_INCREMENT_MS).let { position ->
                    val duration = controller.state.value.durationMs
                    if (duration > 0) position.coerceAtMost(duration) else position
                }
            )
            else -> if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != current) {
                controller.selectTrack(mediaItemIndex)
            } else if (positionMs != C.TIME_UNSET) {
                controller.seekTo(positionMs)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.setPlaying(false)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun getArtworkData(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) {
            artworkBitmap = null
            artworkData = null
            return null
        }
        if (bitmap !== artworkBitmap) {
            artworkBitmap = bitmap
            artworkData = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
        }
        return artworkData
    }

    companion object {
        private const val SEEK_INCREMENT_MS = 10_000L
        private val AVAILABLE_COMMANDS = Media3Player.Commands.Builder()
            .addAll(
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_TIMELINE,
                COMMAND_GET_METADATA,
                COMMAND_PLAY_PAUSE,
                COMMAND_STOP,
                COMMAND_SEEK_BACK,
                COMMAND_SEEK_FORWARD,
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_MEDIA_ITEM,
            )
            .build()
    }
}
