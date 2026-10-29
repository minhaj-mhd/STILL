package app.still.launcher.ui.productivity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.ItemProgress
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.productivity.TimelineItem
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.productivity.progress
import app.still.launcher.productivity.timeline
import app.still.launcher.ui.components.AddButton
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.components.formatWeekdayAndDay
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.theme.stillPalette
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Left page, Today tab, laid out as the day: late tasks and timed tasks and habits along a line
 * with the time beside each and a "now" mark, then what's due at any time today, then tasks
 * without a date. [locks] names, for each item a task gate is waiting for, the apps it keeps
 * closed.
 */
@Composable
fun TodayPage(
    tabs: @Composable () -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    modifier: Modifier = Modifier,
    locks: Map<GateItem, List<String>> = emptyMap(),
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val hasReminders by viewModel.hasReminders.collectAsStateWithLifecycle()
    val minute by viewModel.currentMinute.collectAsStateWithLifecycle()

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
    fun TaskEntry.appsLocked(): List<String> = locks[GateItem(ItemType.TASK, task.id)].orEmpty()
    fun HabitEntry.appsLocked(): List<String> = locks[GateItem(ItemType.HABIT, habit.id)].orEmpty()

    val rows = TimelineRows(
        today = agenda?.date ?: minute.toLocalDate(),
        onTaskClick = ::onTaskClick,
        onTaskMenu = { taskMenu = it },
        onSubtaskClick = { subtask -> viewModel.setSubtaskDone(subtask, subtask.completedAt == null) },
        onHabitClick = ::onHabitClick,
        onHabitMenu = { habitMenu = it },
        locks = { it.appsLocked() },
        habitLocks = { it.appsLocked() },
        focusUntil = { entry ->
            focus?.takeIf { it.isFor(entry.habit.id, entry.occurrence) }?.let { session ->
                Instant.ofEpochMilli(session.endMillis).atZone(ZoneId.systemDefault()).toLocalTime()
            }
        },
    )

    Box(modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, top = 40.dp, end = 24.dp, bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { Header(agenda, minute.toLocalDate(), tabs) }
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
                timeline(current, minute.toLocalTime(), rows)
            }
        }

        AddButton(
            description = stringResource(R.string.today_add),
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )
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
            onNewPlan = {
                adding = false
                onOpenEditor(EditorTarget.EditPlan(null))
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
            locks = entry.appsLocked(),
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
            locks = entry.appsLocked(),
            timerRunning = focus?.isFor(entry.habit.id, entry.occurrence) == true,
            onStartTimer = {
                viewModel.startTimer(entry)
                habitMenu = null
            },
            onStopTimer = {
                viewModel.stopTimer()
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

/** What the timeline's rows need from the page. */
private class TimelineRows(
    val today: LocalDate,
    val onTaskClick: (TaskEntry) -> Unit,
    val onTaskMenu: (TaskEntry) -> Unit,
    val onSubtaskClick: (Task) -> Unit,
    val onHabitClick: (HabitEntry) -> Unit,
    val onHabitMenu: (HabitEntry) -> Unit,
    val locks: (TaskEntry) -> List<String>,
    val habitLocks: (HabitEntry) -> List<String>,
    val focusUntil: (HabitEntry) -> LocalTime?,
)

/** The ring of today's progress, the day's name, and the Today and Notes tabs under it. */
@Composable
private fun Header(agenda: TodayAgenda?, date: LocalDate, tabs: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val progress = agenda?.progress.orEmpty()
        if (progress.isNotEmpty()) {
            ProgressRing(progress)
            Spacer(Modifier.width(16.dp))
        }
        Column {
            Text(formatWeekdayAndDay(agenda?.date ?: date), style = MaterialTheme.typography.headlineMedium)
            tabs()
        }
    }
}

/** Done in the accent, overdue in the warning colour, the rest unlit, with "3/8" inside. */
@Composable
private fun ProgressRing(progress: List<ItemProgress>) {
    val done = progress.count { it == ItemProgress.DONE }
    val overdue = progress.count { it == ItemProgress.OVERDUE }
    val palette = stillPalette
    val description = stringResource(R.string.today_ring, done, progress.size)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = 3.dp.toPx()
            val style = Stroke(width = stroke, cap = StrokeCap.Butt)
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(palette.faint, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            val doneSweep = 360f * done / progress.size
            val overdueSweep = 360f * overdue / progress.size
            if (done > 0) drawArc(palette.accent, -90f, doneSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            if (overdue > 0) drawArc(palette.warning, -90f + doneSweep, overdueSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        }
        Text("$done/${progress.size}", style = MaterialTheme.typography.labelLarge)
    }
}

/** Width of the times beside the line. */
private val LANE_LABEL = 52.dp
private val LANE_GAP = 8.dp

/**
 * A row beside the timeline: [label] (a time, "late" or "now") right-aligned in the margin, then
 * the line, then [content]. Without [line] the row still lines up with the timeline's rows.
 */
@Composable
private fun Lane(
    label: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    line: Boolean = true,
    content: @Composable () -> Unit,
) {
    val lineColor = stillPalette.faint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (line) {
                    Modifier.drawBehind {
                        val x = (LANE_LABEL + LANE_GAP).toPx()
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(Modifier.width(LANE_LABEL), contentAlignment = Alignment.CenterEnd) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = labelColor, textAlign = TextAlign.End, maxLines = 2)
            }
        }
        Spacer(Modifier.width(LANE_GAP + 1.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Rows that line up with the timeline when there is one, or start at the edge when there isn't. */
@Composable
private fun Aligned(withLane: Boolean, content: @Composable () -> Unit) {
    if (withLane) Lane(line = false, content = content) else content()
}

/** "now" in the margin and an accent line across. */
@Composable
private fun NowMark() {
    val accent = stillPalette.accent
    Lane(label = stringResource(R.string.today_now), labelColor = accent) {
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(accent),
        )
    }
}

private fun LazyListScope.timeline(agenda: TodayAgenda, now: LocalTime, rows: TimelineRows) {
    val timeline = agenda.timeline(now)
    val withLane = timeline.hasLine
    // A recurring task can be both late and due today; list its subtasks only once.
    val subtasksShown = mutableSetOf<Long>()

    fun task(entry: TaskEntry, key: String, lane: Boolean, late: Boolean = false) {
        item(key = key) {
            val row = @Composable {
                TaskRow(
                    entry = entry,
                    today = rows.today,
                    onToggle = { rows.onTaskClick(entry) },
                    onLongClick = { rows.onTaskMenu(entry) },
                    locks = rows.locks(entry),
                    showTime = late || !lane,
                    late = late,
                )
            }
            when {
                late -> Lane(label = stringResource(R.string.today_late), labelColor = stillPalette.warning, content = row)
                lane -> Lane(label = entry.occurrence?.time?.let { formatTime(it) }, content = row)
                else -> Aligned(withLane, row)
            }
        }
        // Subtasks stay visible under an unfinished task so they can be ticked off here.
        if (entry.isPending && subtasksShown.add(entry.task.id)) {
            items(entry.subtasks, key = { "subtask:${it.id}" }) { subtask ->
                val row = @Composable {
                    Box(Modifier.padding(start = 24.dp)) { SubtaskRow(subtask, onToggle = { rows.onSubtaskClick(subtask) }) }
                }
                if (lane) Lane(content = row) else Aligned(withLane, row)
            }
        }
    }

    fun habit(entry: HabitEntry, key: String, lane: Boolean) {
        item(key = key) {
            val row = @Composable {
                HabitRow(
                    entry = entry,
                    onToggle = { rows.onHabitClick(entry) },
                    onLongClick = { rows.onHabitMenu(entry) },
                    locks = rows.habitLocks(entry),
                    focusUntil = rows.focusUntil(entry),
                    showTime = !lane,
                )
            }
            if (lane) Lane(label = entry.occurrence.time?.let { formatTime(it) }, content = row) else Aligned(withLane, row)
        }
    }

    if (withLane) item(key = "line-top") { Lane { Spacer(Modifier.height(16.dp)) } }
    timeline.late.forEach { entry -> task(entry, "late:${entry.task.id}:${entry.occurrence}", lane = true, late = true) }
    timeline.timed.forEach { item ->
        when (item) {
            TimelineItem.Now -> item(key = "now") { NowMark() }
            is TimelineItem.OfTask -> {
                val entry = item.entry
                task(entry, "task:${entry.task.id}:${entry.occurrence}", lane = true)
            }
            is TimelineItem.OfHabit -> {
                val entry = item.entry
                habit(entry, "habit:${entry.habit.id}:${entry.occurrence.time}", lane = true)
            }
        }
    }

    if (timeline.anyTime.isNotEmpty()) {
        if (withLane) item(key = "any-time-title") { Aligned(withLane = true) { SectionTitle(R.string.today_section_any_time) } }
        timeline.anyTime.forEach { item ->
            when (item) {
                is TimelineItem.OfTask -> task(item.entry, "task:${item.entry.task.id}:${item.entry.occurrence}", lane = false)
                is TimelineItem.OfHabit -> habit(item.entry, "habit:${item.entry.habit.id}:${item.entry.occurrence.time}", lane = false)
                TimelineItem.Now -> Unit
            }
        }
    }

    if (timeline.undated.isNotEmpty()) {
        item(key = "undated-title") { Aligned(withLane) { SectionTitle(R.string.today_section_undated) } }
        timeline.undated.forEach { entry -> task(entry, "undated:${entry.task.id}", lane = false) }
    }
}

/** Small grey heading between groups of rows on the left page. */
@Composable
internal fun SectionTitle(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
    )
}
