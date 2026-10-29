package app.still.launcher.ui.productivity

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.PlanColor
import app.still.launcher.productivity.MAX_PASTED_CHARS
import app.still.launcher.productivity.MAX_PASTED_ITEMS
import app.still.launcher.productivity.PasteProblem
import app.still.launcher.productivity.PastedList
import app.still.launcher.productivity.PlanCalendar
import app.still.launcher.productivity.pastePrompt
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.DatePickerDialog
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.TimePickerDialog
import app.still.launcher.ui.components.WeekdayPicker
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.editor.transparentFieldColors
import app.still.launcher.ui.theme.planColor
import app.still.launcher.ui.theme.stillPalette
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Writes a new plan, reads a pasted one ([EditorTarget.PasteList], or "Paste a plan" here), or
 * changes a saved one: its name, start, the weekdays its days fall on, what a missed day does,
 * its colour, and each day's tasks. Nothing is saved until Create or Done; [onSaved] then gets
 * the plan's id.
 */
@Composable
fun PlanEditor(
    target: EditorTarget,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: PlanEditorViewModel = viewModel(factory = PlanEditorViewModel.Factory),
) {
    LaunchedEffect(target) { viewModel.open(target) }
    // Nothing is kept when the editor goes, except across a rotation.
    val activity = LocalActivity.current
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) viewModel.close() } }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val current = draft ?: return
    val isNew = current.original == null
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingDays by rememberSaveable { mutableStateOf(current.weekdays != Plan.EVERY_DAY && current.weekdays != Plan.WEEKDAYS) }
    var timeFor by rememberSaveable { mutableStateOf<Long?>(null) }

    EditorScaffold(
        title = stringResource(if (isNew) R.string.plan_new else R.string.plan_edit_title),
        action = stringResource(if (isNew) R.string.plan_create else R.string.editor_done),
        actionEnabled = current.canSave && !pasting,
        onCancel = onClose,
        onDone = { viewModel.save(onSaved) },
    ) {
        if (isNew && target is EditorTarget.EditPlan) {
            ChipRow {
                Chip(stringResource(R.string.plan_write), !pasting) { pasting = false }
                Chip(stringResource(R.string.plan_paste), pasting) { pasting = true }
            }
        }
        if (pasting) {
            PasteBox { text ->
                viewModel.readPasted(text)
                pasting = false
            }
            return@EditorScaffold
        }

        Column {
            FieldLabel(stringResource(R.string.plan_name))
            TextField(
                value = current.name,
                onValueChange = { name -> viewModel.update { it.copy(name = name) } },
                placeholder = { Text(stringResource(R.string.plan_name_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.plan_starts_label))
            val today = LocalDate.now()
            ChipRow {
                Chip(stringResource(R.string.add_when_today), current.start == today) { viewModel.update { it.copy(start = today) } }
                Chip(stringResource(R.string.add_when_tomorrow), current.start == today.plusDays(1)) {
                    viewModel.update { it.copy(start = today.plusDays(1)) }
                }
                val other = current.start.takeIf { it != today && it != today.plusDays(1) }
                Chip(other?.let { formatShortDate(it) } ?: stringResource(R.string.add_when_pick), other != null) { pickingStart = true }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.plan_falls_on))
            ChipRow {
                Chip(stringResource(R.string.plan_every_day), !pickingDays && current.weekdays == Plan.EVERY_DAY) {
                    pickingDays = false
                    viewModel.update { it.copy(weekdays = Plan.EVERY_DAY) }
                }
                Chip(stringResource(R.string.plan_weekdays), !pickingDays && current.weekdays == Plan.WEEKDAYS) {
                    pickingDays = false
                    viewModel.update { it.copy(weekdays = Plan.WEEKDAYS) }
                }
                Chip(stringResource(R.string.plan_pick_days), pickingDays) { pickingDays = true }
            }
            if (pickingDays) {
                val selected = DayOfWeek.entries.filter { day -> current.weekdays and (1 shl day.ordinal) != 0 }.toSet()
                WeekdayPicker(selected) { days -> if (days.isNotEmpty()) viewModel.update { it.copy(weekdays = Plan.weekdaysOf(days)) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.paste_missed_day))
            ChipRow {
                Chip(stringResource(R.string.paste_missed_move), current.movesAlong) { viewModel.update { it.copy(movesAlong = true) } }
                Chip(stringResource(R.string.paste_missed_keep), !current.movesAlong) { viewModel.update { it.copy(movesAlong = false) } }
            }
            Text(
                stringResource(if (current.movesAlong) R.string.paste_missed_move_detail else R.string.paste_missed_keep_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.plan_colour))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanColor.entries.forEach { color ->
                    ColorSwatch(color, selected = current.color == color) { viewModel.update { it.copy(color = color) } }
                }
            }
        }

        Column {
            FieldLabel(stringResource(R.string.plan_days_label))
            // Dates as planned; a plan that has moved along shows where its days are now on its page.
            val calendar = PlanCalendar(current.start, current.weekdays, current.original?.pauses.orEmpty())
            (1..maxOf(current.dayCount, current.tasks.maxOfOrNull { it.day } ?: 1)).forEach { day ->
                val date = calendar.date(day)?.takeIf { isNew }
                Text(
                    listOfNotNull(stringResource(R.string.plan_day, day), date?.let { formatShortDate(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                current.tasks.filter { it.day == day }.forEach { task ->
                    TaskLine(
                        task = task,
                        onTitle = { title -> viewModel.update { d -> d.copy(tasks = d.tasks.map { if (it.key == task.key) it.copy(title = title) else it }) } },
                        onTime = { timeFor = task.key },
                        onRemove = { viewModel.update { d -> d.copy(tasks = d.tasks.filterNot { it.key == task.key }) } },
                    )
                }
                TextButton(onClick = { viewModel.addTask(day) }) { Text(stringResource(R.string.plan_add_task)) }
            }
            TextButton(onClick = { viewModel.update { it.copy(dayCount = maxOf(it.dayCount, it.tasks.maxOfOrNull { t -> t.day } ?: 1) + 1) } }) {
                Text(stringResource(R.string.plan_add_day))
            }
        }

        current.pasted?.let { pasted -> PasteNotes(pasted) }
    }

    if (pickingStart) {
        DatePickerDialog(
            initial = current.start,
            onDismiss = { pickingStart = false },
            onPick = { date ->
                viewModel.update { it.copy(start = date) }
                pickingStart = false
            },
        )
    }
    timeFor?.let { key ->
        val task = current.tasks.firstOrNull { it.key == key }
        TimePickerDialog(
            initial = task?.times?.firstOrNull() ?: LocalTime.of(9, 0),
            onDismiss = { timeFor = null },
            onPick = { time ->
                viewModel.update { d -> d.copy(tasks = d.tasks.map { if (it.key == key) it.copy(times = listOf(time)) else it }) }
                timeFor = null
            },
        )
    }
}

/** A task's title, its time (tap to set), and × to take it out. */
@Composable
private fun TaskLine(task: PlanTaskDraft, onTitle: (String) -> Unit, onTime: () -> Unit, onRemove: () -> Unit) {
    val removeDescription = stringResource(R.string.plan_remove_task)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = task.title,
            onValueChange = onTitle,
            placeholder = { Text(stringResource(R.string.plan_task_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onTime) {
            Text(task.times.firstOrNull()?.let { formatTime(it) } ?: stringResource(R.string.editor_time))
        }
        TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = removeDescription }) { Text("×") }
    }
}

@Composable
private fun ColorSwatch(color: PlanColor, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(
        when (color) {
            PlanColor.TEAL -> R.string.plan_colour_teal
            PlanColor.VIOLET -> R.string.plan_colour_violet
            PlanColor.CORAL -> R.string.plan_colour_coral
            PlanColor.AMBER -> R.string.plan_colour_amber
        },
    )
    val ring = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background
    Box(
        Modifier
            .size(36.dp)
            .border(2.dp, ring, CircleShape)
            .padding(5.dp)
            .background(planColor(color), CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
    )
}

/** Type or paste a plan, or copy the prompt that asks an AI for one; Read it fills in the plan. */
@Composable
private fun PasteBox(onRead: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var promptCopied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val promptLabel = stringResource(R.string.add_list_copy_prompt)
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_PASTED_CHARS) },
            placeholder = { Text(stringResource(R.string.add_list_hint), fontFamily = FontFamily.Monospace) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            minLines = 8,
            maxLines = 16,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(if (promptCopied) R.string.add_list_copied else R.string.add_list_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                scope.launch {
                    val clip = clipboard.getClipEntry()?.clipData
                    val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrBlank()) text = (if (text.isBlank()) pasted else text.trimEnd() + "\n" + pasted).take(MAX_PASTED_CHARS)
                }
            }) { Text(stringResource(R.string.add_list_paste)) }
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(promptLabel, pastePrompt(LocalDate.now()))))
                    promptCopied = true
                }
            }) { Text(stringResource(R.string.add_list_copy_prompt)) }
        }
        TextButton(onClick = { onRead(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.plan_read)) }
    }
}

