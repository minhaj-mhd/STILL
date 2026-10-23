package app.still.launcher.ui.blocking

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
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.TimePickerDialog
import app.still.launcher.ui.components.WeekdayPicker
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.editor.TitleField
import app.still.launcher.ui.productivity.DeleteConfirmDialog

private enum class PickingTime { START, END }

/** Name, times, days, repeat and apps of one blocking schedule; saved when it closes. */
@Composable
fun ScheduleEditor(
    target: EditorTarget.EditSchedule,
    onClose: () -> Unit,
    viewModel: ScheduleEditorViewModel = viewModel(factory = ScheduleEditorViewModel.Factory),
    launcherViewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory),
) {
    val defaultName = stringResource(target.template?.nameRes ?: R.string.schedules_default_name)
    LaunchedEffect(target) { viewModel.open(target, defaultName) }
    DisposableEffect(Unit) { onDispose { viewModel.save() } }

    // The view model is shared between openings; ignore a draft left over from another schedule.
    val draft = viewModel.draft.collectAsStateWithLifecycle().value?.takeIf { it.target == target }
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val apps by launcherViewModel.uiState.collectAsStateWithLifecycle()
    // One row per package: a schedule covers the app in every profile. Essential apps can't be chosen.
    val blockable = remember(apps.apps) { apps.apps.filter { it.canBlock }.distinctBy { it.component.packageName } }

    var pickingTime by rememberSaveable { mutableStateOf<PickingTime?>(null) }
    var pickingApps by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmIncomplete by rememberSaveable { mutableStateOf(false) }

    // A schedule without apps can't be saved; say so instead of quietly dropping it.
    val incomplete = draft != null && !draft.isComplete
    fun requestClose() {
        if (incomplete) confirmIncomplete = true else onClose()
    }
    BackHandler(enabled = incomplete) { confirmIncomplete = true }

    EditorScaffold(
        title = stringResource(if (target.scheduleId == null) R.string.schedules_new_title else R.string.schedules_edit_title),
        onDone = ::requestClose,
    ) {
        if (draft == null) return@EditorScaffold

        TitleField(draft.name) { name -> viewModel.update { it.copy(name = name) } }

        Column {
            FieldLabel(stringResource(R.string.schedules_time))
            ChipRow {
                Chip(stringResource(R.string.schedules_from, formatTime(minuteToTime(draft.startMinute))), selected = true) {
                    pickingTime = PickingTime.START
                }
                val end = formatTime(minuteToTime(draft.endMinute))
                val endLabel = if (draft.endMinute <= draft.startMinute) stringResource(R.string.schedules_to_next_day, end)
                else stringResource(R.string.schedules_to, end)
                Chip(endLabel, selected = true) { pickingTime = PickingTime.END }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.schedules_days))
            WeekdayPicker(draft.days) { days -> if (days.isNotEmpty()) viewModel.update { it.copy(days = days) } }
        }

        Column {
            FieldLabel(stringResource(R.string.repeat_title))
            ChipRow {
                Chip(stringResource(R.string.schedules_every_week), !draft.everyOtherWeek) {
                    viewModel.update { it.copy(everyOtherWeek = false) }
                }
                Chip(stringResource(R.string.schedules_every_other_week), draft.everyOtherWeek) {
                    viewModel.update { it.copy(everyOtherWeek = true) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.schedules_apps))
            val chosen = blockable.filter { it.component.packageName in draft.packages }
            if (chosen.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedules_no_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chosen.forEach { app -> Text(app.label, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = { pickingApps = true }) { Text(stringResource(R.string.schedules_choose_apps)) }
        }

        if (draft.original != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.schedules_delete)) }
        }
    }

    pickingTime?.let { which ->
        val current = if (which == PickingTime.START) draft?.startMinute else draft?.endMinute
        TimePickerDialog(
            initial = minuteToTime(current ?: 0),
            onDismiss = { pickingTime = null },
            onPick = { time ->
                val minute = time.hour * 60 + time.minute
                viewModel.update { if (which == PickingTime.START) it.copy(startMinute = minute) else it.copy(endMinute = minute) }
                pickingTime = null
            },
        )
    }
    if (pickingApps && draft != null) {
        AppPickerSheet(
            apps = blockable,
            usage = usage,
            selected = draft.packages,
            onChange = { packages -> viewModel.update { it.copy(packages = packages) } },
            onDismiss = { pickingApps = false },
        )
    }
    if (confirmIncomplete) {
        AlertDialog(
            onDismissRequest = { confirmIncomplete = false },
            title = { Text(stringResource(R.string.schedules_incomplete_title)) },
            text = { Text(stringResource(R.string.schedules_incomplete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmIncomplete = false
                    pickingApps = true
                }) { Text(stringResource(R.string.schedules_choose_apps)) }
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
            detail = stringResource(R.string.schedules_delete_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete()
                onClose()
            },
        )
    }
}
