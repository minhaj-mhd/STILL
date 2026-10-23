package app.still.launcher.ui.limit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.limit.AppLimitStatus
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.components.formatTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Why STILL is in front of an app with limits. */
enum class AppLimitReason { DONE_TODAY, WAITING, VISIT_UP }

/**
 * Shown instead of an app its own limits keep closed (done for today, or waiting between
 * visits), and by Precision mode when a visit runs its length. Nothing opens from here; the
 * only way on is home.
 */
@Composable
fun AppLimitScreen(
    appLabel: String,
    reason: AppLimitReason,
    status: AppLimitStatus?,
    /** How long the visit that just ended was allowed, for [AppLimitReason.VISIT_UP]. */
    visitMinutes: Int,
    /** When the next day starts, for [AppLimitReason.DONE_TODAY]. */
    nextDay: LocalDateTime,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val limit = status?.limit
    val (title, body) = when (reason) {
        AppLimitReason.DONE_TODAY -> {
            val back = if (nextDay.hour == 0 && nextDay.minute == 0) stringResource(R.string.limit_midnight) else formatTime(nextDay.toLocalTime())
            stringResource(R.string.app_limit_done_title, appLabel) to
                stringResource(R.string.app_limit_done_body, formatDuration(status?.usedToday ?: Duration.ZERO), minutes(limit?.dailyMinutes ?: 0), back)
        }
        AppLimitReason.WAITING -> {
            val opensAt = status?.opensAtMillis ?: System.currentTimeMillis()
            val left = Duration.ofMillis(opensAt - System.currentTimeMillis()).plusSeconds(59)
            stringResource(R.string.app_limit_waiting_title, appLabel, formatTime(timeOf(opensAt))) to
                stringResource(R.string.app_limit_waiting_body, formatDuration(left), minutes(limit?.waitMinutes ?: 0))
        }
        AppLimitReason.VISIT_UP -> {
            val opensAt = limit?.waitMinutes?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 60_000L }
            pluralStringResource(R.plurals.visit_up_title, visitMinutes, visitMinutes) to
                if (opensAt != null) stringResource(R.string.visit_up_body_wait, appLabel, formatTime(timeOf(opensAt)))
                else stringResource(R.string.visit_up_body, appLabel)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) { Text(stringResource(if (reason == AppLimitReason.VISIT_UP) R.string.visit_up_leave else R.string.limit_go_home)) }
        }
    }
}

@Composable
private fun minutes(minutes: Int): String = formatDuration(Duration.ofMinutes(minutes.toLong()))

private fun timeOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
