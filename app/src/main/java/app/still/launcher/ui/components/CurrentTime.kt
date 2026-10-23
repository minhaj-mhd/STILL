package app.still.launcher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.ZonedDateTime

/**
 * The current time, refreshed by the system's once-a-minute tick and on resume.
 * Coroutine delays pause while the device sleeps, so they can't drive a clock.
 */
@Composable
fun rememberCurrentMinute(): State<ZonedDateTime> {
    val context = LocalContext.current
    val time = remember { mutableStateOf(ZonedDateTime.now()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                time.value = ZonedDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LifecycleResumeEffect(Unit) {
        time.value = ZonedDateTime.now()
        onPauseOrDispose { }
    }

    return time
}
