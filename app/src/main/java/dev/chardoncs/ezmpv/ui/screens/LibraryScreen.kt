package dev.chardoncs.ezmpv.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.R
import dev.chardoncs.ezmpv.audio.GroupBy
import dev.chardoncs.ezmpv.audio.LibraryPreferences
import dev.chardoncs.ezmpv.audio.LibraryType
import dev.chardoncs.ezmpv.audio.ViewMode
import dev.chardoncs.ezmpv.player.MediaItem
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.player.PlayerState
import dev.chardoncs.ezmpv.playlists.Playlist
import dev.chardoncs.ezmpv.playlists.ResolvedEntry
import dev.chardoncs.ezmpv.playlists.ResolvedPlaylist
import dev.chardoncs.ezmpv.playlists.displayName
import dev.chardoncs.ezmpv.playlists.toMediaItem
import dev.chardoncs.ezmpv.ui.components.AddToPlaylistDialog
import dev.chardoncs.ezmpv.ui.components.LibraryTrackPickerSheet
import dev.chardoncs.ezmpv.ui.components.PlaylistCover
import dev.chardoncs.ezmpv.ui.components.PlaylistEditDialog
import kotlinx.coroutines.launch

private sealed class LibraryScreen {
    data object Home : LibraryScreen()
    data class Section(val type: LibraryType) : LibraryScreen()
    data class DrillDown(val type: LibraryType, val groupKey: String) : LibraryScreen()
    data class PlaylistDetail(val playlistId: String) : LibraryScreen()
}

