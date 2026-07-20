package dev.chardoncs.ezmpv.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chardoncs.ezmpv.R
import dev.chardoncs.ezmpv.player.MpvSurface
import dev.chardoncs.ezmpv.player.rememberMpvController

@Composable
fun VideoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Initializing libmpv…") }

    val controller = rememberMpvController(
        context = context,
        onConfigure = { mpv ->
            mpv.setOptionString("config", "yes")
            mpv.setOptionString("force-window", "no")
            mpv.setOptionString("idle", "once")
        },
    )

    LaunchedEffect(controller.isAvailable) {
        if (controller.isAvailable) {
            status = "libmpv loaded ✓ — loading sample…"
            val mpv = controller.mpv!!
            val rawUri = rawResourceUri(context, R.raw.sample)
            mpv.command(arrayOf("loadfile", rawUri.toString()))
            status = "libmpv loaded ✓ — sample loaded"
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        MpvSurface(
            controller = controller,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}

private fun rawResourceUri(context: Context, resId: Int): Uri =
    Uri.Builder()
        .scheme("android.resource")
        .authority(context.packageName)
        .appendPath(resId.toString())
        .build()