package dev.chardoncs.ezmpv.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chardoncs.ezmpv.audio.AudioTrack
import dev.chardoncs.ezmpv.audio.AudioUiState
import dev.chardoncs.ezmpv.audio.AudioViewModel
import dev.chardoncs.ezmpv.audio.playlistVisible

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.AudioScreen(
    viewModel: AudioViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    val grantLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.grantFolder(uri) }

    DisposableEffect(Unit) {
        viewModel.startController()
        onDispose { viewModel.stopController() }
    }

    Scaffold(
        modifier = fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add folder…") },
                            onClick = {
                                menuOpen = false
                                grantLauncher.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh playlist") },
                            onClick = {
                                menuOpen = false
                                viewModel.refreshPlaylist()
                            },
                        )
                        uiState.selectedFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text("Remove: ${shortenPath(folder)}") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.revokeFolder(folder)
                                },
                            )
                        }
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
            AlbumArtHeader(
                state = uiState,
                visible = uiState.currentArt != null && !uiState.playlistVisible,
                modifier = Modifier.fillMaxWidth(),
            )
            NowPlayingControls(
                state = uiState,
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onTogglePlaylist = {
                    viewModel.setPlaylistUserOverride(!uiState.playlistVisible)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = uiState.playlistVisible,
                enter = expandVertically(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                PlaylistContent(
                    state = uiState,
                    onSelect = viewModel::selectTrack,
                    onGrant = { grantLauncher.launch(null) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AlbumArtHeader(
    state: AudioUiState,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = spring()) { fullHeight -> fullHeight } +
            fadeIn(animationSpec = spring()),
        exit = slideOutVertically(animationSpec = spring()) { fullHeight -> -fullHeight } +
            fadeOut(animationSpec = spring()),
    ) {
        val bmp = state.currentArt
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Album art",
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = modifier.fillMaxWidth().aspectRatio(1f))
        }
    }
}

@Composable
private fun NowPlayingControls(
    state: AudioUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrack = state.playlist.getOrNull(state.currentIndex)
    var dragPosition by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = currentTrack?.title ?: "No track selected",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = currentTrack?.let { it.artist ?: "Unknown artist" }
                ?: "Pick a track from the playlist",
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(posMs), style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = state.currentIndex > 0) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = state.currentIndex in 0 until state.playlist.lastIndex,
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = onTogglePlaylist) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Toggle playlist")
                }
            }
            Text(formatTime(durMs), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PlaylistContent(
    state: AudioUiState,
    onSelect: (Int) -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.selectedFolders.isEmpty() -> {
            Column(
                modifier = modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No music folders selected.", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Button(onClick = onGrant) {
                    Text("Grant Music folder")
                }
            }
        }
        state.loading -> {
            Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.playlist.isEmpty() -> {
            Text(
                "No audio files found in the selected folders.",
                modifier = modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            LazyColumn(modifier = modifier) {
                itemsIndexed(state.playlist) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = index == state.currentIndex,
                        isPlaying = index == state.currentIndex && state.isPlaying,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
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
        Text(
            text = formatTime(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onClick) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play ${track.title}",
            )
        }
    }
}

private val <T> List<T>.lastIndex: Int get() = size - 1

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun shortenPath(uri: android.net.Uri): String {
    val last = uri.lastPathSegment ?: uri.toString()
    return last.substringAfterLast('/').ifBlank { last }
}