private val LibraryScreenSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateList<LibraryScreen>, MutableList<Any>>(
    save = { stack ->
        stack.map { screen ->
            when (screen) {
                is LibraryScreen.Home -> listOf("home")
                is LibraryScreen.Section -> listOf("section", screen.type.name)
                is LibraryScreen.DrillDown -> listOf("drill", screen.type.name, screen.groupKey)
                is LibraryScreen.PlaylistDetail -> listOf("playlist", screen.playlistId)
            }
        }.toMutableList()
    },
    restore = { saved ->
        saved.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val list = item as List<String>
            when (list[0]) {
                "home" -> LibraryScreen.Home
                "section" -> runCatching { LibraryType.valueOf(list[1]) }
                    .getOrNull()?.let { LibraryScreen.Section(it) }
                "drill" -> runCatching { LibraryType.valueOf(list[1]) }
                    .getOrNull()?.let { LibraryScreen.DrillDown(it, list[2]) }
                "playlist" -> LibraryScreen.PlaylistDetail(list[1])
                else -> null
            }
        }.let { androidx.compose.runtime.mutableStateListOf<LibraryScreen>().apply { addAll(it) } }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    playerOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playlistController = remember {
        (context.applicationContext as EzmpvApplication).playlistController
    }
    val playlists by playlistController.playlists.collectAsStateWithLifecycle()
    val resolved by playlistController.resolved.collectAsStateWithLifecycle()
    val prefs = remember { LibraryPreferences(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val unavailableMsg = stringResource(R.string.playlist_unavailable)
    var createDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var coverPickTarget by remember { mutableStateOf<String?>(null) }

    val audioViewMode by prefs.viewMode(LibraryType.AUDIO).collectAsStateWithLifecycle(ViewMode.GRID)
    val audioGroupBy by prefs.groupBy(LibraryType.AUDIO).collectAsStateWithLifecycle(GroupBy.ALBUM)
    val videoViewMode by prefs.viewMode(LibraryType.VIDEO).collectAsStateWithLifecycle(ViewMode.LIST)
    val videoGroupBy by prefs.groupBy(LibraryType.VIDEO).collectAsStateWithLifecycle(GroupBy.LOCATION)

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            controller.playAdhoc(uri, mime)
            onOpenPlayer()
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val targetId = coverPickTarget
        if (uri != null && targetId != null) {
            playlistController.update(targetId, coverImageUri = uri)
        }
        coverPickTarget = null
    }

    val audioTracks = remember(state.library) {
        state.library.withIndex().filter { !it.value.isVideo }
    }
    val videoTracks = remember(state.library) {
        state.library.withIndex().filter { it.value.isVideo }
    }

    val backStack = rememberSaveable(saver = LibraryScreenSaver) {
        mutableStateListOf<LibraryScreen>(LibraryScreen.Home)
    }
    val current = backStack.last()
    fun push(screen: LibraryScreen) { backStack.add(screen) }
    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
    BackHandler(enabled = !playerOpen && backStack.size > 1) { pop() }

    val title = when (current) {
        is LibraryScreen.Home -> "Library"
        is LibraryScreen.Section -> if (current.type == LibraryType.AUDIO) "Audio" else "Video"
        is LibraryScreen.DrillDown -> current.groupKey
        is LibraryScreen.PlaylistDetail -> playlists.firstOrNull { it.id == current.playlistId }
            ?.let { it.displayName(context) } ?: "Playlist"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (current is LibraryScreen.Home) {
                FloatingActionButton(onClick = { createDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlist_create))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (backStack.size > 1) {
                        IconButton(onClick = { pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (current is LibraryScreen.Home) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Pick file…") },
                                onClick = {
                                    menuOpen = false
                                    pickLauncher.launch(arrayOf("video/*", "audio/*"))
                                },
                            )
                        }
                    } else if (current is LibraryScreen.PlaylistDetail) {
                        val pl = playlists.firstOrNull { it.id == current.playlistId }
                        PlaylistDetailActions(
                            playlist = pl,
                            onRename = { renameTarget = pl },
                            onChangeCover = {
                                coverPickTarget = current.playlistId
                                coverPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            onClearCover = { playlistController.update(current.playlistId, clearCover = true) },
                            onDelete = { playlistController.delete(current.playlistId); pop() },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { controller.refreshPlaylist() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when {
                state.selectedFolders.isEmpty() && current is LibraryScreen.Home ->
                    EmptyLibraryState(Modifier.fillMaxSize())
                current is LibraryScreen.Home -> LibraryHome(
                    audioCount = audioTracks.size,
                    videoCount = videoTracks.size,
                    playlists = playlists,
                    resolved = resolved,
                    controller = controller,
                    onOpenVideo = { push(LibraryScreen.Section(LibraryType.VIDEO)) },
                    onOpenAudio = { push(LibraryScreen.Section(LibraryType.AUDIO)) },
                    onOpenPlaylist = { id -> push(LibraryScreen.PlaylistDetail(id)) },
                )
                current is LibraryScreen.Section -> SectionScreen(
                    type = current.type,
                    state = state,
                    tracks = if (current.type == LibraryType.AUDIO) audioTracks else videoTracks,
                    viewMode = if (current.type == LibraryType.AUDIO) audioViewMode else videoViewMode,
                    groupBy = if (current.type == LibraryType.AUDIO) audioGroupBy else videoGroupBy,
                    prefs = prefs,
                    scope = scope,
                    controller = controller,
                    onOpenPlayer = onOpenPlayer,
                    onDrillInto = { key -> push(LibraryScreen.DrillDown(current.type, key)) },
                )
                current is LibraryScreen.DrillDown -> GroupDrillDown(
                    state = state,
                    tracks = if (current.type == LibraryType.AUDIO) audioTracks else videoTracks,
                    viewMode = if (current.type == LibraryType.AUDIO) audioViewMode else videoViewMode,
                    groupKey = current.groupKey,
                    groupBy = if (current.type == LibraryType.AUDIO) audioGroupBy else videoGroupBy,
                    isAudio = current.type == LibraryType.AUDIO,
                    controller = controller,
                    onOpenPlayer = onOpenPlayer,
                )
                current is LibraryScreen.PlaylistDetail -> PlaylistDetailScreen(
                    playlistId = current.playlistId,
                    playlists = playlists,
                    resolved = resolved,
                    state = state,
                    controller = controller,
                    playlistController = playlistController,
                    onOpenPlayer = onOpenPlayer,
                    onUnavailable = { scope.launch { snackbarHostState.showSnackbar(unavailableMsg) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (createDialog) {
        PlaylistEditDialog(
            onDismiss = { createDialog = false },
            onConfirm = { name, desc ->
                createDialog = false
                playlistController.create(name, desc)
            },
        )
    }
    renameTarget?.let { pl ->
        PlaylistEditDialog(
            initialName = pl.displayName(context),
            initialDescription = pl.description,
            title = stringResource(R.string.playlist_rename),
            confirmLabel = stringResource(R.string.playlist_save),
            onDismiss = { renameTarget = null },
            onConfirm = { name, desc ->
                renameTarget = null
                playlistController.update(pl.id, name = name, description = desc)
            },
        )
    }
}

@Composable
private fun EmptyLibraryState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("No bookmarks yet.", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Add a folder from the Browse tab to populate the library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryHome(
    audioCount: Int,
    videoCount: Int,
    playlists: List<Playlist>,
    resolved: List<ResolvedPlaylist>,
    controller: PlayerController,
    onOpenVideo: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "playlists_header") {
            SectionHeader("Playlists")
        }
        if (playlists.isEmpty()) {
            item(key = "playlists_empty") {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            val ordered = playlists.sortedWith(
                compareBy(
                    { !it.isFavorites },
                    { it.displayName(context).lowercase() },
                ),
            )
            items(ordered, key = { it.id }) { pl ->
                val rp = resolved.firstOrNull { it.playlist.id == pl.id }
                PlaylistRow(
                    playlist = pl,
                    resolved = rp,
                    controller = controller,
                    onClick = { onOpenPlaylist(pl.id) },
                )
            }
        }

        item(key = "video_entry") {
            SectionHeader("Media")
            LibraryEntryRow(
                title = "Video",
                subtitle = if (videoCount == 0) "No files" else "$videoCount file" + if (videoCount != 1) "s" else "",
                icon = Icons.Filled.VideoLibrary,
                onClick = onOpenVideo,
            )
        }
        item(key = "audio_entry") {
            LibraryEntryRow(
                title = "Audio",
                subtitle = if (audioCount == 0) "No files" else "$audioCount file" + if (audioCount != 1) "s" else "",
                icon = Icons.Filled.GraphicEq,
                onClick = onOpenAudio,
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    resolved: ResolvedPlaylist?,
    controller: PlayerController,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaylistCover(
            playlist = playlist,
            resolved = resolved,
            controller = controller,
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.displayName(context),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.playlist_track_count, playlist.entries.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer(rotationZ = 180f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistDetailActions(
    playlist: Playlist?,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onClearCover: () -> Unit,
    onDelete: () -> Unit,
) {
    if (playlist == null) return
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!playlist.isFavorites) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_rename)) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_change_cover)) },
                    onClick = { menuOpen = false; onChangeCover() },
                )
            }
            if (playlist.coverImageUri != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_clear_cover)) },
                    onClick = { menuOpen = false; onClearCover() },
                )
            }
            if (!playlist.isFavorites) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun LibraryEntryRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer(rotationZ = 180f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionScreen(
    type: LibraryType,
    state: PlayerState,
    tracks: List<IndexedValue<MediaItem>>,
    viewMode: ViewMode,
    groupBy: GroupBy,
    prefs: LibraryPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    onDrillInto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                if (type == LibraryType.AUDIO) "No audio files." else "No video files.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val availableGroups = if (type == LibraryType.VIDEO) {
        listOf(GroupBy.LOCATION, GroupBy.YEAR)
    } else {
        listOf(GroupBy.LOCATION, GroupBy.ARTIST, GroupBy.ALBUM, GroupBy.YEAR)
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "section_controls") {
            SectionControls(
                title = if (type == LibraryType.AUDIO) "Audio" else "Video",
                viewMode = viewMode,
                groupBy = groupBy,
                availableGroups = availableGroups,
                onToggleView = {
                    scope.launch {
                        prefs.setViewMode(
                            type,
                            if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST,
                        )
                    }
                },
                onGroupBy = { scope.launch { prefs.setGroupBy(type, it) } },
            )
        }
        if (type == LibraryType.VIDEO) {
            videoSectionItems(
                tracks = tracks,
                viewMode = viewMode,
                groupBy = groupBy,
                state = state,
                controller = controller,
                onOpenPlayer = onOpenPlayer,
                onDrillInto = onDrillInto,
            )
        } else {
            audioSectionItems(
                tracks = tracks,
                viewMode = viewMode,
                groupBy = groupBy,
                state = state,
                controller = controller,
                onOpenPlayer = onOpenPlayer,
                onDrillInto = onDrillInto,
            )
        }
    }
}

@Composable
private fun SectionControls(
    title: String,
    viewMode: ViewMode,
    groupBy: GroupBy,
    availableGroups: List<GroupBy>,
    onToggleView: () -> Unit,
    onGroupBy: (GroupBy) -> Unit,
) {
    var groupMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Box {
            IconButton(onClick = { groupMenuOpen = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = "Group by")
            }
            DropdownMenu(expanded = groupMenuOpen, onDismissRequest = { groupMenuOpen = false }) {
                availableGroups.forEach { gb ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Group by ${gb.name.lowercase().replaceFirstChar { it.titlecase() }}" +
                                    if (gb == groupBy) "  ✓" else "",
                            )
                        },
                        onClick = {
                            groupMenuOpen = false
                            onGroupBy(gb)
                        },
                    )
                }
            }
        }
        IconButton(onClick = onToggleView) {
            Icon(
                if (viewMode == ViewMode.LIST) Icons.Filled.GridView
                else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "Toggle view mode",
            )
        }
    }
}

@Composable
private fun EmptySection(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.videoSectionItems(
    tracks: List<IndexedValue<MediaItem>>,
    viewMode: ViewMode,
    groupBy: GroupBy,
    state: PlayerState,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    onDrillInto: (String) -> Unit,
) {
    val grouped = groupTracks(tracks, groupBy)
    grouped.forEach { (header, items) ->
        item(key = "v_h_$header") {
            GroupHeaderRow(header, items.size)
        }
        if (viewMode == ViewMode.LIST) {
            itemsIndexed(items, key = { _, iv -> "v_${iv.index}" }) { _, iv ->
                TrackListRow(
                    track = iv.value,
                    isCurrent = iv.index == state.currentIndex,
                    isPlaying = iv.index == state.currentIndex && state.isPlaying,
                    onClick = { playFromFolder(state, tracks, iv, controller, onOpenPlayer) },
                )
            }
        } else {
            item(key = "v_grid_$header") {
                Column {
                    items.chunked(3).forEach { chunk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            chunk.forEach { iv ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            playFromFolder(state, tracks, iv, controller, onOpenPlayer)
                                        },
                                ) {
                                    TrackGridArt(
                                        track = iv.value,
                                        isCurrent = iv.index == state.currentIndex,
                                    )
                                    Text(
                                        text = iv.value.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                    Text(
                                        text = iv.value.artist ?: "Unknown artist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            repeat(3 - chunk.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioSectionItems(
    tracks: List<IndexedValue<MediaItem>>,
    viewMode: ViewMode,
    groupBy: GroupBy,
    state: PlayerState,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    onDrillInto: (String) -> Unit,
) {
    val grouped = groupTracks(tracks, groupBy)
    val directoryMode = groupBy == GroupBy.ARTIST || groupBy == GroupBy.ALBUM
    if (directoryMode) {
        if (viewMode == ViewMode.GRID) {
            item(key = "audio_dir_grid") {
                DirectoryGrid(
                    groups = grouped,
                    state = state,
                    controller = controller,
                    onClick = { key -> onDrillInto(key) },
                )
            }
        } else {
            items(grouped, key = { "audio_dir_${it.first}" }) { (key, items) ->
                DirectoryListRow(
                    title = key,
                    subtitle = "${items.size} track" + if (items.size != 1) "s" else "",
                    coverItem = items.firstOrNull()?.value,
                    onClick = { onDrillInto(key) },
                    controller = controller,
                )
            }
        }
    } else {
        grouped.forEach { (header, items) ->
            item(key = "a_h_$header") {
                GroupHeaderRow(header, items.size)
            }
            itemsIndexed(items, key = { _, iv -> "a_${iv.index}" }) { _, iv ->
                TrackListRow(
                    track = iv.value,
                    isCurrent = iv.index == state.currentIndex,
                    isPlaying = iv.index == state.currentIndex && state.isPlaying,
                    onClick = { playFromFolder(state, tracks, iv, controller, onOpenPlayer) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeaderRow(header: String, count: Int) {
    Text(
        text = "$header  ($count)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DirectoryGrid(
    groups: List<Pair<String, List<IndexedValue<MediaItem>>>>,
    state: PlayerState,
    controller: PlayerController,
    onClick: (String) -> Unit,
) {
    val minCardWidth = 180.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        val columns = maxOf(1, (maxWidth / minCardWidth).toInt())
        Column(modifier = Modifier.fillMaxWidth()) {
            groups.chunked(columns).forEach { rowGroups ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowGroups.forEach { (key, items) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onClick(key) },
                        ) {
                            DirectoryArt(
                                coverItem = items.firstOrNull()?.value,
                                controller = controller,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                text = "${items.size} track" + if (items.size != 1) "s" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(columns - rowGroups.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
                Spacer8()
            }
        }
    }
}

@Composable
private fun DirectoryListRow(
    title: String,
    subtitle: String,
    coverItem: MediaItem?,
    onClick: () -> Unit,
    controller: PlayerController,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DirectoryArt(coverItem = coverItem, controller = controller)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DirectoryArt(
    coverItem: MediaItem?,
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(coverItem?.sourceUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(coverItem?.sourceUri) {
        bitmap = coverItem?.let { controller.getArt(it) }
    }
    Surface(
        modifier = modifier.size(56.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackGridArt(
    track: MediaItem,
    isCurrent: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().size(140.dp),
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
            dev.chardoncs.ezmpv.ui.components.AnimatedPlayPauseIcon(
                isPlaying = isPlaying,
                contentDescription = "Play ${track.title}",
            )
        }
    }
}

@Composable
private fun GroupDrillDown(
    state: PlayerState,
    tracks: List<IndexedValue<MediaItem>>,
    viewMode: ViewMode,
    groupKey: String?,
    groupBy: GroupBy,
    isAudio: Boolean,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupTracks = remember(tracks, groupBy, groupKey) {
        val grouped = groupTracks(tracks, groupBy)
        grouped.firstOrNull { it.first == groupKey }?.second ?: emptyList()
    }
    if (groupTracks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No items.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (isAudio && (groupBy == GroupBy.ALBUM || groupBy == GroupBy.ARTIST)) {
        AlbumDrillDown(
            groupTracks = groupTracks,
            state = state,
            controller = controller,
            onOpenPlayer = onOpenPlayer,
            modifier = modifier,
        )
    } else {
        TrackListOrGrid(
            groupTracks = groupTracks,
            viewMode = viewMode,
            state = state,
            controller = controller,
            onOpenPlayer = onOpenPlayer,
            modifier = modifier,
        )
    }
}

@Composable
private fun AlbumDrillDown(
    groupTracks: List<IndexedValue<MediaItem>>,
    state: PlayerState,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(groupTracks) {
        groupTracks.sortedWith(
            compareBy(
                { it.value.discNumber ?: Int.MAX_VALUE },
                { it.value.trackNumber ?: Int.MAX_VALUE },
                { it.value.title.lowercase() },
            ),
        )
    }
    val byDisc = remember(sorted) {
        sorted.groupBy { it.value.discNumber }
            .toSortedMap(compareBy { it ?: Int.MAX_VALUE })
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        byDisc.forEach { (disc, items) ->
            item(key = "disc_${disc ?: -1}") {
                Text(
                    text = if (byDisc.size == 1 && disc == null) "Tracks"
                    else "Disc ${disc ?: "?"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(items, key = { _, iv -> "disc_${disc}_${iv.index}" }) { _, iv ->
                TrackListRow(
                    track = iv.value,
                    isCurrent = iv.index == state.currentIndex,
                    isPlaying = iv.index == state.currentIndex && state.isPlaying,
                    onClick = {
                        val queue = sorted.map { it.value }
                        val pos = queue.indexOfFirst { it.sourceUri == iv.value.sourceUri }
                            .coerceAtLeast(0)
                        controller.playFromLibrary(queue, pos)
                        onOpenPlayer()
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackListOrGrid(
    groupTracks: List<IndexedValue<MediaItem>>,
    viewMode: ViewMode,
    state: PlayerState,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewMode == ViewMode.LIST) {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            itemsIndexed(groupTracks, key = { _, iv -> "gd_${iv.index}" }) { _, iv ->
                TrackListRow(
                    track = iv.value,
                    isCurrent = iv.index == state.currentIndex,
                    isPlaying = iv.index == state.currentIndex && state.isPlaying,
                    onClick = {
                        val queue = groupTracks.map { it.value }
                        controller.playFromLibrary(queue, iv.index)
                        onOpenPlayer()
                    },
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(groupTracks, key = { it.index }) { iv ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val queue = groupTracks.map { it.value }
                            controller.playFromLibrary(queue, iv.index)
                            onOpenPlayer()
                        },
                ) {
                    TrackGridArt(track = iv.value, isCurrent = iv.index == state.currentIndex)
                    Text(
                        text = iv.value.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = iv.value.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlistId: String,
    playlists: List<Playlist>,
    resolved: List<ResolvedPlaylist>,
    state: PlayerState,
    controller: PlayerController,
    playlistController: dev.chardoncs.ezmpv.playlists.PlaylistController,
    onOpenPlayer: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rp = resolved.firstOrNull { it.playlist.id == playlistId }
    val playlist = rp?.playlist
    var showPicker by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf<MediaItem?>(null) }

    if (playlist == null) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Playlist not found.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val availableEntries = rp.entries.filter { it.available }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "pl_header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlaylistCover(
                    playlist = playlist,
                    resolved = rp,
                    controller = controller,
                    modifier = Modifier.size(180.dp).aspectRatio(1f),
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        item(key = "pl_add_buttons") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.playlist_add_from_library))
                }
            }
        }
        if (rp.entries.isEmpty()) {
            item(key = "pl_empty") {
                Text(
                    "No tracks in this playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            itemsIndexed(rp.entries, key = { i, re -> re.entry.uri.toString() + "_" + i }) { index, re ->
                ResolvedTrackRow(
                    resolved = re,
                    controller = controller,
                    isCurrent = re.available && re.mediaItem?.sourceUri ==
                        state.playlist.getOrNull(state.currentIndex)?.sourceUri,
                    isPlaying = re.available && re.mediaItem?.sourceUri ==
                        state.playlist.getOrNull(state.currentIndex)?.sourceUri && state.isPlaying,
                    onPlay = {
                        if (!re.available) { onUnavailable(); return@ResolvedTrackRow }
                        val queue = availableEntries.mapNotNull { it.mediaItem }
                        val pos = queue.indexOfFirst { it.sourceUri == re.mediaItem?.sourceUri }
                        if (pos >= 0) {
                            controller.playFromLibrary(queue, pos)
                            onOpenPlayer()
                        }
                    },
                    onRemove = { playlistController.removeEntry(playlistId, re.entry.uri) },
                    onAddToPlaylist = { showAddToPlaylist = re.mediaItem ?: re.entry.toMediaItem() },
                    onFavoriteToggle = {
                        val item = re.mediaItem ?: re.entry.toMediaItem()
                        playlistController.toggleFavorite(item)
                    },
                    isFavorite = playlistController.isFavorite(re.entry.uri),
                )
            }
        }
    }

    if (showPicker) {
        LibraryTrackPickerSheet(
            tracks = state.library,
            onDismiss = { showPicker = false },
            onConfirm = { items -> playlistController.addMediaItems(playlistId, items) },
        )
    }
    showAddToPlaylist?.let { item ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = null },
            onAddTo = { id -> playlistController.addMediaItems(id, listOf(item)) },
        )
    }
}

@Composable
private fun ResolvedTrackRow(
    resolved: ResolvedEntry,
    controller: PlayerController,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavorite: Boolean,
) {
    val entry = resolved.entry
    val available = resolved.available
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (available) Modifier else Modifier.alpha(0.5f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResolvedTrackArt(
            entry = entry,
            available = available,
            controller = controller,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.artist ?: "Unknown artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (available) {
            Text(
                text = formatTime(entry.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (available) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_add_to)) },
                        onClick = { menuOpen = false; onAddToPlaylist() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isFavorite) R.string.playlist_unfavorite else R.string.playlist_favorite)) },
                        onClick = { menuOpen = false; onFavoriteToggle() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_remove)) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun ResolvedTrackArt(
    entry: dev.chardoncs.ezmpv.playlists.PlaylistEntry,
    available: Boolean,
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (!available) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            return@Surface
        }
        if (entry.isVideo) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            return@Surface
        }
        var bitmap by remember(entry.uri) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(entry.uri) {
            bitmap = controller.getArt(entry.toMediaItem())
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun playFromFolder(
    state: PlayerState,
    allTracks: List<IndexedValue<MediaItem>>,
    iv: IndexedValue<MediaItem>,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
) {
    val selected = state.library.getOrNull(iv.index)
    val selectedFolder = selected?.let { parentFolder(it.sourceUri) }
    val queue = allTracks
        .map { it.value }
        .filter { item ->
            selectedFolder == null || parentFolder(item.sourceUri) == selectedFolder
        }
    val posInQueue = queue.indexOfFirst { it.sourceUri == selected?.sourceUri }
        .coerceAtLeast(0)
    controller.playFromLibrary(queue, posInQueue)
    onOpenPlayer()
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

private fun parentFolder(uri: Uri): String {
    val seg = uri.lastPathSegment ?: return "Unknown location"
    val cut = seg.lastIndexOf('/')
    return if (cut > 0) seg.substring(0, cut) else seg
}

@Composable
private fun Spacer8() {
    Box(modifier = Modifier.size(12.dp))
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
