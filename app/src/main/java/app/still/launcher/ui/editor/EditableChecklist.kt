package app.still.launcher.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.productivity.CheckCircle

/** One line of a checklist being edited; [key] stays the same while its text changes. */
data class ChecklistLine(val key: Long, val text: String, val done: Boolean)

/**
 * A checklist edited in place: tick lines, rename them, remove them with ×, and add new ones
 * from the field at the bottom. Used for task subtasks and note checklists.
 */
@Composable
fun EditableChecklist(
    label: String,
    addHint: String,
    removeDescription: String,
    lines: List<ChecklistLine>,
    onChange: (List<ChecklistLine>) -> Unit,
    onAdd: (String) -> Unit,
) {
    var newText by rememberSaveable { mutableStateOf("") }
    Column {
        FieldLabel(label)
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(checked = line.done, skipped = false) {
                    onChange(lines.map { if (it.key == line.key) it.copy(done = !it.done) else it })
                }
                TextField(
                    value = line.text,
                    onValueChange = { text -> onChange(lines.map { if (it.key == line.key) it.copy(text = text) else it }) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (line.done) TextDecoration.LineThrough else null,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onChange(lines.filterNot { it.key == line.key }) },
                    modifier = Modifier.semantics { contentDescription = removeDescription },
                ) { Text("×") }
            }
        }
        OutlinedTextField(
            value = newText,
            onValueChange = { newText = it },
            placeholder = { Text(addHint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAdd(newText)
                newText = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
