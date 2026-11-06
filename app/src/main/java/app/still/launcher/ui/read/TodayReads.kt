package app.still.launcher.ui.read

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.still.launcher.R
import app.still.launcher.data.read.BookStatus
import app.still.launcher.data.read.ReadSeries
import app.still.launcher.drive.DriveProblem
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.theme.stillPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/** "Today's reads", with the way to every series. */
@Composable
internal fun TodayReadsTitle(onAllSeries: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.daily_today_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(top = 28.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.daily_all_series) + "  ›",
            style = MaterialTheme.typography.labelLarge,
            color = stillPalette.accent,
            modifier = Modifier
                .clickable(onClick = onAllSeries)
                .padding(top = 28.dp, bottom = 4.dp, start = 12.dp),
        )
    }
}

/**
 * One series' read today: a card with its opening words while it's new or under way, folded to a
 * line once it's read, or when it's due if nothing has come yet.
 */
@Composable
internal fun TodayReadRow(entry: TodayRead, getting: Boolean, onOpen: () -> Unit, onSeries: () -> Unit) {
    val read = entry.read
    when {
        read == null -> WaitingLine(entry.series, onSeries)
        read.status == BookStatus.FINISHED -> ReadLine(entry, onOpen, onSeries)
        else -> ReadCard(entry, getting, onOpen, onSeries)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadCard(entry: TodayRead, getting: Boolean, onOpen: () -> Unit, onSeries: () -> Unit) {
    val read = entry.read ?: return
    val haptics = LocalHapticFeedback.current
    val started = read.status == BookStatus.READING
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .border(0.5.dp, stillPalette.faint, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSeries()
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row {
            SeriesLabel(entry.series.name, Modifier.weight(1f))
            val arrived = read.arrivedAt?.let { formatTime(it.atZone(ZoneId.systemDefault()).toLocalTime()) }
            Text(
                text = when {
                    started -> stringResource(R.string.read_percent, (read.progress * 100).roundToInt())
                    arrived != null -> stringResource(R.string.daily_new_at, arrived).uppercase()
                    else -> stringResource(R.string.daily_new).uppercase()
                },
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = LABEL_SPACING),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = read.title,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        if (!started && read.excerpt != null) {
            Text(
                text = read.excerpt,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (started) ProgressTrack(read.progress.toFloat())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            val minutes = read.minutes
            Text(
                text = when {
                    minutes == null -> ""
                    started -> stringResource(R.string.daily_minutes_left, ceil(minutes * (1 - read.progress)).toInt().coerceAtLeast(1))
                    else -> stringResource(R.string.daily_minutes, minutes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    getting -> stringResource(R.string.daily_getting)
                    started -> stringResource(R.string.daily_continue) + "  ›"
                    else -> stringResource(R.string.daily_start) + "  ›"
                },
                style = MaterialTheme.typography.titleSmall,
                color = stillPalette.accent,
            )
        }
    }
}

@Composable
private fun ReadLine(entry: TodayRead, onOpen: () -> Unit, onSeries: () -> Unit) {
    val read = entry.read ?: return
    SeriesLine(entry.series.name, done = true, onClick = onOpen, onLongClick = onSeries) {
        Text(
            text = read.title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val minutes = (entry.readMillis / MINUTE_MILLIS).toInt()
        if (minutes > 0) {
            Text(
                text = stringResource(R.string.daily_read_in, stringResource(R.string.daily_minutes, minutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WaitingLine(series: ReadSeries, onSeries: () -> Unit) {
    SeriesLine(series.name, done = false, onClick = onSeries, onLongClick = onSeries) {
        Text(
            text = waitingText(series),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** When the next read comes, or that nothing has yet. */
@Composable
private fun waitingText(series: ReadSeries): String {
    val zone = ZoneId.systemDefault()
    val lands = series.landsAtMinute?.let { LocalTime.of(it / 60, it % 60) }
    val checked = series.lastCheckedAt?.atZone(zone)?.takeIf { it.toLocalDate() == LocalDate.now(zone) }
    return when {
        lands != null && LocalTime.now(zone).isBefore(lands.plusMinutes(DUE_GRACE_MINUTES)) -> stringResource(R.string.daily_arrives, formatTime(lands))
        checked != null -> stringResource(R.string.daily_nothing_checked, formatTime(checked.toLocalTime()))
        else -> stringResource(R.string.daily_nothing_yet)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesLine(name: String, done: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
    ) {
        DoneMark(done)
        Column(Modifier.weight(1f)) {
            SeriesLabel(name)
            content()
        }
    }
}

/** A filled circle with a tick once read; an empty one before. */
@Composable
internal fun DoneMark(done: Boolean) {
    if (done) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .background(stillPalette.accent, CircleShape),
        ) {
            Text("✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.background)
        }
    } else {
        Box(
            Modifier
                .size(20.dp)
                .border(1.dp, stillPalette.faint, CircleShape),
        )
    }
}

/** A series' name as a small spaced-out label. */
@Composable
internal fun SeriesLabel(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = LABEL_SPACING),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A quiet line inviting daily reads in, or saying Google needs the person. */
@Composable
internal fun DailyInvite(state: DailyState, hasSeries: Boolean, onClick: () -> Unit) {
    val (line, action) = when {
        state == DailyState.NEEDS_YOU -> stringResource(R.string.daily_needs_you_line) to stringResource(R.string.daily_reconnect)
        hasSeries -> stringResource(R.string.daily_paused_line) to stringResource(R.string.daily_connect)
        else -> stringResource(R.string.daily_setup_line) to stringResource(R.string.daily_set_up)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 20.dp, bottom = 4.dp),
    ) {
        SeriesLabel(stringResource(R.string.daily_setup_label))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                modifier = Modifier.weight(1f),
            )
            Text("$action  ›", style = MaterialTheme.typography.titleSmall, color = stillPalette.accent)
        }
    }
}

/** What to say when a daily read couldn't come down. */
internal fun DailyProblem.message(): Int = when (problem) {
    null -> R.string.daily_problem_needs_you
    DriveProblem.SETUP -> R.string.daily_problem_setup
    DriveProblem.OFFLINE -> R.string.daily_problem_offline
    DriveProblem.UNREADABLE -> R.string.daily_problem_unreadable
    DriveProblem.CANCELLED, DriveProblem.OTHER -> R.string.daily_problem_other
}

/** When a read arrived, in the phone's time zone. */
internal fun Instant.arrivalDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private val LABEL_SPACING = 0.08.em
private const val MINUTE_MILLIS = 60_000L
private const val DUE_GRACE_MINUTES = 5L
