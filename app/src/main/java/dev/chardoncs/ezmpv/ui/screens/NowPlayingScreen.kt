package dev.chardoncs.ezmpv.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import dev.chardoncs.ezmpv.player.MpvSurface
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.player.PlayerState
import dev.chardoncs.ezmpv.player.VideoSurfaceHost
import dev.chardoncs.ezmpv.player.VideoTarget
import dev.chardoncs.ezmpv.player.playlistVisible
import kotlin.time.Duration.Companion.milliseconds

private const val LANDSCAPE_OVERLAY_TIMEOUT_MS = 4000L
private val LANDSCAPE_PLAYLIST_WIDTH = 320.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    controller: PlayerController,
    videoHost: VideoSurfaceHost,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isLandscape, state.playlistVisible, state.hasVideo) {
        if (state.hasVideo) {
            val ratio = if (isLandscape && state.playlistVisible) {
                val screenWidthDp = configuration.screenWidthDp
                if (screenWidthDp > 0) LANDSCAPE_PLAYLIST_WIDTH.value / screenWidthDp else 0f
            } else 0f
            controller.player.setVideoRightMarginRatio(ratio)
        }
    }

    if (isLandscape) {
        LandscapeNowPlayingScreen(
            controller = controller,
            videoHost = videoHost,
            state = state,
            onBack = onBack,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    } else {
        PortraitNowPlayingScreen(
            controller = controller,
            videoHost = videoHost,
            state = state,
            onBack = onBack,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PortraitNowPlayingScreen(
    controller: PlayerController,
    videoHost: VideoSurfaceHost,
    state: PlayerState,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "player-container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            containerColor = Color.Transparent,
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = state.playlistVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    dev.chardoncs.ezmpv.ui.components.CompactTrackHeader(
                        state = state,
                        videoHost = videoHost,
                        artSize = 44,
                        horizontalPadding = 12,
                        verticalPadding = 6,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(if (state.playlistVisible) 0.dp else 16.dp)
                    .clip(RoundedCornerShape(if (state.playlistVisible) 0.dp else 16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!state.playlistVisible) {
                    PlayerVisual(
                        state = state,
                        videoHost = videoHost,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.playlistVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            PlaylistOverlay(
                                state = state,
                                onSelect = controller::selectTrack,
                            )
                        }
                    }
                }
            }
            Modifier.fillMaxWidth().NowPlayingControls(
                state = state,
                isVideoTrack = state.playlist.getOrNull(state.currentIndex)?.isVideo == true,
                onPlayPause = controller::togglePlayPause,
                onSeek = controller::seekTo,
                onNext = controller::next,
                onPrevious = controller::previous,
                onToggleAudioOnly = { controller.setAudioOnly(!state.audioOnly) },
                onTogglePlaylist = { controller.setPlaylistUserOverride(!state.playlistVisible) },
                showTrackInfo = !state.playlistVisible,
            )
        }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LandscapeNowPlayingScreen(
    controller: PlayerController,
    videoHost: VideoSurfaceHost,
    state: PlayerState,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(state.currentIndex, state.hasVideo, state.audioOnly) {
        controlsVisible = true
    }
    LaunchedEffect(controlsVisible, state.currentIndex, state.isPlaying) {
        if (controlsVisible) {
            delay(LANDSCAPE_OVERLAY_TIMEOUT_MS.milliseconds)
            controlsVisible = false
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "player-container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { controlsVisible = !controlsVisible },
        ) {
            PlayerVisual(
                state = state,
                videoHost = videoHost,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = state.playlist.getOrNull(state.currentIndex)?.title
                                    ?: "Now Playing",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.playlist.getOrNull(state.currentIndex)?.artist?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Modifier.fillMaxWidth().NowPlayingControls(
                            state = state,
                            isVideoTrack = state.playlist.getOrNull(state.currentIndex)?.isVideo == true,
                            onPlayPause = controller::togglePlayPause,
                            onSeek = controller::seekTo,
                            onNext = controller::next,
                            onPrevious = controller::previous,
                            onToggleAudioOnly = { controller.setAudioOnly(!state.audioOnly) },
                            onTogglePlaylist = { controller.setPlaylistUserOverride(!state.playlistVisible) },
                        compact = true,
                        showTrackInfo = false,
                    )
                }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = state.playlistVisible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        PlaylistOverlay(
                            state = state,
                            onSelect = controller::selectTrack,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerVisual(
    state: PlayerState,
    videoHost: VideoSurfaceHost,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        if (state.hasVideo) {
            MpvSurface(
                host = videoHost,
                target = VideoTarget.FULL,
                modifier = modifier
                    .fillMaxSize()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "player-art"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
            )
        } else {
            AlbumArtOrPlaceholder(
                state = state,
                modifier = modifier,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumArtOrPlaceholder(
    state: PlayerState,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val art = state.currentArt
    with(sharedTransitionScope) {
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = "Album art",
                modifier = modifier
                    .fillMaxSize()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "player-art"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                contentScale = ContentScale.Fit,
            )
        } else {
            Surface(
                modifier = modifier
                    .fillMaxSize()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "player-art"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.35f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistOverlay(
    state: PlayerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(state.playlist) { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == state.currentIndex)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { onSelect(index) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                        isPlaying = index == state.currentIndex && state.isPlaying,
                        contentDescription = "Play ${item.title}",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.NowPlayingControls(
    state: PlayerState,
    isVideoTrack: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onTogglePlaylist: () -> Unit,
    compact: Boolean = false,
    showTrackInfo: Boolean = true,
) {
    val track = state.playlist.getOrNull(state.currentIndex)
    var dragPosition by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = padding(
            horizontal = if (compact) 12.dp else 16.dp,
            vertical = if (compact) 4.dp else 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        if (showTrackInfo) {
            Text(
                text = track?.title ?: "No track selected",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artist ?: "Unknown artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val posMs = dragPosition ?: state.positionMs
        val durMs = state.durationMs
        Slider(
            value = if (durMs > 0) posMs.toFloat() / durMs else 0f,
            onValueChange = { v ->
                if (durMs > 0) dragPosition = (v * durMs).toLong()
            },
            onValueChangeFinished = {
                dragPosition?.let(onSeek)
                dragPosition = null
            },
            enabled = durMs > 0,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(posMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(durMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 2.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(
                if (compact) 8.dp else 12.dp,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onTogglePlaylist,
                modifier = Modifier.size(if (compact) 36.dp else 40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Toggle playlist",
                    tint = if (state.playlistVisible) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onPrevious,
                enabled = state.currentIndex > 0,
                modifier = Modifier.size(if (compact) 44.dp else 56.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(if (compact) 64.dp else 80.dp),
                shape = IconButtonDefaults.filledShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                    isPlaying = state.isPlaying,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(if (compact) 44.dp else 56.dp),
                    showRing = false,
                    glyphScaleFactor = 1.7f,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconButton(
                onClick = onNext,
                enabled = state.currentIndex in 0 until state.playlist.size - 1,
                modifier = Modifier.size(if (compact) 44.dp else 56.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                )
            }
            if (isVideoTrack) {
                IconButton(
                    onClick = onToggleAudioOnly,
                    modifier = Modifier.size(if (compact) 36.dp else 40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = "Toggle audio-only",
                        tint = if (state.audioOnly) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(if (compact) 36.dp else 40.dp))
            }
        }
    }
}
