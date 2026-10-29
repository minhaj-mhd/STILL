package app.still.launcher.ui.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.productivity.PlanProgress
import app.still.launcher.productivity.PlanState
import app.still.launcher.ui.components.AddButton
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.theme.planColor
import app.still.launcher.ui.theme.stillPalette

/**
 * Left page, Plans tab: plans going now (and ones still to start), paused, and finished, each
 * with how far along it is. Tap a plan to open its page; + starts a new one.
 */
@Composable
fun PlansPage(
    tabs: @Composable () -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshDay() }

    Box(modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 32.dp, top = 48.dp, end = 32.dp, bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val current = plans
            val going = current.orEmpty().filter { it.state == PlanState.ACTIVE || it.state == PlanState.NOT_STARTED }
            item(key = "header") {
                Column {
                    tabs()
                    if (going.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(R.plurals.plans_going, going.size, going.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (current == null) return@LazyColumn
            if (current.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.plans_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            section("going", R.string.plans_going_title, going, onOpenEditor)
            section("paused", R.string.plans_paused, current.filter { it.state == PlanState.PAUSED }, onOpenEditor)
            section("finished", R.string.plans_finished, current.filter { it.state == PlanState.FINISHED }, onOpenEditor)
        }

        AddButton(
            description = stringResource(R.string.plans_new),
            onClick = { onOpenEditor(EditorTarget.EditPlan(null)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )
    }
}

private fun LazyListScope.section(key: String, title: Int, plans: List<PlanProgress>, onOpenEditor: (EditorTarget) -> Unit) {
    if (plans.isEmpty()) return
    item(key = "$key-title") { SectionTitle(title) }
    items(plans, key = { it.plan.id }) { progress ->
        PlanRow(progress) { onOpenEditor(EditorTarget.OpenPlan(progress.plan.id)) }
    }
}

/** A plan's name and where it's at, a bar of the days done, and how it's going. */
@Composable
private fun PlanRow(progress: PlanProgress, onClick: () -> Unit) {
    val finished = progress.state == PlanState.FINISHED || progress.state == PlanState.PAUSED
    val nameColor = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanDot(progress)
            Spacer(Modifier.width(8.dp))
            Text(planName(progress), style = MaterialTheme.typography.titleMedium, color = nameColor, modifier = Modifier.weight(1f))
            Text(planPlace(progress), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress.state != PlanState.FINISHED) {
            PlanBar(progress, Modifier.padding(vertical = 6.dp))
        }
        Row {
            val (status, statusColor) = planStatus(progress)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor, modifier = Modifier.weight(1f))
            planEnd(progress)?.let { end ->
                Text(end, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The plan's colour, as a dot before its name. */
@Composable
internal fun PlanDot(progress: PlanProgress, size: Int = 8) {
    Box(
        Modifier
            .size(size.dp)
            .background(planColor(progress.plan.color), CircleShape),
    )
}

/** The share of its days done, in the plan's colour. */
@Composable
internal fun PlanBar(progress: PlanProgress, modifier: Modifier = Modifier) {
    val done = if (progress.days.isEmpty()) 0f else progress.daysDone.toFloat() / progress.days.size
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(stillPalette.faint, RoundedCornerShape(2.dp)),
    ) {
        if (done > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(done)
                    .height(4.dp)
                    .background(planColor(progress.plan.color), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
internal fun planName(progress: PlanProgress): String = progress.plan.name.ifBlank { stringResource(R.string.plan_unnamed) }

/** "Day 4 of 14", or the plan's length before it starts and once it's finished. */
@Composable
private fun planPlace(progress: PlanProgress): String {
    val current = progress.current
    return if (current != null && progress.state != PlanState.NOT_STARTED) {
        stringResource(R.string.plan_day_of, current.number, progress.length)
    } else {
        pluralStringResource(R.plurals.plan_days, progress.length, progress.length)
    }
}

/** How it's going, in the colour that says so: on track, behind, paused, to start, or finished. */
@Composable
internal fun planStatus(progress: PlanProgress): Pair<String, Color> {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    return when (progress.state) {
        PlanState.FINISHED -> {
            val finished = progress.days.maxOf { it.finishedOn!! }
            stringResource(R.string.plan_finished_on, formatShortDate(finished)) to quiet
        }
        PlanState.PAUSED -> stringResource(R.string.plan_paused_since, formatShortDate(progress.plan.pauses.last().from)) to quiet
        PlanState.NOT_STARTED -> {
            val start = progress.current?.due
            (if (start != null) stringResource(R.string.plan_starts, formatShortDate(start)) else "") to quiet
        }
        PlanState.ACTIVE -> when {
            progress.behind == 0 -> stringResource(R.string.plan_on_track) to stillPalette.accent
            progress.plan.movesAlong -> pluralStringResource(R.plurals.plan_behind, progress.behind, progress.behind) to stillPalette.warning
            else -> pluralStringResource(R.plurals.plan_overdue, progress.behind, progress.behind) to stillPalette.warning
        }
    }
}

/** "Ends Mon 9 Nov", while there's an end to come. */
@Composable
internal fun planEnd(progress: PlanProgress): String? {
    if (progress.state == PlanState.FINISHED || progress.state == PlanState.PAUSED) return null
    val end = progress.end ?: return null
    return stringResource(R.string.plan_ends, formatShortDate(end))
}
