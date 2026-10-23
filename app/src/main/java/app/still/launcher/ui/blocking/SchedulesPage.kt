package app.still.launcher.ui.blocking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.blocking.activeWindow
import app.still.launcher.blocking.isOffForToday
import app.still.launcher.data.blocking.BlockingSchedule
import app.still.launcher.schedule.Recurrence
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.components.rememberCurrentMinute
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * Settings > Blocking schedules. Each schedule has a switch; switching one off asks whether
 * that's just for today. Editing a schedule that is blocking right now asks first.
 */
@Composable
fun SchedulesPage(
    onBack: () -> Unit,
    onOpenEditor: (EditorTarget.EditSchedule) -> Unit,
    viewModel: SchedulesViewModel = viewModel(factory = SchedulesViewModel.Factory),
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val now = rememberCurrentMinute().value.toLocalDateTime()
    var turningOff by remember { mutableStateOf<BlockingSchedule?>(null) }
    var confirmingEdit by remember { mutableStateOf<BlockingSchedule?>(null) }

    EditorScaffold(title = stringResource(R.string.schedules_title), onDone = onBack) {
        val list = schedules ?: return@EditorScaffold
        if (list.isEmpty()) {
            Text(
                text = stringResource(R.string.schedules_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        list.forEach { schedule ->
            ScheduleRow(
                schedule = schedule,
                now = now,
                onOpen = {
                    if (schedule.isBlockingNow(now)) confirmingEdit = schedule
                    else onOpenEditor(EditorTarget.EditSchedule(schedule.id))
                },
                onSwitch = { on -> if (on) viewModel.turnOn(schedule) else turningOff = schedule },
            )
        }
        Column {
            FieldLabel(stringResource(R.string.schedules_new))
            ChipRow {
                ScheduleTemplate.entries.forEach { template ->
                    Chip(stringResource(template.nameRes), selected = false) {
                        onOpenEditor(EditorTarget.EditSchedule(null, template))
                    }
                }
                Chip(stringResource(R.string.schedules_custom), selected = false) { onOpenEditor(EditorTarget.EditSchedule(null)) }
            }
        }
    }

    turningOff?.let { schedule ->
        AlertDialog(
            onDismissRequest = { turningOff = null },
            title = { Text(stringResource(R.string.schedules_turn_off_title, schedule.name)) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.turnOffForToday(schedule)
                        turningOff = null
                    }) { Text(stringResource(R.string.schedules_off_today)) }
                    TextButton(onClick = {
                        viewModel.turnOff(schedule)
                        turningOff = null
                    }) { Text(stringResource(R.string.schedules_off_permanently)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { turningOff = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    confirmingEdit?.let { schedule ->
        AlertDialog(
            onDismissRequest = { confirmingEdit = null },
            title = { Text(stringResource(R.string.schedules_edit_active_title)) },
            text = { Text(stringResource(R.string.schedules_edit_active_body, schedule.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingEdit = null
                    onOpenEditor(EditorTarget.EditSchedule(schedule.id))
                }) { Text(stringResource(R.string.item_edit)) }
            },
            dismissButton = { TextButton(onClick = { confirmingEdit = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun BlockingSchedule.isBlockingNow(now: LocalDateTime): Boolean = enabled && activeWindow(now) != null

@Composable
private fun ScheduleRow(
    schedule: BlockingSchedule,
    now: LocalDateTime,
    onOpen: () -> Unit,
    onSwitch: (Boolean) -> Unit,
) {
    val offToday = schedule.enabled && schedule.isOffForToday(now)
    val window = schedule.activeWindow(now)
    val status = when {
        !schedule.enabled -> stringResource(R.string.schedules_status_off)
        offToday -> stringResource(R.string.schedules_status_off_today)
        window != null -> stringResource(R.string.schedules_status_blocking, formatTime(window.endsAt.toLocalTime()))
        else -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = scheduleSummary(schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) }
        }
        Switch(checked = schedule.enabled && !offToday, onCheckedChange = onSwitch)
    }
}

/** "Every day · 21:00–07:00 · 3 apps", with "Every other week" when it alternates. */
@Composable
fun scheduleSummary(schedule: BlockingSchedule): String {
    val recurrence = schedule.schedule.recurrence as? Recurrence.Weekly
    val days = daysLabel(recurrence?.days ?: DayOfWeek.entries.toSet())
    val repeat = if ((recurrence?.interval ?: 1) > 1) stringResource(R.string.schedules_every_other_week) else null
    val times = "${formatTime(minuteToTime(schedule.startMinute))}–${formatTime(minuteToTime(schedule.endMinute))}"
    val apps = pluralStringResource(R.plurals.settings_app_count, schedule.packages.size, schedule.packages.size)
    return listOfNotNull(repeat, days, times, apps).joinToString(" · ")
}

/** "Every day", "Weekdays", "Weekends", or the days themselves: "Mon, Wed, Fri". */
@Composable
fun daysLabel(days: Set<DayOfWeek>): String {
    val weekdays = DayOfWeek.entries.take(5).toSet()
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val locale = LocalLocale.current.platformLocale
    return when (days) {
        DayOfWeek.entries.toSet() -> stringResource(R.string.schedules_every_day)
        weekdays -> stringResource(R.string.schedules_weekdays)
        weekend -> stringResource(R.string.schedules_weekends)
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}

fun minuteToTime(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)
