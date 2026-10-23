package app.still.launcher.ui.productivity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry

@Composable
fun TaskActionsSheet(
    entry: TaskEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSetSkipped: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ActionsSheet(title = entry.task.title, onDismiss = onDismiss) {
        ActionRow(stringResource(R.string.item_edit), onEdit)
        // Only recurring occurrences can be skipped; a one-time task is either done or still due.
        if (entry.occurrence != null && entry.task.schedule?.isRecurring == true && !entry.isDone) {
            if (entry.isSkipped) ActionRow(stringResource(R.string.item_unskip)) { onSetSkipped(false) }
            else ActionRow(stringResource(R.string.item_skip_today)) { onSetSkipped(true) }
        }
        ActionRow(stringResource(R.string.item_delete_task)) { confirmDelete = true }
    }
    if (confirmDelete) {
        DeleteConfirmDialog(
            name = entry.task.title,
            detail = stringResource(R.string.item_delete_task_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
}

@Composable
fun HabitActionsSheet(
    entry: HabitEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onLogAmount: () -> Unit,
    onSetSkipped: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ActionsSheet(title = entry.habit.title, onDismiss = onDismiss) {
        ActionRow(stringResource(R.string.item_edit), onEdit)
        if (entry.habit.measure != HabitMeasure.CHECK) {
            ActionRow(stringResource(R.string.item_log_amount), onLogAmount)
        }
        if (!entry.isDone) {
            if (entry.isSkipped) ActionRow(stringResource(R.string.item_unskip)) { onSetSkipped(false) }
            else ActionRow(stringResource(R.string.item_skip_today)) { onSetSkipped(true) }
        }
        ActionRow(stringResource(R.string.item_delete_habit)) { confirmDelete = true }
    }
    if (confirmDelete) {
        DeleteConfirmDialog(
            name = entry.habit.title,
            detail = stringResource(R.string.item_delete_habit_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
}

/** Sets the count, minutes or amount logged for one habit occurrence. */
@Composable
fun AmountDialog(
    entry: HabitEntry,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(if (entry.progress > 0) formatAmount(entry.progress) else "") }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_amount_title, entry.habit.title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = {
                    val unit = when (entry.habit.measure) {
                        HabitMeasure.DURATION -> stringResource(R.string.today_minutes_unit)
                        else -> entry.habit.unit.orEmpty()
                    }
                    Text("/ ${formatAmount(entry.habit.target)} $unit".trim())
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null) {
                Text(stringResource(R.string.item_amount_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/** Asks before deleting something whose history goes with it. */
@Composable
fun DeleteConfirmDialog(name: String, detail: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_delete_title, name)) },
        text = { Text(detail) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.item_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
