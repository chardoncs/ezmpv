package dev.chardoncs.ezmpv.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.R
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.playlists.Playlist
import dev.chardoncs.ezmpv.playlists.ResolvedPlaylist
import dev.chardoncs.ezmpv.playlists.toMediaItem

@Composable
fun PlaylistCover(
    playlist: Playlist,
    resolved: ResolvedPlaylist?,
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (playlist.isFavorites) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.playlist_favorites_cover),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        return
    }
    playlist.coverImageUri?.let { coverUri ->
        CoverImage(uri = coverUri, modifier = modifier)
        return
    }
    val recent = resolved?.entries
        ?.filter { it.available }
        ?.sortedByDescending { it.entry.addedAt }
        ?.take(4)
        ?: emptyList()
    if (recent.isEmpty()) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        return
    }
    val arts = remember(recent.map { it.entry.uri }) { recent.map { it.entry } }
    ArtGrid(entries = arts, controller = controller, modifier = modifier)
}

@Composable
private fun CoverImage(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            }.getOrNull()
        }
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_playlist_cover),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtGrid(
    entries: List<dev.chardoncs.ezmpv.playlists.PlaylistEntry>,
    controller: PlayerController,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (entries.size == 1) {
            val e = entries[0]
            var bmp by remember(e.uri) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(e.uri) { bmp = controller.getArt(e.toMediaItem()) }
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                entries.chunked(2).take(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { e ->
                            var bmp by remember(e.uri) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(e.uri) { bmp = controller.getArt(e.toMediaItem()) }
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp!!.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        repeat(2 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
                repeat(2 - entries.chunked(2).take(2).size) {
                    Box(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}