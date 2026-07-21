package dev.chardoncs.ezmpv.player

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "mpv"

@Composable
fun MpvSurface(
    player: Player,
    modifier: Modifier = Modifier,
) {
    var currentHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    LaunchedEffect(player.isCreated, currentHolder) {
        val holder = currentHolder
        if (player.isCreated && holder != null) {
            Log.d(TAG, "attaching surface (player ready + holder present)")
            player.attachSurface(holder.surface)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { surfaceView ->
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceCreated")
                        currentHolder = holder
                        if (player.isCreated) {
                            player.attachSurface(holder.surface)
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        Log.d(TAG, "surfaceChanged ${width}x$height")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceDestroyed")
                        currentHolder = null
                        player.detachSurface(holder.surface)
                    }
                })
            }
        },
        update = { },
    )
}