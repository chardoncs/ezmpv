package dev.chardoncs.ezmpv.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chardoncs.ezmpv.player.PlayerController

private const val MINI_PLAYER_EXPAND_THRESHOLD_DP = 64

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.MiniPlayerBar(
    controller: PlayerController,
    onClick: () -> Unit,
    onExpandProgress: (Float) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.playlist.getOrNull(state.currentIndex) ?: return
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var baseHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val expandThreshold = with(androidx.compose.ui.platform.LocalDensity.current) {
        MINI_PLAYER_EXPAND_THRESHOLD_DP.dp.toPx()
    }
    val density = androidx.compose.ui.platform.LocalDensity.current

    with(sharedTransitionScope) {
        Surface(
            modifier = this@MiniPlayerBar
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (baseHeightPx == 0) baseHeightPx = size.height
                }
                .heightIn(
                    min = with(density) {
                        (baseHeightPx.toFloat() - swipeOffset)
                            .coerceAtLeast(baseHeightPx.toFloat())
                            .toDp()
                    },
                )
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "player-container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs > 0)
                        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(expandThreshold) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount < 0f || swipeOffset < 0f) {
                                    change.consume()
                                    swipeOffset = (swipeOffset + dragAmount).coerceAtMost(0f)
                                    val progress = (-swipeOffset / expandThreshold).coerceIn(0f, 1f)
                                    onExpandProgress(progress)
                                }
                            },
                            onDragEnd = {
                                if (swipeOffset <= -expandThreshold) {
                                    swipeOffset = 0f
                                    onClick()
                                } else {
                                    swipeOffset = 0f
                                    onExpandProgress(0f)
                                }
                            },
                            onDragCancel = {
                                swipeOffset = 0f
                                onExpandProgress(0f)
                            },
                        )
                    }
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val art = state.currentArt
                if (state.hasVideo && !state.audioOnly) {
                    dev.chardoncs.ezmpv.player.MpvSurface(
                        player = controller.player,
                        modifier = Modifier
                            .size(44.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "player-art"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                    )
                } else if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier = Modifier
                            .size(44.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "player-art"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(8.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "player-art"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = controller::togglePlayPause) {
                    dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                        isPlaying = state.isPlaying,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(
                    onClick = controller::next,
                    enabled = state.currentIndex in 0 until state.playlist.size - 1,
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
    }
}