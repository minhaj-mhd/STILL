package app.still.launcher.ui.productivity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.ui.editor.EditorTarget

/** Left page: what's due today, overdue tasks, today's habits, and tasks without a date. */
@Composable
fun TodayPage(
    onOpenEditor: (EditorTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val hasReminders by viewModel.hasReminders.collectAsStateWithLifecycle()

    var adding by rememberSaveable { mutableStateOf(false) }
    var taskMenu by remember { mutableStateOf<TaskEntry?>(null) }
    var habitMenu by remember { mutableStateOf<HabitEntry?>(null) }
    var amountFor by remember { mutableStateOf<HabitEntry?>(null) }

    fun onHabitClick(entry: HabitEntry) {
        when {
            entry.isSkipped -> viewModel.setSkipped(entry, skipped = false)
            entry.habit.measure == HabitMeasure.CHECK ->
                viewModel.setHabitProgress(entry, if (entry.isDone) 0.0 else 1.0)
            entry.habit.measure == HabitMeasure.COUNT -> viewModel.setHabitProgress(entry, entry.progress + 1)
            else -> amountFor = entry
        }
    }

    fun onTaskClick(entry: TaskEntry) {
        if (entry.isSkipped) viewModel.setSkipped(entry, skipped = false)
        else viewModel.setTaskDone(entry, done = !entry.isDone)
    }

    fun openTask(entry: TaskEntry) = onOpenEditor(EditorTarget.EditTask(entry.task.id))

    Box(modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 32.dp, top = 48.dp, end = 32.dp, bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { Header(agenda) }
            if (hasReminders) {
                item(key = "reminder-permission") { ReminderPermissionBanner(viewModel) }
            }
            agenda?.let { current ->
                if (current.isEmpty) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.today_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                // A recurring task can be both overdue and due today; list its subtasks only once.
                val subtasksShown = mutableSetOf<Long>()
                val onSubtaskClick = { subtask: Task -> viewModel.setSubtaskDone(subtask, subtask.completedAt == null) }
                taskSection(R.string.today_section_overdue, current.overdue, current, subtasksShown, ::onTaskClick, onSubtaskClick) {
                    taskMenu = it
                }
                taskSection(R.string.today_section_today, current.today, current, subtasksShown, ::onTaskClick, onSubtaskClick) {
                    taskMenu = it
                }
                if (current.habits.isNotEmpty()) {
                    item(key = "habits-title") { SectionTitle(R.string.today_section_habits) }
                    items(current.habits, key = { "habit:${it.habit.id}:${it.occurrence.time}" }) { entry ->
                        HabitRow(entry, onToggle = { onHabitClick(entry) }, onLongClick = { habitMenu = entry })
                    }
                }
                taskSection(R.string.today_section_undated, current.undated, current, subtasksShown, ::onTaskClick, onSubtaskClick) {
                    taskMenu = it
                }
            }
        }

        val addDescription = stringResource(R.string.today_add)
        FloatingActionButton(
            onClick = { adding = true },
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .semantics { contentDescription = addDescription },
        ) {
            Text(text = "+", style = MaterialTheme.typography.headlineSmall)
        }
    }

    if (adding) {
        AddItemSheet(
            onDismiss = { adding = false },
            onAddTask = { title, due, priority ->
                viewModel.addTask(title, due, priority)
                adding = false
            },
            onAddHabit = { title, measure, target, frequency ->
                viewModel.addHabit(title, measure, target, frequency)
                adding = false
            },
            onMoreOptions = { isHabit, title ->
                adding = false
                onOpenEditor(if (isHabit) EditorTarget.EditHabit(null, title) else EditorTarget.EditTask(null, title))
            },
        )
    }

    taskMenu?.let { entry ->
        TaskActionsSheet(
            entry = entry,
            onDismiss = { taskMenu = null },
            onEdit = {
                taskMenu = null
                openTask(entry)
            },
            onSetSkipped = { skipped ->
                viewModel.setSkipped(entry, skipped)
                taskMenu = null
            },
            onDelete = {
                viewModel.deleteTask(entry.task)
                taskMenu = null
            },
        )
    }

    habitMenu?.let { entry ->
        HabitActionsSheet(
            entry = entry,
            onDismiss = { habitMenu = null },
            onEdit = {
                habitMenu = null
                onOpenEditor(EditorTarget.EditHabit(entry.habit.id))
            },
            onLogAmount = {
                habitMenu = null
                amountFor = entry
            },
            onSetSkipped = { skipped ->
                viewModel.setSkipped(entry, skipped)
                habitMenu = null
            },
            onDelete = {
                viewModel.deleteHabit(entry.habit)
                habitMenu = null
            },
        )
    }

    amountFor?.let { entry ->
        AmountDialog(
            entry = entry,
            onDismiss = { amountFor = null },
            onSave = { value ->
                viewModel.setHabitProgress(entry, value)
                amountFor = null
            },
        )
    }
}

@Composable
private fun Header(agenda: TodayAgenda?) {
    Column {
        Text(
            text = stringResource(R.string.today_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (agenda != null && !agenda.isEmpty) {
            val pending = agenda.pendingCount
            Text(
                text = if (pending == 0) stringResource(R.string.today_all_done)
                else pluralStringResource(R.plurals.today_left, pending, pending),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
    )
}

private fun LazyListScope.taskSection(
    title: Int,
    entries: List<TaskEntry>,
    agenda: TodayAgenda,
    subtasksShown: MutableSet<Long>,
    onToggle: (TaskEntry) -> Unit,
    onToggleSubtask: (Task) -> Unit,
    onLongClick: (TaskEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "title:$title") { SectionTitle(title) }
    entries.forEach { entry ->
        item(key = "task:$title:${entry.task.id}:${entry.occurrence}") {
            TaskRow(
                entry = entry,
                today = agenda.date,
                onToggle = { onToggle(entry) },
                onLongClick = { onLongClick(entry) },
            )
        }
        // Subtasks stay visible under an unfinished task so they can be ticked off here.
        if (entry.isPending && subtasksShown.add(entry.task.id)) {
            items(entry.subtasks, key = { "subtask:${it.id}" }) { subtask ->
                SubtaskRow(subtask, onToggle = { onToggleSubtask(subtask) })
            }
        }
    }
}
