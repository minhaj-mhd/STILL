package app.still.launcher.ui.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Priority
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.DatePickerDialog
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatShortDate
import java.time.LocalDate

private enum class Due { NONE, TODAY, TOMORROW, PICKED }

private enum class AddKind { TASK, HABIT }

/**
 * Quick add from the Today page: a title and the few choices that matter most.
 * "More options" hands the title over to the full editor. Tasks and habits are added one at a
 * time; "Plan" opens a new plan ([onNewPlan]), the one way to add many at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    onDismiss: () -> Unit,
    onAddTask: (title: String, due: LocalDate?, priority: Priority) -> Unit,
    onAddHabit: (title: String, measure: HabitMeasure, target: Double, frequency: QuickFrequency) -> Unit,
    onMoreOptions: (isHabit: Boolean, title: String) -> Unit,
    onNewPlan: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(AddKind.TASK) }
    val isHabit = kind == AddKind.HABIT
    var title by rememberSaveable { mutableStateOf("") }

    var due by rememberSaveable { mutableStateOf(Due.TODAY) }
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var priority by rememberSaveable { mutableStateOf(Priority.NONE) }

    var measure by rememberSaveable { mutableStateOf(HabitMeasure.CHECK) }
    var targetText by rememberSaveable { mutableStateOf("") }
    var timesPerWeek by rememberSaveable { mutableIntStateOf(0) } // 0 = every day

    val target = when (measure) {
        HabitMeasure.CHECK -> 1.0
        else -> targetText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    }
    val canAdd = title.isNotBlank() && (!isHabit || target != null)

    fun submit() {
        if (!canAdd) return
        if (isHabit) {
            val frequency = if (timesPerWeek == 0) QuickFrequency.Daily else QuickFrequency.TimesPerWeek(timesPerWeek)
            onAddHabit(title, measure, target ?: 1.0, frequency)
        } else {
            val today = LocalDate.now()
            val date = when (due) {
                Due.NONE -> null
                Due.TODAY -> today
                Due.TOMORROW -> today.plusDays(1)
                Due.PICKED -> pickedDate
            }
            onAddTask(title, date, priority)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            ChipRow {
                Chip(stringResource(R.string.add_kind_task), selected = kind == AddKind.TASK) { kind = AddKind.TASK }
                Chip(stringResource(R.string.add_kind_habit), selected = kind == AddKind.HABIT) { kind = AddKind.HABIT }
                Chip(stringResource(R.string.add_kind_plan), selected = false, onClick = onNewPlan)
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(stringResource(if (isHabit) R.string.add_habit_hint else R.string.add_task_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            if (isHabit) {
                FieldLabel(stringResource(R.string.add_track))
                ChipRow {
                    Chip(stringResource(R.string.measure_check), measure == HabitMeasure.CHECK) { measure = HabitMeasure.CHECK }
                    Chip(stringResource(R.string.measure_count), measure == HabitMeasure.COUNT) {
                        measure = HabitMeasure.COUNT
                        targetText = "8"
                    }
                    Chip(stringResource(R.string.measure_minutes), measure == HabitMeasure.DURATION) {
                        measure = HabitMeasure.DURATION
                        targetText = "15"
                    }
                }
                if (measure != HabitMeasure.CHECK) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text(stringResource(R.string.add_target)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(160.dp),
                    )
                }
                FieldLabel(stringResource(R.string.add_how_often))
                ChipRow {
                    Chip(stringResource(R.string.add_often_daily), timesPerWeek == 0) { timesPerWeek = 0 }
                    (1..6).forEach { count ->
                        Chip(stringResource(R.string.add_often_weekly, count), timesPerWeek == count) { timesPerWeek = count }
                    }
                }
            } else {
                FieldLabel(stringResource(R.string.add_when))
                ChipRow {
                    Chip(stringResource(R.string.add_when_today), due == Due.TODAY) { due = Due.TODAY }
                    Chip(stringResource(R.string.add_when_tomorrow), due == Due.TOMORROW) { due = Due.TOMORROW }
                    Chip(stringResource(R.string.add_when_none), due == Due.NONE) { due = Due.NONE }
                    val pickedLabel = pickedDate?.takeIf { due == Due.PICKED }?.let { formatShortDate(it) }
                    Chip(pickedLabel ?: stringResource(R.string.add_when_pick), due == Due.PICKED) { showDatePicker = true }
                }
                FieldLabel(stringResource(R.string.add_priority))
                PriorityChips(priority) { priority = it }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onMoreOptions(isHabit, title) }) { Text(stringResource(R.string.add_more_options)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = ::submit, enabled = canAdd) { Text(stringResource(R.string.add_confirm)) }
            }

        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            initial = pickedDate ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onPick = { date ->
                pickedDate = date
                due = Due.PICKED
                showDatePicker = false
            },
        )
    }
}

@Composable
fun PriorityChips(selected: Priority, onSelect: (Priority) -> Unit) {
    ChipRow {
        Chip(stringResource(R.string.priority_none), selected == Priority.NONE) { onSelect(Priority.NONE) }
        Chip(stringResource(R.string.priority_low), selected == Priority.LOW) { onSelect(Priority.LOW) }
        Chip(stringResource(R.string.priority_medium), selected == Priority.MEDIUM) { onSelect(Priority.MEDIUM) }
        Chip(stringResource(R.string.priority_high), selected == Priority.HIGH) { onSelect(Priority.HIGH) }
    }
}
