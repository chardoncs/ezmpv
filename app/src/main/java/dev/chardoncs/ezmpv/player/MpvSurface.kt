package dev.chardoncs.ezmpv.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

enum class VideoTarget {
    MINI,
    FULL,
    HEADER,
    PIP,
}

@Stable
class VideoSurfaceHost(val player: Player) {
    private val targets = mutableStateMapOf<VideoTarget, IntRect>()

    fun updateTarget(target: VideoTarget, bounds: Rect) {
        val rect = IntRect(
            left = bounds.left.roundToInt(),
            top = bounds.top.roundToInt(),
            right = bounds.right.roundToInt(),
            bottom = bounds.bottom.roundToInt(),
        )
        if (rect.width > 0 && rect.height > 0 && targets[target] != rect) targets[target] = rect
    }

    fun clearTarget(target: VideoTarget) {
        targets.remove(target)
    }

    fun boundsFor(target: VideoTarget): IntRect? = targets[target]
}

@Composable
fun rememberVideoSurfaceHost(player: Player): VideoSurfaceHost = remember(player) { VideoSurfaceHost(player) }

@Composable
fun MpvSurface(
    host: VideoSurfaceHost,
    target: VideoTarget,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(host, target) {
        onDispose { host.clearTarget(target) }
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier.onGloballyPositioned { host.updateTarget(target, it.boundsInRoot()) },
    )
}

@Composable
fun PersistentMpvSurface(
    host: VideoSurfaceHost,
    target: VideoTarget?,
    modifier: Modifier = Modifier,
) {
    val targetBounds = target?.let(host::boundsFor)
    var previousBounds by remember { mutableStateOf<IntRect?>(null) }
    SideEffect {
        if (targetBounds != null) previousBounds = targetBounds
    }
    AndroidView(
        modifier = modifier,
        factory = { context -> VideoSurfaceContainer(context, host.player) },
        update = { container -> container.update(if (target == null) null else targetBounds ?: previousBounds) },
    )
}

@SuppressLint("ViewConstructor")
private class VideoSurfaceContainer(context: Context, private val player: Player) : FrameLayout(context) {
    private val textureView = TextureView(context).apply {
        isClickable = false
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        setSurfaceTexture(player.acquireVideoTexture())
    }
    private var displayedBounds: IntRect? = null

    init {
        isClickable = false
        addView(textureView, LayoutParams(1, 1))
    }

    fun update(bounds: IntRect?) {
        if (bounds == displayedBounds) return
        displayedBounds = bounds
        if (bounds == null) {
            textureView.alpha = 0f
            return
        }
        textureView.alpha = 1f
        textureView.layoutParams = LayoutParams(bounds.width, bounds.height).apply {
            leftMargin = bounds.left
            topMargin = bounds.top
        }
        player.resizeVideoSurface(bounds.width, bounds.height)
    }
}
