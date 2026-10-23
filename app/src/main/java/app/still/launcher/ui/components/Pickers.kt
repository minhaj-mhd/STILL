package app.still.launcher.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import androidx.compose.material3.DatePickerDialog as M3DatePickerDialog

/** Material date picker that speaks [LocalDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The picker works in milliseconds at UTC midnight.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    M3DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Material clock picker that speaks [LocalTime], in the phone's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Seven round toggles, Monday to Sunday. */
@Composable
fun WeekdayPicker(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val isOn = day in selected
            val colors = MaterialTheme.colorScheme
            OutlinedButton(
                onClick = { onChange(if (isOn) selected - day else selected + day) },
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isOn) colors.onBackground else colors.background,
                    contentColor = if (isOn) colors.background else colors.onBackground,
                ),
                modifier = Modifier.size(40.dp),
            ) {
                Text(day.getDisplayName(TextStyle.NARROW, locale))
            }
        }
    }
}

/** "−  label  +" for small whole numbers. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}
