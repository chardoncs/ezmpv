package dev.chardoncs.ezmpv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import dev.chardoncs.ezmpv.player.MpvSurface
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.player.PlayerState
import dev.chardoncs.ezmpv.player.playlistVisible

private const val LANDSCAPE_OVERLAY_TIMEOUT_MS = 4000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    controller: PlayerController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.playlist.getOrNull(state.currentIndex)
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeNowPlayingScreen(
            controller = controller,
            state = state,
            onBack = onBack,
            modifier = modifier,
        )
    } else {
        PortraitNowPlayingScreen(
            controller = controller,
            state = state,
            trackTitle = track?.title,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitNowPlayingScreen(
    controller: PlayerController,
    state: PlayerState,
    trackTitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = trackTitle ?: "Now Playing",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PlayerVisual(
                    state = state,
                    controller = controller,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.playlistVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
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
            NowPlayingControls(
                state = state,
                isVideoTrack = state.playlist.getOrNull(state.currentIndex)?.isVideo == true,
                onPlayPause = controller::togglePlayPause,
                onSeek = controller::seekTo,
                onNext = controller::next,
                onPrevious = controller::previous,
                onToggleAudioOnly = { controller.setAudioOnly(!state.audioOnly) },
                onTogglePlaylist = { controller.setPlaylistUserOverride(!state.playlistVisible) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LandscapeNowPlayingScreen(
    controller: PlayerController,
    state: PlayerState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(state.currentIndex, state.hasVideo, state.audioOnly) {
        controlsVisible = true
    }
    LaunchedEffect(controlsVisible, state.currentIndex, state.isPlaying) {
        if (controlsVisible) {
            delay(LANDSCAPE_OVERLAY_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { controlsVisible = !controlsVisible },
            ) {
                PlayerVisual(
                    state = state,
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
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
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                ) {
                    NowPlayingControls(
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (state.playlistVisible) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
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

@Composable
private fun PlayerVisual(
    state: PlayerState,
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    if (state.hasVideo) {
        MpvSurface(
            player = controller.player,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        AlbumArtOrPlaceholder(state = state, modifier = modifier)
    }
}

@Composable
private fun AlbumArtOrPlaceholder(
    state: PlayerState,
    modifier: Modifier = Modifier,
) {
    val art = state.currentArt
    if (art != null) {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = "Album art",
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxSize(),
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
                IconButton(onClick = { onSelect(index) }) {
                    dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                        isPlaying = index == state.currentIndex && state.isPlaying,
                        contentDescription = "Play ${item.title}",
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingControls(
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
    modifier: Modifier = Modifier,
) {
    val track = state.playlist.getOrNull(state.currentIndex)
    var dragPosition by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.padding(
            horizontal = if (compact) 12.dp else 16.dp,
            vertical = if (compact) 4.dp else 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        if (showTrackInfo) {
            Text(
                text = track?.title ?: "No track selected",
                style = MaterialTheme.typography.titleLarge,
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
            Text(formatTime(posMs), style = MaterialTheme.typography.labelLarge)
            Text(formatTime(durMs), style = MaterialTheme.typography.labelLarge)
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
            ) {
                dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                    isPlaying = state.isPlaying,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(if (compact) 44.dp else 56.dp),
                    showRing = false,
                    glyphScaleFactor = 1.7f,
                )
            }
            IconButton(
                onClick = onNext,
                enabled = state.currentIndex in 0 until state.playlist.size - 1,
                modifier = Modifier.size(if (compact) 44.dp else 56.dp),
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
