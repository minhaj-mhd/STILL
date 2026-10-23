package app.still.launcher.ui.gates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.ui.components.FieldLabel

/** Pick the tasks and habits a gate waits for. Ticks apply straight away; close the sheet when done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPickerSheet(
    choices: List<GateChoice>,
    selected: List<GateItem>,
    onChange: (List<GateItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tasks = choices.filter { it.item.type == ItemType.TASK }
    val habits = choices.filter { it.item.type == ItemType.HABIT }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.gates_choose_items), style = MaterialTheme.typography.headlineSmall)
            if (choices.isEmpty()) {
                Text(
                    text = stringResource(R.string.gates_no_items),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn {
                if (tasks.isNotEmpty()) {
                    item(key = "tasks") { FieldLabel(stringResource(R.string.gates_tasks), Modifier.padding(top = 16.dp)) }
                    items(tasks, key = { "task:${it.item.id}" }) { choice -> ChoiceRow(choice, selected, onChange) }
                }
                if (habits.isNotEmpty()) {
                    item(key = "habits") { FieldLabel(stringResource(R.string.gates_habits), Modifier.padding(top = 16.dp)) }
                    items(habits, key = { "habit:${it.item.id}" }) { choice -> ChoiceRow(choice, selected, onChange) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: GateChoice, selected: List<GateItem>, onChange: (List<GateItem>) -> Unit) {
    val isChosen = choice.item in selected
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(if (isChosen) selected - choice.item else selected + choice.item) },
    ) {
        Checkbox(checked = isChosen, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(
            text = choice.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
