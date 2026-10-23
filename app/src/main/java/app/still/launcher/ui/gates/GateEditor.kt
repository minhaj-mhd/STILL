package app.still.launcher.ui.gates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.gates.GateCondition
import app.still.launcher.data.gates.GateWindow
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.productivity.Priority
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.blocking.AppPickerSheet
import app.still.launcher.ui.blocking.minuteToTime
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.TimePickerDialog
import app.still.launcher.ui.components.WeekdayPicker
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.editor.SwitchRow
import app.still.launcher.ui.editor.TitleField
import app.still.launcher.ui.productivity.DeleteConfirmDialog
import java.time.DayOfWeek

private enum class PickingTime { START, END }

/** Weekdays 06:00–18:00, offered when a gate gets set hours. */
private val DEFAULT_WINDOW = GateWindow(DayOfWeek.entries.take(5).toSet(), 6 * 60, 18 * 60)
private val EMERGENCY_OPENS = listOf(0, 1, 3, 5)
private val EMERGENCY_MINUTES = listOf(2, 5, 10)

/** Apps, what they wait for, hours and emergency opens of one task gate; saved when it closes. */
@Composable
fun GateEditor(
    target: EditorTarget.EditGate,
    onClose: () -> Unit,
    viewModel: GateEditorViewModel = viewModel(factory = GateEditorViewModel.Factory),
    launcherViewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory),
) {
    val defaultName = stringResource(R.string.gates_default_name)
    LaunchedEffect(target) { viewModel.open(target, defaultName) }
    DisposableEffect(Unit) { onDispose { viewModel.save() } }

    // The view model is shared between openings; ignore a draft left over from another gate.
    val draft = viewModel.draft.collectAsStateWithLifecycle().value?.takeIf { it.target == target }
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val apps by launcherViewModel.uiState.collectAsStateWithLifecycle()
    // One row per package: a gate covers the app in every profile. Apps gates never close can't be chosen.
    val gateable = remember(apps.apps) { apps.apps.filter { it.canGate }.distinctBy { it.component.packageName } }

    var pickingApps by rememberSaveable { mutableStateOf(false) }
    var pickingItems by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf<PickingTime?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmIncomplete by rememberSaveable { mutableStateOf(false) }

    // An incomplete gate can't be saved; say so instead of quietly dropping it.
    val incomplete = draft != null && !draft.isComplete
    fun requestClose() {
        if (incomplete) confirmIncomplete = true else onClose()
    }
    BackHandler(enabled = incomplete) { confirmIncomplete = true }

    EditorScaffold(
        title = stringResource(if (target.gateId == null) R.string.gates_new_title else R.string.gates_edit_title),
        onDone = ::requestClose,
    ) {
        if (draft == null) return@EditorScaffold

        TitleField(draft.name) { name -> viewModel.update { it.copy(name = name) } }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.gates_apps))
            val chosen = gateable.filter { it.component.packageName in draft.packages }
            if (chosen.isEmpty()) Hint(stringResource(R.string.gates_no_apps))
            chosen.forEach { app -> Text(app.label, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = { pickingApps = true }) { Text(stringResource(R.string.schedules_choose_apps)) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.gates_until))
            ChipRow {
                Chip(stringResource(R.string.gates_by_priority), draft.condition == GateCondition.PRIORITY) {
                    viewModel.update { it.copy(condition = GateCondition.PRIORITY) }
                }
                Chip(stringResource(R.string.gates_by_items), draft.condition == GateCondition.ITEMS) {
                    viewModel.update { it.copy(condition = GateCondition.ITEMS) }
                }
            }
            when (draft.condition) {
                GateCondition.PRIORITY -> {
                    ChipRow {
                        Chip(stringResource(R.string.gates_priority_high), draft.minPriority == Priority.HIGH) {
                            viewModel.update { it.copy(minPriority = Priority.HIGH) }
                        }
                        Chip(stringResource(R.string.gates_priority_medium), draft.minPriority == Priority.MEDIUM) {
                            viewModel.update { it.copy(minPriority = Priority.MEDIUM) }
                        }
                        Chip(stringResource(R.string.gates_priority_any), draft.minPriority == Priority.LOW) {
                            viewModel.update { it.copy(minPriority = Priority.LOW) }
                        }
                    }
                    // At least one of the two stays on.
                    ChipRow {
                        Chip(stringResource(R.string.gates_tasks), draft.includeTasks) {
                            viewModel.update { if (it.includeHabits) it.copy(includeTasks = !it.includeTasks) else it }
                        }
                        Chip(stringResource(R.string.gates_habits), draft.includeHabits) {
                            viewModel.update { if (it.includeTasks) it.copy(includeHabits = !it.includeHabits) else it }
                        }
                    }
                    if (draft.includeTasks) {
                        SwitchRow(
                            title = stringResource(R.string.gates_include_undated),
                            detail = stringResource(R.string.gates_include_undated_detail),
                            checked = draft.includeUndated,
                        ) { on -> viewModel.update { it.copy(includeUndated = on) } }
                    }
                    Hint(stringResource(R.string.gates_priority_hint))
                }
                GateCondition.ITEMS -> {
                    val chosen = choices.filter { it.item in draft.items }
                    if (chosen.isEmpty()) Hint(stringResource(R.string.gates_no_items_chosen))
                    chosen.forEach { choice -> Text(choice.title, style = MaterialTheme.typography.titleMedium) }
                    TextButton(onClick = { pickingItems = true }) { Text(stringResource(R.string.gates_choose_items)) }
                    ChipRow {
                        Chip(stringResource(R.string.gates_require_all), draft.requireAll) { viewModel.update { it.copy(requireAll = true) } }
                        Chip(stringResource(R.string.gates_require_any), !draft.requireAll) { viewModel.update { it.copy(requireAll = false) } }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.gates_when))
            ChipRow {
                Chip(stringResource(R.string.gates_always), draft.window == null) { viewModel.update { it.copy(window = null) } }
                Chip(stringResource(R.string.gates_set_hours), draft.window != null) {
                    viewModel.update { it.copy(window = it.window ?: DEFAULT_WINDOW) }
                }
            }
            draft.window?.let { window ->
                WeekdayPicker(window.days) { days -> if (days.isNotEmpty()) viewModel.update { it.copy(window = window.copy(days = days)) } }
                ChipRow {
                    Chip(stringResource(R.string.schedules_from, formatTime(minuteToTime(window.startMinute))), selected = true) {
                        pickingTime = PickingTime.START
                    }
                    val end = formatTime(minuteToTime(window.endMinute))
                    val endLabel = if (window.endMinute <= window.startMinute) stringResource(R.string.schedules_to_next_day, end)
                    else stringResource(R.string.schedules_to, end)
                    Chip(endLabel, selected = true) { pickingTime = PickingTime.END }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.gates_emergency))
            ChipRow {
                EMERGENCY_OPENS.forEach { opens ->
                    val label = if (opens == 0) stringResource(R.string.gates_emergency_none) else stringResource(R.string.gates_emergency_per_day, opens)
                    Chip(label, draft.emergencyOpensPerDay == opens) { viewModel.update { it.copy(emergencyOpensPerDay = opens) } }
                }
            }
            if (draft.emergencyOpensPerDay > 0) {
                Hint(stringResource(R.string.gates_emergency_detail, draft.original?.emergencyDelaySeconds ?: TaskGate.DEFAULT_EMERGENCY_DELAY_SECONDS))
                ChipRow {
                    EMERGENCY_MINUTES.forEach { minutes ->
                        Chip(stringResource(R.string.time_minutes, minutes), draft.emergencyMinutes == minutes) {
                            viewModel.update { it.copy(emergencyMinutes = minutes) }
                        }
                    }
                }
            }
        }

        if (draft.original != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.gates_delete)) }
        }
    }

    if (pickingApps && draft != null) {
        AppPickerSheet(
            apps = gateable,
            usage = usage,
            selected = draft.packages,
            onChange = { packages -> viewModel.update { it.copy(packages = packages) } },
            onDismiss = { pickingApps = false },
        )
    }
    if (pickingItems && draft != null) {
        ItemPickerSheet(
            choices = choices,
            selected = draft.items,
            onChange = { items -> viewModel.update { it.copy(items = items) } },
            onDismiss = { pickingItems = false },
        )
    }
    pickingTime?.let { which ->
        val window = draft?.window ?: DEFAULT_WINDOW
        TimePickerDialog(
            initial = minuteToTime(if (which == PickingTime.START) window.startMinute else window.endMinute),
            onDismiss = { pickingTime = null },
            onPick = { time ->
                val minute = time.hour * 60 + time.minute
                viewModel.update { draft ->
                    val current = draft.window ?: DEFAULT_WINDOW
                    draft.copy(window = if (which == PickingTime.START) current.copy(startMinute = minute) else current.copy(endMinute = minute))
                }
                pickingTime = null
            },
        )
    }
    if (confirmIncomplete) {
        AlertDialog(
            onDismissRequest = { confirmIncomplete = false },
            title = { Text(stringResource(R.string.gates_incomplete_title)) },
            text = { Text(stringResource(R.string.gates_incomplete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmIncomplete = false }) { Text(stringResource(R.string.gates_keep_editing)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmIncomplete = false
                    viewModel.discard()
                    onClose()
                }) { Text(stringResource(R.string.schedules_discard)) }
            },
        )
    }
    if (confirmDelete && draft != null) {
        DeleteConfirmDialog(
            name = draft.name,
            detail = stringResource(R.string.gates_delete_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete()
                onClose()
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
