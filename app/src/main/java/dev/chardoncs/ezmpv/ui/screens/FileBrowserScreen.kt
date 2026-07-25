package dev.chardoncs.ezmpv.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.R
import dev.chardoncs.ezmpv.browse.DirEntry
import dev.chardoncs.ezmpv.browse.StorageAccess
import dev.chardoncs.ezmpv.player.MediaItem
import dev.chardoncs.ezmpv.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    rootTreeUri: Uri,
    rootTitle: String,
    onOpenPlayer: () -> Unit,
    onExit: () -> Unit,
    playerOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = remember {
        (context.applicationContext as EzmpvApplication).playerController
    }
    val browseController = remember {
        (context.applicationContext as EzmpvApplication).browseController
    }
    val scope = rememberCoroutineScope()

    val stack = rememberSaveable(rootTreeUri, saver = UriListSaver) { mutableStateListOf(rootTreeUri) }
    val titles = rememberSaveable(rootTreeUri, rootTitle, saver = StringListSaver) { mutableStateListOf(rootTitle) }

    val currentUri = stack.last()
    val currentTitle = titles.last()

    var entries by remember { mutableStateOf<List<DirEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionTarget by remember { mutableStateOf<DirEntry?>(null) }
    var sheetEntry by remember { mutableStateOf<DirEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<DirEntry>?>(null) }
    val selection = remember { mutableStateSetOf<Uri>() }

    LaunchedEffect(currentUri) {
        loading = true
        selection.clear()
        entries = withContext(Dispatchers.IO) { StorageAccess.listDirectory(context, currentUri) }
        loading = false
    }

    val inSelection = selection.isNotEmpty()
    val atRoot = stack.size == 1
    BackHandler(enabled = !playerOpen && (!atRoot || inSelection)) {
        if (inSelection) {
            selection.clear()
        } else {
            stack.removeAt(stack.lastIndex)
            titles.removeAt(titles.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (inSelection) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = { selection.clear() },
                    onSelectAll = {
                        entries?.let { list ->
                            selection.clear()
                            list.forEach { selection.add(it.uri) }
                        }
                    },
                    onPlayAll = {
                        val selected = selectedEntries(entries, selection)
                        val items = selected.filter { !it.isDirectory }.map(::entryToMediaItem)
                        if (items.isNotEmpty()) {
                            controller.playFromLibrary(items, 0)
                            selection.clear()
                            onOpenPlayer()
                        }
                    },
                    onAppend = {
                        val selected = selectedEntries(entries, selection)
                        val items = selected.filter { !it.isDirectory }.map(::entryToMediaItem)
                        if (items.isNotEmpty()) {
                            controller.appendToQueue(items)
                            selection.clear()
                        }
                    },
                    onPlayNext = {
                        val selected = selectedEntries(entries, selection)
                        val items = selected.filter { !it.isDirectory }.map(::entryToMediaItem)
                        if (items.isNotEmpty()) {
                            controller.playNext(items)
                            selection.clear()
                        }
                    },
                    onAddBookmarks = {
                        val selected = selectedEntries(entries, selection).filter { it.isDirectory }
                        selected.forEach { browseController.toggleBookmark(it.uri, it.name) }
                        selection.clear()
                    },
                    onDelete = {
                        deleteTargets = selectedEntries(entries, selection)
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (atRoot) onExit() else {
                                stack.removeAt(stack.lastIndex)
                                titles.removeAt(titles.lastIndex)
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                entries.isNullOrEmpty() -> {
                    Text(
                        text = stringResource(R.string.browse_empty_folder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries!!, key = { it.uri }) { entry ->
                            val selected = entry.uri in selection
                            DirEntryRow(
                                entry = entry,
                                selected = selected,
                                inSelectionMode = inSelection,
                                onClick = {
                                    if (inSelection) {
                                        toggleSelection(selection, entry.uri)
                                    } else if (entry.isDirectory) {
                                        stack.add(entry.uri)
                                        titles.add(entry.name)
                                    } else {
                                        playFile(controller, entry.uri, entry.mimeType)
                                        onOpenPlayer()
                                    }
                                },
                                onLongClick = {
                                    if (!inSelection) {
                                        selection.clear()
                                        selection.add(entry.uri)
                                    } else {
                                        toggleSelection(selection, entry.uri)
                                    }
                                },
                                onMore = { actionTarget = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        EntryActionSheet(
            entry = target,
            isBookmarked = browseController.isBookmarked(target.uri),
            onDismiss = { actionTarget = null },
            onPlayFile = {
                actionTarget = null
                playFile(controller, target.uri, target.mimeType)
                onOpenPlayer()
            },
            onPlayFolder = { recursive ->
                actionTarget = null
                controller.playDirectory(target.uri, recursive) { onOpenPlayer() }
            },
            onAppend = {
                actionTarget = null
                controller.appendToQueue(listOf(entryToMediaItem(target)))
            },
            onPlayNext = {
                actionTarget = null
                controller.playNext(listOf(entryToMediaItem(target)))
            },
            onToggleBookmark = {
                actionTarget = null
                browseController.toggleBookmark(target.uri, target.name)
            },
            onShowInfo = {
                actionTarget = null
                sheetEntry = target
            },
        )
    }

    sheetEntry?.let { entry ->
        InfoSheet(entry = entry, onDismiss = { sheetEntry = null })
    }

    deleteTargets?.let { targets ->
        DeleteConfirmDialog(
            count = targets.size,
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        targets.forEach { entry ->
                            runCatching {
                                DocumentFile.fromSingleUri(context, entry.uri)?.delete()
                            }
                        }
                    }
                    deleteTargets = null
                    selection.clear()
                    loading = true
                    entries = withContext(Dispatchers.IO) { StorageAccess.listDirectory(context, currentUri) }
                    loading = false
                }
            },
            onDismiss = { deleteTargets = null },
        )
    }
}

private fun toggleSelection(set: MutableSet<Uri>, uri: Uri) {
    if (uri in set) set.remove(uri) else set.add(uri)
}

private fun selectedEntries(entries: List<DirEntry>?, selection: Set<Uri>): List<DirEntry> =
    entries?.filter { it.uri in selection } ?: emptyList()

private fun playFile(controller: PlayerController, uri: Uri, mimeType: String?) {
    val isVideo = mimeType?.startsWith("video/") == true
    val title = uri.lastPathSegment?.substringAfterLast('/') ?: "File"
    val item = MediaItem(
        sourceUri = uri,
        title = title.substringBeforeLast('.'),
        mimeType = mimeType,
        isVideo = isVideo,
    )
    controller.playFromLibrary(listOf(item), 0)
}

private fun entryToMediaItem(entry: DirEntry): MediaItem = MediaItem(
    sourceUri = entry.uri,
    title = entry.name.substringBeforeLast('.'),
    mimeType = entry.mimeType,
    isVideo = entry.mimeType?.startsWith("video/") == true,
    sizeBytes = entry.sizeBytes,
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DirEntryRow(
    entry: DirEntry,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    entry.isDirectory -> Icons.Filled.Folder
                    entry.mimeType?.startsWith("video/") == true -> Icons.Filled.VideoFile
                    entry.mimeType?.startsWith("audio/") == true -> Icons.Filled.AudioFile
                    else -> Icons.Filled.Folder
                },
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDirectory && entry.sizeBytes > 0) {
                Text(
                    text = formatBytes(entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!inSelectionMode) {
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPlayAll: () -> Unit,
    onAppend: () -> Unit,
    onPlayNext: () -> Unit,
    onAddBookmarks: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.browse_selection_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.browse_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.browse_select_all))
            }
            IconButton(onClick = onPlayAll) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.browse_play_all))
            }
            IconButton(onClick = onAppend) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.browse_append))
            }
            IconButton(onClick = onPlayNext) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.browse_play_next))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.browse_delete))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryActionSheet(
    entry: DirEntry,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onPlayFile: () -> Unit,
    onPlayFolder: (recursive: Boolean) -> Unit,
    onAppend: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowInfo: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (entry.isDirectory) {
                DropdownMenuItem(text = { Text(stringResource(R.string.browse_play_folder)) }, onClick = { onPlayFolder(false) })
                DropdownMenuItem(text = { Text(stringResource(R.string.browse_play_folder_subfolders)) }, onClick = { onPlayFolder(true) })
                DropdownMenuItem(
                    text = { Text(stringResource(if (isBookmarked) R.string.browse_remove_bookmark else R.string.browse_add_bookmark)) },
                    onClick = onToggleBookmark,
                )
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.browse_play)) }, onClick = onPlayFile)
                DropdownMenuItem(text = { Text(stringResource(R.string.browse_append)) }, onClick = onAppend)
                DropdownMenuItem(text = { Text(stringResource(R.string.browse_play_next)) }, onClick = onPlayNext)
                DropdownMenuItem(text = { Text(stringResource(R.string.browse_info)) }, onClick = onShowInfo)
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browse_delete)) },
        text = { Text(stringResource(R.string.browse_delete_confirm, count)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.browse_delete))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(entry: DirEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(entry.uri) {
        info = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, entry.uri)
                    buildMap {
                        put("Title", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: entry.name)
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { put("Artist", it) }
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { put("Album", it) }
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { put("Duration", formatTime(it)) }
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { put("Year", it) }
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            if (info == null) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            } else {
                info!!.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private val UriListSaver = androidx.compose.runtime.saveable.Saver<MutableList<Uri>, List<String>>(
    save = { it.map(Uri::toString) },
    restore = { it.map(Uri::parse).toMutableList() },
)

private val StringListSaver = androidx.compose.runtime.saveable.Saver<MutableList<String>, List<String>>(
    save = { it.toList() },
    restore = { it.toMutableList() },
)
