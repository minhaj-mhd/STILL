package app.still.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.still.launcher.apps.ListSection
import app.still.launcher.ui.theme.stillPalette
import kotlin.math.roundToInt

/**
 * The letters down the edge of the app list. Touching one jumps to its apps, and sliding along
 * them runs through the list, with the letter shown large beside the finger.
 */
@Composable
fun FastScroller(sections: List<ListSection>, onSection: (ListSection) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(sections)
    val jumpTo by rememberUpdatedState(onSection)
    var height by remember { mutableIntStateOf(0) }
    var touchY by remember { mutableFloatStateOf(0f) }
    // The letter under the finger; null when the scroller isn't being touched.
    var active by remember { mutableStateOf<Int?>(null) }

    fun touch(y: Float) {
        if (height == 0 || current.isEmpty()) return
        touchY = y.coerceIn(0f, height.toFloat())
        val index = (touchY / height * current.size).toInt().coerceIn(0, current.lastIndex)
        if (index == active) return
        active = index
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        jumpTo(current[index])
    }

    // As tall as the letters need, up to the space there is.
    Box(modifier.heightIn(max = LETTER_HEIGHT * sections.size).fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(SCROLLER_WIDTH)
                .fillMaxHeight()
                .onSizeChanged { height = it.height }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // Ours, so the pages don't swipe while running down the letters.
                        down.consume()
                        touch(down.position.y)
                        drag(down.id) { change ->
                            change.consume()
                            touch(change.position.y)
                        }
                        active = null
                    }
                },
        ) {
            sections.forEachIndexed { index, section ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = section.letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == active) stillPalette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        active?.let { index ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(-(BUBBLE_SIZE + BUBBLE_GAP).roundToPx(), (touchY - BUBBLE_SIZE.toPx() / 2).roundToInt())
                    }
                    .size(BUBBLE_SIZE)
                    .background(stillPalette.accent, CircleShape),
            ) {
                Text(
                    text = current.getOrNull(index)?.letter.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
}

private val LETTER_HEIGHT = 22.dp
private val SCROLLER_WIDTH = 24.dp
private val BUBBLE_SIZE = 56.dp
private val BUBBLE_GAP = 12.dp