/** What couldn't be read, and what a plan leaves out of what was pasted. */
@Composable
private fun PasteNotes(pasted: PastedList) {
    val habits = pasted.items.filter { it.isHabit }
    val repeats = pasted.items.filter { !it.isHabit && it.repeat != null }
    if (pasted.problems.isEmpty() && habits.isEmpty() && repeats.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(stringResource(R.string.paste_check), Modifier.padding(bottom = 4.dp))
        habits.forEach { Note(stringResource(R.string.plan_left_out_habit, it.line, it.title)) }
        repeats.forEach { Note(stringResource(R.string.plan_repeat_once, it.line, it.title)) }
        pasted.problems.forEach { Note(problemText(it)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = stillPalette.warning)
}

@Composable
private fun problemText(problem: PasteProblem): String = when (problem.kind) {
    PasteProblem.Kind.UNKNOWN_TAG -> stringResource(R.string.paste_problem_unknown_tag, problem.line, problem.text)
    PasteProblem.Kind.UNKNOWN_REPEAT -> stringResource(R.string.paste_problem_repeat, problem.line, problem.text)
    PasteProblem.Kind.UNKNOWN_UNTIL -> stringResource(R.string.paste_problem_until, problem.line, problem.text)
    PasteProblem.Kind.HABIT_ONLY -> stringResource(R.string.paste_problem_habit_only, problem.line, problem.text)
    PasteProblem.Kind.TIMES_WITHOUT_DAYS -> stringResource(R.string.paste_problem_times, problem.line)
    PasteProblem.Kind.TOO_MANY -> pluralStringResource(R.plurals.paste_problem_too_many, MAX_PASTED_ITEMS, MAX_PASTED_ITEMS)
}
