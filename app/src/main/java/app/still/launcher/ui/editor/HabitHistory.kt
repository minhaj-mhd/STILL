package app.still.launcher.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.productivity.DayStatus
import app.still.launcher.productivity.HabitStats
import app.still.launcher.productivity.StreakUnit
import app.still.launcher.ui.components.FieldLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Current and best streak, the 30-day rate, and a 16-week grid of days. */
@Composable
fun HabitStatsBlock(stats: HabitStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            StatNumber(stringResource(R.string.stats_current), streakText(stats.currentStreak, stats.unit))
            StatNumber(stringResource(R.string.stats_best), streakText(stats.bestStreak, stats.unit))
            stats.recentRate?.let { rate ->
                StatNumber(stringResource(R.string.stats_recent), stringResource(R.string.stats_percent, (rate * 100).roundToInt()))
            }
        }
        FieldLabel(stringResource(R.string.stats_history))
        HistoryGrid(stats.history)
    }
}

@Composable
private fun StatNumber(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "5 in a row", "3 weeks", "2 months". */
@Composable
fun streakText(count: Int, unit: StreakUnit): String = when (unit) {
    StreakUnit.OCCURRENCES -> pluralStringResource(R.plurals.streak_occurrences, count, count)
    StreakUnit.WEEKS -> pluralStringResource(R.plurals.streak_weeks, count, count)
    StreakUnit.MONTHS -> pluralStringResource(R.plurals.streak_months, count, count)
}

/** One column per week (Monday at the top), one cell per day, oldest week on the left. */
@Composable
private fun HistoryGrid(history: List<Pair<LocalDate, DayStatus>>) {
    val weeks = history
        .groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .values
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            val byDay = week.associate { (date, status) -> date.dayOfWeek to status }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DayOfWeek.entries.forEach { day -> DayCell(byDay[day]) }
            }
        }
    }
}

@Composable
private fun DayCell(status: DayStatus?) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    val modifier = Modifier.size(14.dp)
    when (status) {
        null, DayStatus.NOT_DUE -> Spacer(modifier)
        DayStatus.DONE -> Spacer(modifier.background(colors.onBackground, shape))
        DayStatus.PARTIAL -> Spacer(modifier.background(colors.onSurfaceVariant, shape))
        DayStatus.MISSED -> Spacer(modifier.background(colors.surfaceVariant, shape))
        DayStatus.SKIPPED -> Spacer(modifier.border(1.dp, colors.outline, shape))
        DayStatus.PENDING -> Spacer(modifier.border(1.dp, colors.onBackground, shape))
    }
}
