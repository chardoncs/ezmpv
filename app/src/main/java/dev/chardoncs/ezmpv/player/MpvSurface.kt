package dev.chardoncs.ezmpv.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
 * [isCreated] becomes true once mpv has been created AND initialized (the event
 * thread is running and mpv logs will flow). Callers can then issue commands.
 */
class MpvController internal constructor(
    val mpv: MPVLib?,
) {
    internal var created by mutableStateOf(false)
    val isCreated: Boolean get() = mpv != null && created
}

/**
 * Creates and owns an [MpvController] bound to the lifetime of the calling composable.
 *
 * Mirrors mpv-android's `BaseMPVView.initialize()`: calls [MPVLib.create], applies
 * options (via [onConfigure]), then calls [MPVLib.init].
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
                controller.created = true
                Log.i(TAG, "mpv created and initialized")
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
 * Renders mpv video output into a [SurfaceView].
 *
 * Handles the race between [SurfaceHolder.Callback.surfaceCreated] (which may fire
 * before [rememberMpvController] has populated [MpvController.mpv]) and the
 * `DisposableEffect` that creates mpv. The current [SurfaceHolder] is tracked in
 * a [androidx.compose.runtime.MutableState]; whenever `controller.mpv` becomes non-null while a holder is
 * already present, the surface is attached proactively.
 */
@Composable
fun MpvSurface(
    controller: MpvController,
    modifier: Modifier = Modifier,
) {
    // Tracks the currently-active SurfaceHolder (set by surfaceCreated, cleared by
    // surfaceDestroyed). Used to attach the surface once mpv is ready, and to re-attach
    // if the surface is recreated.
    var currentHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // When mpv becomes available and a surface is already created, attach it now.
    // This covers the race where surfaceCreated fired before controller.mpv was set.
    LaunchedEffect(controller.mpv, currentHolder) {
        val mpv = controller.mpv
        val holder = currentHolder
        if (mpv != null && holder != null) {
            Log.d(TAG, "attaching surface (mpv ready + holder present)")
            mpv.attachSurface(holder.surface)
            mpv.setOptionString("force-window", "yes")
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
                        // If mpv is already initialized, attach immediately. Otherwise
                        // the LaunchedEffect above will attach once mpv becomes ready.
                        controller.mpv?.let { mpv ->
                            mpv.attachSurface(holder.surface)
                            mpv.setOptionString("force-window", "yes")
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        Log.d(TAG, "surfaceChanged ${width}x$height")
                        controller.mpv?.setPropertyString("android-surface-size", "${width}x$height")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceDestroyed")
                        currentHolder = null
                        val mpv = controller.mpv ?: return
                        mpv.setPropertyString("vo", "null")
                        mpv.setPropertyString("force-window", "no")
                        mpv.detachSurface()
                    }
                })
            }
        },
        update = { /* SurfaceView instance is stable */ },
    )
}