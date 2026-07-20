package dev.chardoncs.ezmpv.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.jdtech.mpv.MPVLib

private const val TAG = "mpv"

/**
 * Minimal lifecycle wrapper around the instance-based [MPVLib] from libmpv-android.
 *
 * Callers obtain a controller via [rememberMpvController] and pass it to [MpvSurface].
 * The controller is created lazily, configured via [onConfigure] (options set before
 * [MPVLib.init]), and destroyed automatically when the calling composable leaves
 * the composition.
 */
class MpvController internal constructor(
    val mpv: MPVLib?,
) {
    val isAvailable: Boolean get() = mpv != null
}

/**
 * Creates and owns an [MpvController] bound to the lifetime of the calling composable.
 *
 * [onConfigure] is invoked after [MPVLib.create] but before [MPVLib.init], so it can
 * set mpv options that must be configured before initialization (e.g. `config-dir`,
 * `gpu-shader-cache-dir`, `vo`, `hwdec`). Defaults match the behavior of mpv-android's
 * `BaseMPVView.initialize()`.
 */
@Composable
fun rememberMpvController(
    context: Context,
    onConfigure: (MPVLib) -> Unit = { mpv ->
        mpv.setOptionString("config", "yes")
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("idle", "once")
    },
): MpvController {
    val appCtx = context.applicationContext
    var controller by remember { mutableStateOf(MpvController(null)) }

    DisposableEffect(appCtx) {
        val mpv = MPVLib.create(appCtx)
        if (mpv == null) {
            Log.e(TAG, "MPVLib.create returned null")
        } else {
            try {
                onConfigure(mpv)
                mpv.init()
                controller = MpvController(mpv)
            } catch (t: Throwable) {
                Log.e(TAG, "mpv init failed", t)
                runCatching { mpv.destroy() }
            }
        }

        onDispose {
            controller.mpv?.let { mpv -> runCatching { mpv.destroy() } }
            controller = MpvController(null)
        }
    }

    return controller
}

/**
 * Renders mpv video output into a [SurfaceView] via [MPVLib.attachSurface].
 *
 * Pass an [MpvController] obtained from [rememberMpvController]. Until the controller
 * becomes available the surface is created but left idle.
 */
@Composable
fun MpvSurface(
    controller: MpvController,
    modifier: Modifier = Modifier,
) {
    val mpv = controller.mpv
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { surfaceView ->
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceCreated")
                        mpv?.attachSurface(holder.surface)
                        mpv?.setOptionString("force-window", "yes")
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        Log.d(TAG, "surfaceChanged ${width}x$height")
                        mpv?.setPropertyString("android-surface-size", "${width}x$height")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceDestroyed")
                        mpv?.setPropertyString("vo", "null")
                        mpv?.setPropertyString("force-window", "no")
                        mpv?.detachSurface()
                    }
                })
            }
        },
        update = { /* SurfaceView instance is stable; no per-recompose work */ },
    )
}