package app.still.launcher.ui.productivity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class Due { NONE, TODAY, TOMORROW, PICKED }

/** Quick add from the Today page: a title and the few choices that matter most. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    onDismiss: () -> Unit,
    onAddTask: (title: String, due: LocalDate?, priority: Priority) -> Unit,
    onAddHabit: (title: String, measure: HabitMeasure, target: Double, frequency: QuickFrequency) -> Unit,
) {
    var isHabit by rememberSaveable { mutableStateOf(false) }
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
                Chip(stringResource(R.string.add_kind_task), selected = !isHabit) { isHabit = false }
                Chip(stringResource(R.string.add_kind_habit), selected = isHabit) { isHabit = true }
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
                Label(R.string.add_track)
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
                Label(R.string.add_how_often)
                ChipRow {
                    Chip(stringResource(R.string.add_often_daily), timesPerWeek == 0) { timesPerWeek = 0 }
                    (1..6).forEach { count ->
                        Chip(stringResource(R.string.add_often_weekly, count), timesPerWeek == count) { timesPerWeek = count }
                    }
                }
            } else {
                Label(R.string.add_when)
                ChipRow {
                    Chip(stringResource(R.string.add_when_today), due == Due.TODAY) { due = Due.TODAY }
                    Chip(stringResource(R.string.add_when_tomorrow), due == Due.TOMORROW) { due = Due.TOMORROW }
                    Chip(stringResource(R.string.add_when_none), due == Due.NONE) { due = Due.NONE }
                    val pickedLabel = pickedDate?.takeIf { due == Due.PICKED }?.let { formatShortDate(it) }
                    Chip(pickedLabel ?: stringResource(R.string.add_when_pick), due == Due.PICKED) { showDatePicker = true }
                }
                Label(R.string.add_priority)
                ChipRow {
                    Chip(stringResource(R.string.priority_none), priority == Priority.NONE) { priority = Priority.NONE }
                    Chip(stringResource(R.string.priority_low), priority == Priority.LOW) { priority = Priority.LOW }
                    Chip(stringResource(R.string.priority_medium), priority == Priority.MEDIUM) { priority = Priority.MEDIUM }
                    Chip(stringResource(R.string.priority_high), priority == Priority.HIGH) { priority = Priority.HIGH }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = ::submit, enabled = canAdd) { Text(stringResource(R.string.add_confirm)) }
            }
        }
    }

    if (showDatePicker) {
        val initial = (pickedDate ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker works in UTC midnight milliseconds.
                    pickerState.selectedDateMillis?.let { millis ->
                        pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        due = Due.PICKED
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun Label(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun formatShortDate(date: LocalDate): String {
    val locale = LocalLocale.current.platformLocale
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}
