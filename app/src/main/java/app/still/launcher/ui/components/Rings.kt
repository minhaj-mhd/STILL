package app.still.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.still.launcher.productivity.ItemProgress
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TICKS = 60

/**
 * A watch-like ring of 60 ticks filling its size, lit clockwise from the top for [fraction] of
 * the way. Every fifth tick is longer, like a watch's hour marks.
 */
@Composable
fun TickRing(fraction: Float, lit: Color, unlit: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val litCount = (TICKS * fraction.coerceIn(0f, 1f)).roundToInt()
        for (i in 0 until TICKS) {
            val major = i % 5 == 0
            val length = radius * if (major) 0.11f else 0.065f
            val angle = (i * 6 - 90) * PI / 180
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                color = if (i < litCount) lit else unlit,
                start = center + direction * (radius - length),
                end = center + direction * (radius - 1.dp.toPx()),
                strokeWidth = (if (major) 2.4.dp else 1.7.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Past this many items the segments are too short to tell apart, so the ring shows one arc for what's done. */
private const val MAX_SEGMENTS = 24

/**
 * One arc per item on today's list, coloured by its [ItemProgress], filling its size. With more
 * items than fit, it becomes one continuous arc for the share that's done.
 */
@Composable
fun TaskRing(
    segments: List<ItemProgress>,
    done: Color,
    overdue: Color,
    pending: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.5.dp,
) {
    Canvas(modifier) {
        if (segments.isEmpty()) return@Canvas
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset(center.x - arcSize.width / 2, center.y - arcSize.height / 2)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        if (segments.size > MAX_SEGMENTS) {
            drawArc(pending, -90f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            val share = segments.count { it == ItemProgress.DONE }.toFloat() / segments.size
            drawArc(done, -90f, 360f * share, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            return@Canvas
        }
        // Round caps reach past each arc's ends, so the gap allows for them to keep segments apart.
        val radius = arcSize.width / 2
        val capDegrees = (inset / radius * 180 / PI).toFloat()
        val gap = 6f + 2 * capDegrees
        val step = 360f / segments.size
        segments.forEachIndexed { i, state ->
            val color = when (state) {
                ItemProgress.DONE -> done
                ItemProgress.OVERDUE -> overdue
                ItemProgress.PENDING -> pending
            }
            val sweep = (step - gap).coerceAtLeast(0.5f)
            drawArc(color, -90f + i * step + gap / 2, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        }
    }
}
