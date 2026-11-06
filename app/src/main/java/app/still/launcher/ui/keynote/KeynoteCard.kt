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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.practice.Keynote
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.keynote.OwnQuality
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.theme.stillPalette

/** A Today item the day's quality can be practised with. */
data class LinkOption(val type: ItemType, val id: Long, val title: String)

/** Today's unfinished tasks and habits, to practise the day's quality with. */
fun linkOptionsOf(agenda: TodayAgenda?): List<LinkOption> = agenda?.let { today ->
    (today.overdue + today.today).filter { it.isPending }.map { LinkOption(ItemType.TASK, it.task.id, it.task.title) } +
        today.habits.filter { it.isPending }.map { LinkOption(ItemType.HABIT, it.habit.id, it.habit.title) }
}.orEmpty().distinctBy { it.type to it.id }

/** A quality STILL suggests, with an action that expresses it. */
private data class Preset(@StringRes val quality: Int, @StringRes val action: Int)

private val PRESETS = listOf(
    Preset(R.string.keynote_steadiness, R.string.keynote_steadiness_action),
    Preset(R.string.keynote_patience, R.string.keynote_patience_action),
    Preset(R.string.keynote_attention, R.string.keynote_attention_action),
    Preset(R.string.keynote_courage, R.string.keynote_courage_action),
    Preset(R.string.keynote_goodwill, R.string.keynote_goodwill_action),
)

/** The names of the qualities STILL suggests, which aren't kept among your own. */
@Composable
fun suggestedQualities(): List<String> = PRESETS.map { stringResource(it.quality) }

/**
 * Choosing one quality for the day, one action that expresses it, and the Today item it's
 * practised with: the morning card on home, and the same in Customise. It starts from [initial]
 * (the last quality chosen, or today's when [changing] it). Next to the suggestions are your
 * [own] qualities; "+ New" adds one through [onAddOwn], and [onRemoveOwn], when given, puts a × on
 * each of yours. Without [onPutOff] there's no "Not today".
 */
@Composable
fun KeynoteCard(
    initial: Keynote?,
    linkOptions: List<LinkOption>,
    changing: Boolean,
    own: List<OwnQuality>,
    onStart: (quality: String, action: String, link: KeynoteLink) -> Unit,
    onAddOwn: (quality: String, action: String) -> Unit,
    onPutOff: (() -> Unit)?,
    onRemoveOwn: ((quality: String) -> Unit)? = null,
    startLabel: String? = null,
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
    var adding by remember { mutableStateOf(false) }
    var editingAction by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf(false) }
    // A quality typed in before STILL kept your own, or just taken off the list, stays on offer while chosen.
    val typed = quality.takeIf { chosen -> presets.none { it.first == chosen } && own.none { it.quality.equals(chosen, ignoreCase = true) } }

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
                own.forEach { saved ->
                    OwnChip(
                        name = saved.quality,
                        selected = quality.equals(saved.quality, ignoreCase = true),
                        onClick = {
                            quality = saved.quality
                            action = saved.action
                        },
                        onRemove = onRemoveOwn?.let { remove -> { remove(saved.quality) } },
                    )
                }
                typed?.let { Chip(it, selected = true) {} }
                Chip(stringResource(R.string.keynote_new), selected = false) { adding = true }
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
                onPutOff?.let { putOff ->
                    TextButton(onClick = putOff) { Text(stringResource(if (changing) R.string.cancel else R.string.keynote_not_today)) }
                }
                Button(
                    onClick = { onStart(quality, action, link) },
                    enabled = quality.isNotBlank() && action.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = stillPalette.accent, contentColor = MaterialTheme.colorScheme.background),
                ) { Text(startLabel ?: stringResource(if (changing) R.string.keynote_save else R.string.keynote_start)) }
            }
        }
    }

    if (adding) {
        QualityDialog(
            onSave = { name, deed ->
                quality = name
                action = deed
                adding = false
                onAddOwn(name, deed)
            },
            onDismiss = { adding = false },
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

/** One of your own qualities; with [onRemove], a × that takes it off the list. */
@Composable
private fun OwnChip(name: String, selected: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name) },
        trailingIcon = onRemove?.let { remove ->
            {
                Text(
                    text = "×",
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(R.string.keynote_remove_own, name), role = Role.Button, onClick = remove)
                        .padding(horizontal = 2.dp),
                )
            }
        },
    )
}

/** A new quality of your own and the action that shows it; both are needed. */
@Composable
private fun QualityDialog(onSave: (quality: String, action: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var deed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keynote_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.keynote_new_quality)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                OutlinedTextField(
                    value = deed,
                    onValueChange = { deed = it },
                    label = { Text(stringResource(R.string.keynote_new_action)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), deed.trim()) }, enabled = name.isNotBlank() && deed.isNotBlank()) {
                Text(stringResource(R.string.keynote_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
