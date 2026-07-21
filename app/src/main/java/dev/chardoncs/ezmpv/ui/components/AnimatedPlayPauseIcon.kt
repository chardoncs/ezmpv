package dev.chardoncs.ezmpv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val VIEWPORT = 48f
private const val CENTER = 24f
private const val RING_RADIUS = 18.75f
private const val RING_WIDTH = 2.5f

private const val PLAY_PATH =
    "M 32 24 L 20 24.074 L 20 15 L 32 24 L 32 24 M 20 33 L 20 24.074 L 32 24 L 31.783 24.162 L 20 33 M 32 24 L 32 24 L 32 24"
private const val PAUSE_PATH =
    "M 30.667 22.333 L 17.333 22.333 L 17.333 19 L 30.667 19 L 30.667 22.333 M 17.333 29 L 17.333 25.667 L 30.667 25.667 L 30.667 29 L 17.333 29 M 32 24 L 32 24 L 32 24"

private data class PathPoint(val cmd: Char, val x: Float, val y: Float)

private fun parsePath(data: String): List<PathPoint> {
    val tokens = data.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val out = mutableListOf<PathPoint>()
    var i = 0
    var cmd = 'M'
    while (i < tokens.size) {
        val t = tokens[i]
        if (t.first().isLetter()) {
            cmd = t[0]
            i++
            continue
        }
        val x = tokens[i].toFloat(); val y = tokens[i + 1].toFloat()
        out += PathPoint(cmd, x, y)
        if (cmd == 'M') cmd = 'L'
        i += 2
    }
    return out
}

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
    showRing: Boolean = true,
    glyphScaleFactor: Float = 1f,
) {
    val progress = remember { Animatable(if (isPlaying) 1f else 0f) }
    LaunchedEffect(isPlaying) {
        progress.animateTo(
            if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        )
    }
    val playPts = remember { parsePath(PLAY_PATH) }
    val pausePts = remember { parsePath(PAUSE_PATH) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val scale = size.minDimension / VIEWPORT
            if (showRing) {
                drawCircle(
                    color = tint,
                    radius = RING_RADIUS * scale,
                    center = Offset(CENTER * scale, CENTER * scale),
                    style = Stroke(width = RING_WIDTH * scale),
                )
            }
            val p = progress.value
            val g = glyphScaleFactor
            val path = Path()
            for (idx in playPts.indices) {
                val a = playPts[idx]
                val b = pausePts[idx]
                val x = (CENTER + (a.x + (b.x - a.x) * p - CENTER) * g) * scale
                val y = (CENTER + (a.y + (b.y - a.y) * p - CENTER) * g) * scale
                if (a.cmd == 'M') path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            rotate(degrees = p * 90f, pivot = Offset(CENTER * scale, CENTER * scale)) {
                drawPath(path, color = tint)
            }
        }
    }
}