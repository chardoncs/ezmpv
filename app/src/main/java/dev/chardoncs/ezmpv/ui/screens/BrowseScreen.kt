package dev.chardoncs.ezmpv.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.R
import dev.chardoncs.ezmpv.browse.BrowseBookmark
import dev.chardoncs.ezmpv.browse.IconType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenBrowser: (treeUri: Uri, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = remember {
        (context.applicationContext as EzmpvApplication).browseController
    }
    val bookmarks by controller.bookmarks.collectAsStateWithLifecycle()
    var removeTarget by remember { mutableStateOf<BrowseBookmark?>(null) }

    val addBookmarkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val title = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Folder"
            controller.addBookmark(uri, title)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_browse)) },
                actions = {
                    IconButton(onClick = { addBookmarkLauncher.launch(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.browse_add_bookmark))
                    }
                },
            )
        },
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.browse_no_bookmarks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(bookmarks, key = { it.uri }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onClick = { onOpenBrowser(bookmark.uri, bookmark.title) },
                        onRemove = { removeTarget = bookmark },
                    )
                }
            }
        }
    }

    removeTarget?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.browse_remove_bookmark)) },
            text = { Text(bookmark.title) },
            confirmButton = {
                TextButton(onClick = {
                    controller.removeBookmark(bookmark.uri)
                    removeTarget = null
                }) { Text(stringResource(R.string.browse_remove_bookmark)) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BrowseBookmark,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconFor(bookmark.iconType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun iconFor(type: IconType) = when (type) {
    IconType.DOWNLOAD -> Icons.Filled.Download
    IconType.MOVIES -> Icons.Filled.Movie
    IconType.MUSIC -> Icons.Filled.MusicNote
    IconType.PODCASTS -> Icons.Filled.Podcasts
    IconType.FOLDER -> Icons.Filled.Folder
    IconType.SDCARD -> Icons.Filled.Folder
    IconType.USB -> Icons.Filled.Folder
}