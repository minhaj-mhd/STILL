package app.still.launcher.ui.insights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.ScreenTimeSummary
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.editor.streakText
import java.time.Duration

private enum class Range { TODAY, WEEK }

/** How many apps the top-apps list shows. */
private const val TOP_APPS = 5

/**
 * Opened by swiping up on home: screen time and the busiest apps, today's tasks and habits,
 * and running streaks. Settings is one tap away at the top.
 */
@Composable
fun InsightsPage(
    apps: List<LauncherApp>,
    agenda: TodayAgenda?,
    /** Notifications the filter holds back; null while the filter is off. */
    heldCount: Int?,
    onOpenHeld: () -> Unit,
    onOpenSettings: () -> Unit,
    onAllowUsageAccess: () -> Unit,
    onClose: () -> Unit,
    viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory),
) {
    BackHandler(onBack = onClose)
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    LifecycleResumeEffect(Unit) {
        hasUsageAccess = viewModel.hasUsageAccess()
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val summary by viewModel.screenTime.collectAsStateWithLifecycle()
    val streaks by viewModel.streaks.collectAsStateWithLifecycle()
    val focusStats by viewModel.focusStats.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 16.dp, top = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.insights_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.insights_settings)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.editor_done)) }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 48.dp),
            ) {
                ScreenTimeSection(apps, summary, hasUsageAccess, onAllowUsageAccess)
                TodaySection(agenda)
                heldCount?.let { HeldSection(it, onOpenHeld) }
                focusStats?.let { FocusSection(it) }
                if (streaks.isNotEmpty()) StreaksSection(streaks)
            }
        }
    }
}

@Composable
private fun ScreenTimeSection(
    apps: List<LauncherApp>,
    summary: ScreenTimeSummary?,
    hasUsageAccess: Boolean,
    onAllowUsageAccess: () -> Unit,
) {
    var range by rememberSaveable { mutableStateOf(Range.TODAY) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel(stringResource(R.string.insights_screen_time))
        if (!hasUsageAccess) {
            Text(
                text = stringResource(R.string.screen_time_needs_access),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAllowUsageAccess) { Text(stringResource(R.string.screen_time_allow)) }
            return@Column
        }
        summary ?: return@Column

        ChipRow {
            Chip(stringResource(R.string.insights_range_today), range == Range.TODAY) { range = Range.TODAY }
            Chip(stringResource(R.string.insights_range_week), range == Range.WEEK) { range = Range.WEEK }
        }
        // Only apps you can open from STILL count; system screens and STILL itself don't.
        val labels = remember(apps) { apps.associate { it.component.packageName to it.label } }
        val usage = (if (range == Range.TODAY) summary.today else summary.lastSevenDays)
            .filterKeys { it in labels }
            .filterValues { !it.isZero }
        val total = usage.values.fold(Duration.ZERO, Duration::plus)

        Text(formatDuration(total), style = MaterialTheme.typography.displaySmall)
        if (range == Range.WEEK) {
            Text(
                text = stringResource(R.string.insights_daily_average, formatDuration(total.dividedBy(7))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (usage.isEmpty()) {
            Text(
                text = stringResource(R.string.insights_no_usage),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FieldLabel(stringResource(R.string.insights_top_apps), Modifier.padding(top = 8.dp))
            val top = usage.entries.sortedByDescending { it.value }.take(TOP_APPS)
            val longest = top.first().value.toMillis().coerceAtLeast(1)
            top.forEach { (packageName, time) ->
                AppUsageRow(labels.getValue(packageName), time, time.toMillis().toFloat() / longest)
            }
        }
    }
}

/** An app's name and time, with a bar showing its share of the busiest app's time. */
@Composable
private fun AppUsageRow(label: String, time: Duration, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(formatDuration(time), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun TodaySection(agenda: TodayAgenda?) {
    agenda ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.today_title))
        val tasksTotal = agenda.today.size + agenda.overdue.size
        val tasksDone = agenda.today.count { it.isDone }
        val habitsTotal = agenda.habits.size
        val habitsDone = agenda.habits.count { it.isDone }
        if (tasksTotal == 0 && habitsTotal == 0) {
            Text(stringResource(R.string.insights_nothing_due), style = MaterialTheme.typography.titleMedium)
            return@Column
        }
        if (tasksTotal > 0) {
            val overdue = agenda.overdueTaskCount
            val text = buildList {
                add(pluralStringResource(R.plurals.insights_tasks_done, tasksTotal, tasksDone, tasksTotal))
                if (overdue > 0) add(pluralStringResource(R.plurals.home_tasks_overdue, overdue, overdue))
            }.joinToString(" · ")
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
        if (habitsTotal > 0) {
            Text(
                text = pluralStringResource(R.plurals.insights_habits_done, habitsTotal, habitsDone, habitsTotal),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** "12 held back ›": how many notifications the filter is holding, and the way to them. */
@Composable
private fun HeldSection(count: Int, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_held))
        Text(
            text = pluralStringResource(R.plurals.insights_held_count, count, count) + "  ›",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

/** How many duration habits the focus timer finished this week, against those logged by hand. */
@Composable
private fun FocusSection(stats: FocusStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_focus))
        Text(pluralStringResource(R.plurals.insights_focus_timer, stats.byTimer, stats.byTimer), style = MaterialTheme.typography.titleMedium)
        Text(
            text = pluralStringResource(R.plurals.insights_focus_by_hand, stats.byHand, stats.byHand),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreaksSection(streaks: List<HabitStreak>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_streaks))
        streaks.forEach { (habit, stats) ->
            Row {
                Text(habit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    text = streakText(stats.currentStreak, stats.unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
