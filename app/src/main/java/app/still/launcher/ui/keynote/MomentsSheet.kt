package app.still.launcher.ui.keynote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.practice.Keynote
import app.still.launcher.keynote.Moment
import app.still.launcher.keynote.MomentKind
import app.still.launcher.keynote.UnlockObservation
import app.still.launcher.keynote.UnlockTrend
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** What home shows for today's quality: its name and how many marks it has. */
data class HomeKeynote(val quality: String, val marks: Int)

/**
 * Tapping today's quality on home: the action chosen for it, each mark with its time, and a few
 * plain observations about the phone today. No score.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsSheet(
    keynote: Keynote,
    linkedTitle: String?,
    moments: List<Moment>,
    unlocks: UnlockObservation?,
    phoneToday: Duration?,
    /** Apps with a daily budget, to say how much of it is used. */
    budgetApps: List<LauncherApp>,
    labelOf: (packageName: String) -> String,
    onChange: () -> Unit,
    onQuietMinute: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(keynote.quality, style = MaterialTheme.typography.headlineSmall, color = stillPalette.accent)
                Text(keynote.action, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                linkedTitle?.let { Note(stringResource(R.string.moments_practising_with, it)) }
            }
            if (moments.isEmpty()) Note(stringResource(R.string.moments_empty))
            moments.forEach { moment -> MomentRow(moment, labelOf) }
            HorizontalDivider(color = stillPalette.faint)
            phoneToday?.let { Note(stringResource(R.string.moments_phone_today, formatDuration(it))) }
            unlocks?.let { Note(unlockText(it)) }
            budgetApps.forEach { app ->
                val status = app.appLimit ?: return@forEach
                Note(stringResource(R.string.moments_app_use, app.label, formatDuration(status.usedToday), formatDuration(Duration.ofMinutes(status.limit.dailyMinutes.toLong()))))
            }
            Row {
                TextButton(onClick = onChange) { Text(stringResource(R.string.moments_change)) }
                TextButton(onClick = onQuietMinute) { Text(stringResource(R.string.quiet_open)) }
            }
        }
    }
}

@Composable
private fun MomentRow(moment: Moment, labelOf: (String) -> String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 2.dp, height = 12.dp).background(stillPalette.accent, RoundedCornerShape(1.dp)))
        Text(
            text = moment.atMillis?.let { formatTime(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()) } ?: stringResource(R.string.moment_evening),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = when (moment.kind) {
                MomentKind.TURNED_BACK -> stringResource(R.string.moment_turned_back, labelOf(moment.subject))
                MomentKind.PRACTISED -> stringResource(R.string.moment_practised, moment.subject)
                MomentKind.FOCUSED -> pluralStringResource(R.plurals.moment_focused, moment.minutes, moment.minutes, moment.subject)
                MomentKind.WITHIN_LIMIT -> stringResource(R.string.moment_within_limit, labelOf(moment.subject), formatDuration(Duration.ofMinutes(moment.minutes.toLong())))
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** "18 unlocks · fewer than usual by now", or just "18 unlocks today" without enough history. */
@Composable
fun unlockText(unlocks: UnlockObservation): String {
    val count = unlocks.count
    return when (unlocks.trend) {
        null -> pluralStringResource(R.plurals.unlocks_today, count, count)
        UnlockTrend.FEWER -> pluralStringResource(R.plurals.unlocks_fewer, count, count)
        UnlockTrend.ABOUT -> pluralStringResource(R.plurals.unlocks_about, count, count)
        UnlockTrend.MORE -> pluralStringResource(R.plurals.unlocks_more, count, count)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
