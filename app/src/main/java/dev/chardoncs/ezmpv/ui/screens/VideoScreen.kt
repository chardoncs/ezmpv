package dev.chardoncs.ezmpv.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chardoncs.ezmpv.player.PlayerController

@Composable
fun VideoScreen(
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryScreen(
        type = LibraryType.VIDEO,
        controller = controller,
        onOpenPlayer = onOpenPlayer,
        modifier = modifier,
        showPickFile = true,
    )
}