package app.still.launcher.ui.gates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.gates.GateCondition
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.productivity.formatAmount

/**
 * Shown instead of an app that a task gate keeps closed: what's left to do, by gate. Nothing
 * can be ticked off here; that happens on the Today page, so opening the app takes finishing
 * the work rather than a tap. "Not now" is the bigger button; an emergency open, while any are
 * left today, waits through a countdown first.
 */
@Composable
fun GateScreen(
    app: LauncherApp,
    onEmergencyOpen: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            Text(stringResource(R.string.gate_title, app.label), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.gate_where_to_finish, app.label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            app.gates.forEach { check ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(check.gate.name)
                    if (check.gate.condition == GateCondition.ITEMS && !check.gate.requireAll) {
                        Text(
                            text = stringResource(R.string.gate_any_one),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    check.tasks.forEach { entry -> PendingItem(entry.task.title, taskDetail(entry)) }
                    check.habits.forEach { entry -> PendingItem(entry.habit.title, habitDetail(entry)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) { Text(stringResource(R.string.delay_not_now)) }
            app.emergencyOpen?.let { emergency ->
                TextButton(onClick = onEmergencyOpen) {
                    Text(pluralStringResource(R.plurals.gate_emergency_open, emergency.left, emergency.left))
                }
            }
        }
    }
}

/** One thing left to do, as plain text: there's deliberately nothing to tap. */
@Composable
private fun PendingItem(title: String, detail: String?) {
    Column {
        Text("• $title", style = MaterialTheme.typography.titleMedium)
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

/** "Overdue", or the time it's due. */
@Composable
private fun taskDetail(entry: TaskEntry): String? = when {
    entry.isOverdue -> stringResource(R.string.today_section_overdue)
    else -> entry.occurrence?.time?.let { formatTime(it) }
}

/** How far along a counted or timed habit is, e.g. "3 / 8" or "5 / 15 min". */
@Composable
private fun habitDetail(entry: HabitEntry): String? {
    val habit = entry.habit
    val progress = formatAmount(entry.progress)
    val target = formatAmount(habit.target)
    return when (habit.measure) {
        HabitMeasure.CHECK -> entry.occurrence.time?.let { formatTime(it) }
        HabitMeasure.COUNT -> stringResource(R.string.today_progress, progress, target)
        HabitMeasure.DURATION -> stringResource(R.string.today_progress_unit, progress, target, stringResource(R.string.today_minutes_unit))
        HabitMeasure.MEASUREMENT -> stringResource(R.string.today_progress_unit, progress, target, habit.unit.orEmpty()).trim()
    }
}
