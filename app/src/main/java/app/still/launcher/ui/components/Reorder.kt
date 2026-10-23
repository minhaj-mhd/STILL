package app.still.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/** What a touch turned out to be before the long-press time ran out. */
private enum class EarlyEnd { TAP, MOVED }

/**
 * Tap, hold, or hold and drag, in one gesture so they can't trip over each other. A quick tap
 * calls [onTap]. Holding calls [onLift]; moving then calls [onDrag] with each step, and letting
 * go calls [onDrop] with whether it moved, so a hold released in place can open a menu. A touch
 * that moves before the hold is left alone, for the page swipes underneath.
 */
@Composable
fun Modifier.holdToDrag(
    key: Any,
    onTap: () -> Unit,
    onLift: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: (moved: Boolean) -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val lift by rememberUpdatedState(onLift)
    val dragBy by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    return pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown()
            // Ours from here, so home's own taps and long-press on empty space don't fire too.
            down.consume()
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result: EarlyEnd? = null
                while (result == null) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull EarlyEnd.MOVED
                    when {
                        change.changedToUp() -> result = EarlyEnd.TAP
                        change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> result = EarlyEnd.MOVED
                    }
                }
                result
            }
            when (early) {
                EarlyEnd.TAP -> tap()
                EarlyEnd.MOVED -> Unit
                null -> {
                    lift()
                    var travelled = Offset.Zero
                    drag(down.id) { change ->
                        val step = change.positionChange()
                        travelled += step
                        change.consume()
                        dragBy(step)
                    }
                    drop(travelled.getDistance() > viewConfiguration.touchSlop)
                }
            }
        }
    }
}
