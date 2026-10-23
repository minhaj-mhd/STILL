package app.still.launcher.ui.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Tapping anywhere on the row ticks the task off; editing is in the long-press menu. [locks]
 * names the apps a task gate keeps closed until it's done.
 */
@Composable
fun TaskRow(
    entry: TaskEntry,
    today: LocalDate,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    locks: List<String> = emptyList(),
) {
    val details = listOfNotNull(
        entry.occurrence?.takeIf { entry.isOverdue }?.let { relativeDate(it.date, today) },
        entry.occurrence?.time?.let { formatTime(it) },
        stringResource(R.string.today_skipped).takeIf { entry.isSkipped },
        locksText(locks),
    )
    ItemRow(
        title = entry.task.title,
        details = details,
        priority = entry.task.priority,
        finished = !entry.isPending,
        struckThrough = entry.isDone,
        leading = { CheckCircle(checked = entry.isDone, skipped = entry.isSkipped, onClick = onToggle) },
        onClick = onToggle,
        onLongClick = onLongClick,
    )
}

/** A subtask, indented under its task; tapping it ticks it off. */
@Composable
fun SubtaskRow(subtask: Task, onToggle: () -> Unit) {
    val done = subtask.completedAt != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 32.dp),
    ) {
        CheckCircle(checked = done, skipped = false, onClick = onToggle)
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Tapping anywhere on the row logs progress; editing and streaks are in the long-press menu.
 * [locks] names the apps a task gate keeps closed until it's done.
 */
@Composable
fun HabitRow(
    entry: HabitEntry,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    locks: List<String> = emptyList(),
) {
    val habit = entry.habit
    val progress = when (habit.measure) {
        HabitMeasure.CHECK -> null
        HabitMeasure.COUNT -> stringResource(R.string.today_progress, formatAmount(entry.progress), formatAmount(habit.target))
        HabitMeasure.DURATION -> stringResource(
            R.string.today_progress_unit,
            formatAmount(entry.progress),
            formatAmount(habit.target),
            stringResource(R.string.today_minutes_unit),
        )
        HabitMeasure.MEASUREMENT -> stringResource(
            R.string.today_progress_unit,
            formatAmount(entry.progress),
            formatAmount(habit.target),
            habit.unit.orEmpty(),
        ).trim()
    }
    val quota = habit.quota?.let { quota ->
        val done = entry.quotaDone ?: 0
        when (quota.period) {
            QuotaPeriod.WEEK -> stringResource(R.string.today_quota_week, done, quota.count)
            QuotaPeriod.MONTH -> stringResource(R.string.today_quota_month, done, quota.count)
        }
    }
    val details = listOfNotNull(
        entry.occurrence.time?.let { formatTime(it) },
        progress,
        quota,
        stringResource(R.string.today_skipped).takeIf { entry.isSkipped },
        locksText(locks),
    )
    ItemRow(
        title = habit.title,
        details = details,
        priority = habit.priority,
        finished = entry.isDone || entry.isSkipped,
        struckThrough = false,
        leading = { CheckCircle(checked = entry.isDone, skipped = entry.isSkipped, onClick = onToggle) },
        onClick = onToggle,
        onLongClick = onLongClick,
    )
}

@Composable
private fun ItemRow(
    title: String,
    details: List<String>,
    priority: Priority,
    finished: Boolean,
    struckThrough: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val contentColor = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 4.dp),
    ) {
        leading()
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                textDecoration = if (struckThrough) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        priorityMark(priority)?.let { mark ->
            Text(
                text = mark,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Round tick box with a full-size touch target: filled with a check when done, a dash when skipped. */
@Composable
fun CheckCircle(checked: Boolean, skipped: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .border(1.5.dp, if (checked) colors.onBackground else colors.outline, CircleShape)
                .then(if (checked) Modifier.background(colors.onBackground, CircleShape) else Modifier),
        ) {
            when {
                checked -> Text("✓", style = MaterialTheme.typography.labelLarge, color = colors.background)
                skipped -> Text("–", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** "Locks Instagram, YouTube", or null when the item locks nothing. */
@Composable
private fun locksText(locks: List<String>): String? =
    if (locks.isEmpty()) null else stringResource(R.string.today_locks, locks.joinToString(", "))

private fun priorityMark(priority: Priority): String? = when (priority) {
    Priority.NONE -> null
    Priority.LOW -> "!"
    Priority.MEDIUM -> "!!"
    Priority.HIGH -> "!!!"
}

/** 3.0 → "3", 2.5 → "2.5", 2.25 → "2.3". */
fun formatAmount(value: Double): String =
    value.toBigDecimal().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@Composable
private fun relativeDate(date: LocalDate, today: LocalDate): String =
    if (date == today.minusDays(1)) stringResource(R.string.today_yesterday) else formatShortDate(date)
