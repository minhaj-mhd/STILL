package app.still.launcher.ui.keynote

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.practice.Keynote
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.theme.stillPalette

/** A Today item the day's quality can be practised with. */
data class LinkOption(val type: ItemType, val id: Long, val title: String)

/** A quality STILL suggests, with an action that expresses it. */
private data class Preset(@StringRes val quality: Int, @StringRes val action: Int)

private val PRESETS = listOf(
    Preset(R.string.keynote_steadiness, R.string.keynote_steadiness_action),
    Preset(R.string.keynote_patience, R.string.keynote_patience_action),
    Preset(R.string.keynote_attention, R.string.keynote_attention_action),
    Preset(R.string.keynote_courage, R.string.keynote_courage_action),
    Preset(R.string.keynote_goodwill, R.string.keynote_goodwill_action),
)

/**
 * The morning card on home: one quality for the day, one action that expresses it, and the Today
 * item it's practised with. It starts from [initial] (the last quality chosen, or today's when
 * [changing] it). It's an invitation: "Not today" puts it away until tomorrow.
 */
@Composable
fun KeynoteCard(
    initial: Keynote?,
    linkOptions: List<LinkOption>,
    changing: Boolean,
    onStart: (quality: String, action: String, link: KeynoteLink) -> Unit,
    onPutOff: () -> Unit,
) {
    val presets = PRESETS.map { stringResource(it.quality) to stringResource(it.action) }
    var quality by rememberSaveable { mutableStateOf(initial?.quality ?: presets.first().first) }
    var action by rememberSaveable { mutableStateOf(initial?.action ?: presets.first().second) }
    val initialLink = when {
        changing && initial?.itemType != null && initial.itemId != null -> KeynoteLink.Item(initial.itemType, initial.itemId)
        changing -> KeynoteLink.None
        else -> linkOptions.firstOrNull()?.let { KeynoteLink.Item(it.type, it.id) } ?: KeynoteLink.None
    }
    var link by remember { mutableStateOf(initialLink) }
    var editingOwn by remember { mutableStateOf(false) }
    var editingAction by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf(false) }
    // A quality typed in before stays on offer next to the suggestions.
    val own = quality.takeIf { typed -> presets.none { it.first == typed } }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            FieldLabel(stringResource(R.string.keynote_today))
            ChipRow {
                presets.forEach { (name, presetAction) ->
                    Chip(name, quality == name) {
                        quality = name
                        action = presetAction
                    }
                }
                own?.let { Chip(it, selected = true) {} }
                Chip(stringResource(R.string.keynote_own), selected = false) { editingOwn = true }
            }
            Text(
                text = action,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingAction = true }
                    .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
            )
            Box {
                val linkTitle = when (val current = link) {
                    is KeynoteLink.Item -> linkOptions.firstOrNull { it.type == current.type && it.id == current.id }?.title
                    KeynoteLink.NewTask -> stringResource(R.string.keynote_link_new_task)
                    KeynoteLink.None -> null
                } ?: stringResource(R.string.keynote_link_none)
                Text(
                    text = stringResource(R.string.keynote_practise_with, linkTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { linkMenu = true }
                        .padding(vertical = 6.dp),
                )
                DropdownMenu(expanded = linkMenu, onDismissRequest = { linkMenu = false }) {
                    linkOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = {
                            link = KeynoteLink.Item(option.type, option.id)
                            linkMenu = false
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.keynote_link_new_task)) }, onClick = {
                        link = KeynoteLink.NewTask
                        linkMenu = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.keynote_link_none)) }, onClick = {
                        link = KeynoteLink.None
                        linkMenu = false
                    })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onPutOff) { Text(stringResource(if (changing) R.string.cancel else R.string.keynote_not_today)) }
                Button(
                    onClick = { onStart(quality, action, link) },
                    enabled = quality.isNotBlank() && action.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = stillPalette.accent, contentColor = MaterialTheme.colorScheme.background),
                ) { Text(stringResource(if (changing) R.string.keynote_save else R.string.keynote_start)) }
            }
        }
    }

    if (editingOwn) {
        TextDialog(
            title = stringResource(R.string.keynote_own_title),
            initial = own.orEmpty(),
            onSave = {
                quality = it
                editingOwn = false
            },
            onDismiss = { editingOwn = false },
        )
    }
    if (editingAction) {
        TextDialog(
            title = stringResource(R.string.keynote_action_title),
            initial = action,
            onSave = {
                action = it
                editingAction = false
            },
            onDismiss = { editingAction = false },
        )
    }
}

/** One line of text to type, kept when saved; blank text can't be saved. */
@Composable
private fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.keynote_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
