package dev.chardoncs.ezmpv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chardoncs.ezmpv.player.MpvSurface
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.player.PlayerState
import dev.chardoncs.ezmpv.player.playlistVisible

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    controller: PlayerController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.playlist.getOrNull(state.currentIndex)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = track?.title ?: "Now Playing",
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
                if (state.hasVideo) {
                    MpvSurface(
                        player = controller.player,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AlbumArtOrPlaceholder(state = state)
                }
                this@Column.AnimatedVisibility(
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
                isVideoTrack = track?.isVideo == true,
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
    modifier: Modifier = Modifier,
) {
    val track = state.playlist.getOrNull(state.currentIndex)
    var dragPosition by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onTogglePlaylist,
                modifier = Modifier.size(40.dp),
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
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(80.dp),
                shape = IconButtonDefaults.filledShape,
            ) {
                dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                    isPlaying = state.isPlaying,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(56.dp),
                    showRing = false,
                    glyphScaleFactor = 1.7f,
                )
            }
            IconButton(
                onClick = onNext,
                enabled = state.currentIndex in 0 until state.playlist.size - 1,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }
            if (isVideoTrack) {
                IconButton(
                    onClick = onToggleAudioOnly,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Toggle audio-only",
                        tint = if (state.audioOnly) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    }
}
