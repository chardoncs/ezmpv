package dev.chardoncs.ezmpv.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chardoncs.ezmpv.audio.GroupBy
import dev.chardoncs.ezmpv.audio.LibraryPreferences
import dev.chardoncs.ezmpv.audio.ViewMode
import dev.chardoncs.ezmpv.player.MediaItem
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.player.PlayerState
import kotlinx.coroutines.launch

enum class LibraryType { AUDIO, VIDEO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    type: LibraryType,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    showPickFile: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { LibraryPreferences(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val viewMode by prefs.viewMode.collectAsStateWithLifecycle(ViewMode.LIST)
    val groupBy by prefs.groupBy.collectAsStateWithLifecycle(GroupBy.LOCATION)
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var groupMenuOpen by remember { mutableStateOf(false) }

    val grantLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) controller.grantFolder(uri) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            controller.playAdhoc(uri, mime)
            onOpenPlayer()
        }
    }

    val tracks = remember(state.library, type) {
        state.library.withIndex().filter { indexed ->
            when (type) {
                LibraryType.AUDIO -> !indexed.value.isVideo
                LibraryType.VIDEO -> indexed.value.isVideo
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (type == LibraryType.AUDIO) "Audio" else "Video") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            prefs.setViewMode(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                        }
                    }) {
                        Icon(
                            if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "Toggle view mode",
                        )
                    }
                    IconButton(onClick = { groupMenuOpen = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Group by")
                    }
                    DropdownMenu(expanded = groupMenuOpen, onDismissRequest = { groupMenuOpen = false }) {
                        GroupBy.entries.forEach { gb ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Group by ${gb.name.lowercase().replaceFirstChar { it.titlecase() }}" +
                                                if (gb == groupBy) "  ✓" else "",
                                    )
                                },
                                onClick = {
                                    groupMenuOpen = false
                                    scope.launch { prefs.setGroupBy(gb) }
                                },
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (showPickFile) {
                            DropdownMenuItem(
                                text = { Text("Pick file…") },
                                onClick = {
                                    menuOpen = false
                                    pickLauncher.launch(arrayOf("video/*", "audio/*"))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Add folder…") },
                            onClick = {
                                menuOpen = false
                                grantLauncher.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                menuOpen = false
                                controller.refreshPlaylist()
                            },
                        )
                        state.selectedFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text("Remove: ${shortenPath(folder)}") },
                                onClick = {
                                    menuOpen = false
                                    controller.revokeFolder(folder)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LibraryBody(
            state = state,
            tracks = tracks,
            viewMode = viewMode,
            groupBy = groupBy,
            onGrant = { grantLauncher.launch(null) },
            onPlay = { libraryIndex ->
                val queue = tracks.map { it.value }
                val posInQueue = tracks.indexOfFirst { it.index == libraryIndex }.coerceAtLeast(0)
                controller.playFromLibrary(queue, posInQueue)
                onOpenPlayer()
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@Composable
private fun LibraryBody(
    state: PlayerState,
    tracks: List<IndexedValue<MediaItem>>,
    viewMode: ViewMode,
    groupBy: GroupBy,
    onGrant: () -> Unit,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.selectedFolders.isEmpty() -> EmptyFolderState(onGrant, modifier)
        state.loading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        tracks.isEmpty() -> Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No ${if (state.selectedFolders.isNotEmpty()) "matching" else ""} files found.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            val grouped = remember(tracks, groupBy) { groupTracks(tracks, groupBy) }
            if (viewMode == ViewMode.LIST) {
                GroupedList(grouped, state, onPlay, modifier)
            } else {
                GroupedGrid(grouped, state, onPlay, modifier)
            }
        }
    }
}

@Composable
private fun EmptyFolderState(onGrant: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("No folders selected.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onGrant) { Text("Add folder") }
    }
}

@Composable
private fun GroupedList(
    grouped: List<Pair<String, List<IndexedValue<MediaItem>>>>,
    state: PlayerState,
    onPlay: (Int) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier) {
        grouped.forEach { (header, items) ->
            item(key = "h_$header") {
                Text(
                    text = "$header  (${items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(items, key = { _, iv -> iv.index }) { _, iv ->
                TrackListRow(
                    track = iv.value,
                    isCurrent = iv.index == state.currentIndex,
                    isPlaying = iv.index == state.currentIndex && state.isPlaying,
                    onClick = { onPlay(iv.index) },
                )
            }
        }
    }
}

@Composable
private fun TrackListRow(
    track: MediaItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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

@Composable
private fun GroupedGrid(
    grouped: List<Pair<String, List<IndexedValue<MediaItem>>>>,
    state: PlayerState,
    onPlay: (Int) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        grouped.forEach { (header, items) ->
            item(key = "gh_$header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "$header  (${items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            gridItems(items, key = { it.index }) { iv ->
                TrackGridCard(
                    track = iv.value,
                    isCurrent = iv.index == state.currentIndex,
                    isPlaying = iv.index == state.currentIndex && state.isPlaying,
                    onClick = { onPlay(iv.index) },
                )
            }
        }
    }
}

@Composable
private fun TrackGridCard(
    track: MediaItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .size(140.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = track.artist ?: "Unknown artist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun groupTracks(
    tracks: List<IndexedValue<MediaItem>>,
    groupBy: GroupBy,
): List<Pair<String, List<IndexedValue<MediaItem>>>> {
    return tracks.groupBy { iv -> keyForGroup(iv.value, groupBy) }
        .toSortedMap(compareBy { it.lowercase() })
        .map { it.key to it.value }
}

private fun keyForGroup(item: MediaItem, groupBy: GroupBy): String = when (groupBy) {
    GroupBy.LOCATION -> parentFolder(item.sourceUri)
    GroupBy.ARTIST -> item.artist ?: "Unknown artist"
    GroupBy.ALBUM -> item.album ?: "Unknown album"
    GroupBy.YEAR -> item.year?.toString() ?: "Unknown year"
}

private fun parentFolder(uri: android.net.Uri): String {
    val seg = uri.lastPathSegment ?: return "Unknown location"
    val cut = seg.lastIndexOf('/')
    return if (cut > 0) seg.substring(0, cut) else seg
}

private fun shortenPath(uri: android.net.Uri): String {
    val last = uri.lastPathSegment ?: uri.toString()
    return last.substringAfterLast('/').ifBlank { last }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}