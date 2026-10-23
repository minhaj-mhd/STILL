package app.still.launcher.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.DatePickerDialog
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.Stepper
import app.still.launcher.ui.components.TimePickerDialog
import app.still.launcher.ui.components.WeekdayPicker
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.productivity.DeleteConfirmDialog
import app.still.launcher.ui.productivity.PriorityChips
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HabitEditor(
    target: EditorTarget.EditHabit,
    onClose: () -> Unit,
    viewModel: HabitEditorViewModel = viewModel(factory = HabitEditorViewModel.Factory),
) {
    LaunchedEffect(target) { viewModel.open(target) }
    DisposableEffect(Unit) { onDispose { viewModel.save() } }

    // The view model is shared between openings; ignore a draft left over from another item.
    val draft = viewModel.draft.collectAsStateWithLifecycle().value?.takeIf { it.target == target }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var addingTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    EditorScaffold(
        title = stringResource(if (target.habitId == null) R.string.editor_new_habit else R.string.editor_habit),
        onDone = onClose,
    ) {
        if (draft == null) return@EditorScaffold

        stats?.let { HabitStatsBlock(it) }

        TitleField(draft.title) { title -> viewModel.update { it.copy(title = title) } }
        NotesField(draft.notes, onValueChange = { notes -> viewModel.update { it.copy(notes = notes) } })

        Column {
            FieldLabel(stringResource(R.string.add_priority))
            PriorityChips(draft.priority) { priority -> viewModel.update { it.copy(priority = priority) } }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.add_track))
            ChipRow {
                MeasureChip(R.string.measure_check, HabitMeasure.CHECK, draft, viewModel, defaultTarget = "1")
                MeasureChip(R.string.measure_count, HabitMeasure.COUNT, draft, viewModel, defaultTarget = "8")
                MeasureChip(R.string.measure_minutes, HabitMeasure.DURATION, draft, viewModel, defaultTarget = "15")
                MeasureChip(R.string.measure_amount, HabitMeasure.MEASUREMENT, draft, viewModel, defaultTarget = "1")
            }
            if (draft.measure != HabitMeasure.CHECK) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.targetText,
                        onValueChange = { text -> viewModel.update { it.copy(targetText = text) } },
                        label = { Text(stringResource(R.string.add_target)) },
                        isError = draft.targetValue == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp),
                    )
                    if (draft.measure == HabitMeasure.MEASUREMENT) {
                        OutlinedTextField(
                            value = draft.unit,
                            onValueChange = { unit -> viewModel.update { it.copy(unit = unit) } },
                            label = { Text(stringResource(R.string.editor_unit)) },
                            singleLine = true,
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
        }

        FrequencyEditor(draft.frequency, draft.start) { frequency -> viewModel.update { it.copy(frequency = frequency) } }

        if (draft.frequency.isScheduled) {
            Column {
                FieldLabel(stringResource(R.string.editor_starts))
                ChipRow { Chip(formatShortDate(draft.start), selected = true) { pickingStart = true } }
            }
            Column {
                FieldLabel(stringResource(R.string.editor_times))
                ChipRow {
                    // Tapping a time removes it.
                    draft.times.sorted().forEach { time ->
                        Chip("${formatTime(time)}  ×", selected = true) {
                            viewModel.update { it.copy(times = it.times - time) }
                        }
                    }
                    Chip(stringResource(R.string.editor_add_time), selected = false) { addingTime = true }
                }
            }
        }

        if (draft.original != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.item_delete_habit)) }
        }
    }

    if (pickingStart && draft != null) {
        DatePickerDialog(
            initial = draft.start,
            onDismiss = { pickingStart = false },
            onPick = { date ->
                viewModel.update { it.copy(start = date) }
                pickingStart = false
            },
        )
    }
    if (addingTime) {
        TimePickerDialog(
            initial = LocalTime.of(8, 0),
            onDismiss = { addingTime = false },
            onPick = { time ->
                viewModel.update { it.copy(times = (it.times + time).distinct()) }
                addingTime = false
            },
        )
    }
    if (confirmDelete && draft != null) {
        DeleteConfirmDialog(
            name = draft.title,
            detail = stringResource(R.string.item_delete_habit_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete()
                onClose()
            },
        )
    }
}

@Composable
private fun MeasureChip(
    label: Int,
    measure: HabitMeasure,
    draft: HabitDraft,
    viewModel: HabitEditorViewModel,
    defaultTarget: String,
) {
    Chip(stringResource(label), draft.measure == measure) {
        if (draft.measure != measure) viewModel.update { it.copy(measure = measure, targetText = defaultTarget) }
    }
}

@Composable
private fun FrequencyEditor(
    frequency: HabitFrequency,
    start: LocalDate,
    onChange: (HabitFrequency) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.add_how_often))
        ChipRow {
            Chip(stringResource(R.string.add_often_daily), frequency == HabitFrequency.Daily) { onChange(HabitFrequency.Daily) }
            Chip(stringResource(R.string.often_specific_days), frequency is HabitFrequency.SpecificDays) {
                if (frequency !is HabitFrequency.SpecificDays) onChange(HabitFrequency.SpecificDays(setOf(start.dayOfWeek)))
            }
            Chip(stringResource(R.string.often_every_few_days), frequency is HabitFrequency.EveryNDays) {
                if (frequency !is HabitFrequency.EveryNDays) onChange(HabitFrequency.EveryNDays(2))
            }
            Chip(stringResource(R.string.often_per_week), frequency is HabitFrequency.PerWeek) {
                if (frequency !is HabitFrequency.PerWeek) onChange(HabitFrequency.PerWeek(3))
            }
            Chip(stringResource(R.string.often_per_month), frequency is HabitFrequency.PerMonth) {
                if (frequency !is HabitFrequency.PerMonth) onChange(HabitFrequency.PerMonth(10))
            }
            if (frequency is HabitFrequency.Custom) {
                Chip(stringResource(R.string.often_custom), selected = true) {}
            }
        }
        when (frequency) {
            is HabitFrequency.SpecificDays -> WeekdayPicker(frequency.days) { days ->
                if (days.isNotEmpty()) onChange(HabitFrequency.SpecificDays(days))
            }
            is HabitFrequency.EveryNDays -> Stepper(
                value = frequency.days,
                onChange = { onChange(HabitFrequency.EveryNDays(it)) },
                range = 2..30,
                label = pluralStringResource(R.plurals.repeat_every_days, frequency.days, frequency.days),
            )
            is HabitFrequency.PerWeek -> Stepper(
                value = frequency.count,
                onChange = { onChange(HabitFrequency.PerWeek(it)) },
                range = 1..7,
                label = pluralStringResource(R.plurals.often_per_week_count, frequency.count, frequency.count),
            )
            is HabitFrequency.PerMonth -> Stepper(
                value = frequency.count,
                onChange = { onChange(HabitFrequency.PerMonth(it)) },
                range = 1..31,
                label = pluralStringResource(R.plurals.often_per_month_count, frequency.count, frequency.count),
            )
            HabitFrequency.Daily, is HabitFrequency.Custom -> Unit
        }
    }
}
