package app.still.launcher.ui.productivity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.productivity.PlanDay
import app.still.launcher.productivity.PlanProgress
import app.still.launcher.productivity.PlanState
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.theme.planColor
import app.still.launcher.ui.theme.stillPalette
import kotlinx.coroutines.delay

/**
 * One plan: its dates, how it's going (days done, tasks done, days on time, how far behind), a
 * strip of its days, then each day with its tasks, ticked off here as on Today. Pause, Edit and
 * Delete are at the bottom.
 */
@Composable
fun PlanPage(
    planId: Long,
    onClose: () -> Unit,
    onOpen: (EditorTarget) -> Unit,
    viewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshDay() }
    val progress = plans?.firstOrNull { it.plan.id == planId }
    // Deleted, or gone with its last task. A plan just created can take a moment to arrive.
    var seen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(plans) {
        when {
            progress != null -> seen = true
            plans != null -> {
                if (!seen) delay(1_000)
                onClose()
            }
        }
    }
    progress ?: return

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(listOfNotNull(progress.current?.number)) }

    EditorScaffold(title = planName(progress), onDone = onClose) {
        Column {
            Text(planDates(progress), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(if (progress.plan.movesAlong) R.string.plan_moves_along else R.string.plan_keeps_dates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Insights(progress)
        DayStrip(progress)

        Column {
            progress.days.forEach { day ->
                val isOpen = day.number in opened
                DayHeader(day, progress, isOpen) { opened = if (isOpen) opened - day.number else opened + day.number }
                if (isOpen) {
                    day.tasks.forEach { task ->
                        val done = task.completedAt != null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTaskDone(task, !done) }
                                .padding(start = 8.dp),
                        ) {
                            CheckCircle(checked = done, skipped = false) { viewModel.setTaskDone(task, !done) }
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                            )
                            task.schedule?.times?.minOrNull()?.let { time ->
                                Text(formatTime(time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        ChipRow {
            if (progress.state != PlanState.FINISHED) {
                if (progress.plan.isPaused) {
                    Chip(stringResource(R.string.plan_resume), selected = false) { viewModel.resume(progress.plan) }
                } else {
                    Chip(stringResource(R.string.plan_pause), selected = false) { viewModel.pause(progress.plan) }
                }
            }
            Chip(stringResource(R.string.plan_edit), selected = false) { onOpen(EditorTarget.EditPlan(planId)) }
            Chip(stringResource(R.string.plan_delete), selected = false) { confirmDelete = true }
        }
        if (progress.plan.isPaused) {
            Text(stringResource(R.string.plan_paused_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            name = planName(progress),
            detail = pluralStringResource(R.plurals.plan_delete_detail, progress.taskCount, progress.taskCount),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete(progress.plan)
                onClose()
            },
        )
    }
}

/** "Mon 26 Oct – Mon 9 Nov · 14 days · was Sun 8 Nov". */
@Composable
private fun planDates(progress: PlanProgress): String {
    val first = progress.days.firstOrNull()
    val start = first?.finishedOn?.takeIf { first.due == null || it < first.due } ?: first?.due
    val parts = listOfNotNull(
        start?.let { from -> progress.end?.let { to -> stringResource(R.string.plan_dates, formatShortDate(from), formatShortDate(to)) } },
        pluralStringResource(R.plurals.plan_days, progress.length, progress.length),
        progress.plannedEnd?.takeIf { progress.end != null && it != progress.end && progress.state != PlanState.FINISHED }
            ?.let { stringResource(R.string.plan_was, formatShortDate(it)) },
    )
    return parts.joinToString(" · ")
}

/** A ring of the days done, with tasks done, days on time and how it's going beside it. */
@Composable
private fun Insights(progress: PlanProgress) {
    val color = planColor(progress.plan.color)
    val track = stillPalette.faint
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Canvas(Modifier.size(64.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
                val share = if (progress.days.isEmpty()) 0f else progress.daysDone.toFloat() / progress.days.size
                if (share > 0f) {
                    drawArc(color, -90f, 360f * share, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
                }
            }
            Text("${progress.daysDone}/${progress.days.size}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.plan_tasks_done, progress.tasksDone, progress.taskCount), style = MaterialTheme.typography.bodyMedium)
            if (progress.daysDone > 0) {
                Text(stringResource(R.string.plan_days_on_time, progress.daysOnTime, progress.daysDone), style = MaterialTheme.typography.bodyMedium)
            }
            val (status, statusColor) = planStatus(progress)
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }
    }
}

/** One square per day: on time in the plan's colour, late in amber, missed in red, today outlined, the rest unlit. */
@Composable
private fun DayStrip(progress: PlanProgress) {
    val color = planColor(progress.plan.color)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            progress.days.forEach { day ->
                val shape = RoundedCornerShape(3.dp)
                val modifier = Modifier.size(14.dp)
                val fill: Color? = when {
                    day.isFinished && day.isLate -> stillPalette.warning
                    day.isFinished -> color
                    day.isMissed(progress) -> stillPalette.danger
                    day.due == progress.today -> null
                    else -> stillPalette.faint
                }
                Box(if (fill != null) modifier.background(fill, shape) else modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground, shape))
            }
        }
        Text(stringResource(R.string.plan_strip_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A day of a plan that keeps its dates, gone by without being finished. */
private fun PlanDay.isMissed(progress: PlanProgress): Boolean = !isFinished && due != null && due < progress.today

/** "Day 4 · Today", "Day 3 · Wed 28 · done Thu 29", "Day 5 · Fri 30", with how many tasks are done. */
@Composable
private fun DayHeader(day: PlanDay, progress: PlanProgress, isOpen: Boolean, onClick: () -> Unit) {
    val date = when {
        day.due == progress.today && !day.isFinished -> stringResource(R.string.today_title)
        day.due != null -> formatShortDate(day.due)
        else -> null
    }
    val done = day.finishedOn?.takeIf { it != day.due }?.let { stringResource(R.string.plan_done_on, formatShortDate(it)) }
    val current = day == progress.current && day.due != null && day.due <= progress.today
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            listOfNotNull(stringResource(R.string.plan_day, day.number), date, done).joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                day.isMissed(progress) -> stillPalette.danger
                current || isOpen -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.plan_day_count, day.tasks.count { it.completedAt != null }, day.tasks.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
