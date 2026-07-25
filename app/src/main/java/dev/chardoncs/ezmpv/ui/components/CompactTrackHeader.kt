package dev.chardoncs.ezmpv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chardoncs.ezmpv.player.MpvSurface
import dev.chardoncs.ezmpv.player.PlayerState
import dev.chardoncs.ezmpv.player.VideoSurfaceHost
import dev.chardoncs.ezmpv.player.VideoTarget

@Composable
fun CompactTrackHeader(
    state: PlayerState,
    videoHost: VideoSurfaceHost,
    modifier: Modifier = Modifier,
    artSize: Int = 44,
    horizontalPadding: Int = 12,
    verticalPadding: Int = 6,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    artistStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val track = state.playlist.getOrNull(state.currentIndex)
    Row(
        modifier = modifier
            .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hasVideo && !state.audioOnly) {
            MpvSurface(
                host = videoHost,
                target = VideoTarget.HEADER,
                modifier = Modifier.size(artSize.dp),
            )
        } else {
            val bitmap = state.currentArt
            if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album art",
                modifier = Modifier.size(artSize.dp),
                contentScale = ContentScale.Crop,
            )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(artSize.dp).padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = track?.title ?: "Not playing",
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artist ?: "Unknown artist",
                style = artistStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
