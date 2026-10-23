package app.still.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A deliberate confirmation: drag the knob all the way to the right. Letting go early springs
 * it back. Screen readers get a plain "activate" action instead of the drag.
 */
@Composable
fun SlideToConfirm(text: String, accessibilityLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val knobSize = 56.dp
    val knobPx = with(LocalDensity.current) { knobSize.toPx() }
    var trackWidth by remember { mutableIntStateOf(0) }
    val maxOffset = (trackWidth - knobPx).coerceAtLeast(0f)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(knobSize)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .onSizeChanged { trackWidth = it.width }
            .semantics {
                onClick(label = accessibilityLabel) {
                    onConfirm()
                    true
                }
            },
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knobSize)
                .clip(CircleShape)
                .background(colors.onBackground)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset)) }
                    },
                    onDragStopped = {
                        if (maxOffset > 0f && offset.value >= maxOffset * CONFIRM_FRACTION) {
                            offset.snapTo(maxOffset)
                            onConfirm()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ),
        ) {
            Text("→", style = MaterialTheme.typography.titleLarge, color = colors.background)
        }
    }
}

/** How far the knob must travel to count. */
private const val CONFIRM_FRACTION = 0.9f
