package app.still.launcher.ui.insights

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.history.HOURS
import app.still.launcher.history.LEVELS
import app.still.launcher.history.PeriodKind
import app.still.launcher.history.level
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The locale the page is shown in, followed when it changes. */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** How many apps a week or month lists. */
private const val PERIOD_APPS = 5

/** Under a minute apart counts as the same. */
private const val SAME_MILLIS = 60_000L

/**
 * A week or month: its time on an average day against the one before, each day, the apps, the
 * hours the phone is used, what STILL stepped in on, and focus and plans. Lines with nothing to
 * say are left out.
 */
@Composable
internal fun PeriodSections(view: PeriodView, labels: Map<String, String>, onEarlier: () -> Unit, onLater: () -> Unit) {
    val locale = currentLocale()
    val before = when {
        view.kind == PeriodKind.MONTH -> view.from.minusMonths(1).month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        view.isCurrent -> stringResource(R.string.insights_than_last_week)
        else -> stringResource(R.string.insights_than_week_before)
    }
    val usage = view.usage

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PeriodSwitcher(view, onEarlier, onLater)
        if (!usage.hasDays) {
            Text(
                text = stringResource(R.string.insights_nothing_saved),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        usage.perDayMillis?.let { perDay ->
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration(Duration.ofMillis(perDay)), style = MaterialTheme.typography.displaySmall, modifier = Modifier.alignByBaseline())
                Text(
                    text = stringResource(R.string.insights_a_day),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        val total = formatDuration(Duration.ofMillis(usage.totalMillis))
        val compared = comparedText(usage.perDayMillis, usage.perDayBeforeMillis, before)
        Text(
            text = listOfNotNull(stringResource(if (view.isCurrent) R.string.insights_so_far else R.string.insights_in_all, total), compared).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (view.kind == PeriodKind.WEEK) WeekBars(view) else MonthCalendar(view)
    }
    if (!usage.hasDays) return

    AppsSection(view, labels, before)
    HoursSection(usage.hourMillis, view.dayStartHour)
    if (view.weeks.isNotEmpty()) WeeksSection(view)
    SteppedInSection(view, labels)
    FocusSection(view, before)
}

/** "‹  This week · Nov 2 – 8  ›": going back stops where the saved days begin, forward at today. */
@Composable
private fun PeriodSwitcher(view: PeriodView, onEarlier: () -> Unit, onLater: () -> Unit) {
    val locale = currentLocale()
    val label = when (view.kind) {
        PeriodKind.MONTH -> view.from.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        PeriodKind.WEEK -> {
            val dayMonth = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
            val end = if (view.to.month == view.from.month) view.to.dayOfMonth.toString() else view.to.format(dayMonth)
            val days = "${view.from.format(dayMonth)} – $end"
            when (view.offset) {
                0 -> stringResource(R.string.insights_this_week, days)
                -1 -> stringResource(R.string.insights_last_week, days)
                else -> days
            }
        }
    }
    val earlier = stringResource(R.string.insights_earlier)
    val later = stringResource(R.string.insights_later)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onEarlier, enabled = view.canGoBack, modifier = Modifier.semantics { contentDescription = earlier }) { Text("‹") }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        TextButton(onClick = onLater, enabled = !view.isCurrent, modifier = Modifier.semantics { contentDescription = later }) { Text("›") }
    }
}

/** "21m less a day than last week", or null when there's nothing to compare with. */
@Composable
private fun comparedText(now: Long?, before: Long?, beforeName: String): String? {
    if (now == null || before == null) return null
    val difference = formatDuration(Duration.ofMillis(kotlin.math.abs(now - before)))
    return when {
        kotlin.math.abs(now - before) < SAME_MILLIS -> stringResource(R.string.insights_same_a_day, beforeName)
        now < before -> stringResource(R.string.insights_less_a_day, difference, beforeName)
        else -> stringResource(R.string.insights_more_a_day, difference, beforeName)
    }
}

/** A bar for each day of the week, today's paler while it's still going, under the daily limit's line. */
@Composable
private fun WeekBars(view: PeriodView) {
    val usage = view.usage
    val palette = stillPalette
    val limit = usage.limitMillis
    val top = maxOf(usage.dayMillis.maxOf { it ?: 0L }, (limit ?: 0L) * 11 / 10, 1L)
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        limit?.let {
            Text(
                text = stringResource(R.string.insights_limit_line, formatDuration(Duration.ofMillis(it))),
                style = MaterialTheme.typography.labelSmall,
                color = palette.warning,
                modifier = Modifier.align(Alignment.End),
            )
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val count = usage.dayMillis.size
            val gap = 10.dp.toPx()
            val width = (size.width - gap * (count - 1)) / count
            usage.dayMillis.forEachIndexed { index, millis ->
                if (millis == null || millis <= 0) return@forEachIndexed
                val height = size.height * millis / top
                val isToday = view.from.plusDays(index.toLong()) == view.today
                drawRoundRect(
                    color = if (isToday) palette.accent.copy(alpha = 0.5f) else palette.accent,
                    topLeft = Offset(index * (width + gap), size.height - height),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            limit?.let {
                val y = size.height - size.height * it / top
                drawLine(
                    color = palette.warning,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            usage.dayMillis.indices.forEach { index ->
                val day = view.from.plusDays(index.toLong())
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day == view.today) FontWeight.Bold else null,
                    color = if (day == view.today) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The month's days shaded by their time, with a dot on days over the daily limit. */
@Composable
private fun MonthCalendar(view: PeriodView) {
    val usage = view.usage
    val palette = stillPalette
    val locale = currentLocale()
    val busiest = usage.dayMillis.maxOf { it ?: 0L }
    val lead = (view.from.dayOfWeek.value - view.firstDayOfWeek.value + 7) % 7
    val cells: List<Int?> = List(lead) { null } + usage.dayMillis.indices
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(7) {
                Text(
                    text = view.firstDayOfWeek.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { column ->
                    val index = week.getOrNull(column)
                    if (index == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val shade = level(usage.dayMillis[index] ?: 0L, busiest)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(levelColor(shade), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = view.from.plusDays(index.toLong()).dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (shade >= LEVELS - 1) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (usage.overLimit[index]) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 3.dp)
                                        .size(5.dp)
                                        .background(palette.warning, RoundedCornerShape(50)),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.insights_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (1..LEVELS).forEach { Box(Modifier.padding(horizontal = 2.dp).size(10.dp).background(levelColor(it), RoundedCornerShape(2.dp))) }
            Text(stringResource(R.string.insights_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (usage.overLimitDays > 0) {
                Box(Modifier.padding(end = 6.dp).size(5.dp).background(palette.warning, RoundedCornerShape(50)))
                Text(
                    text = pluralStringResource(R.plurals.insights_over_limit_days, usage.overLimitDays, usage.overLimitDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The accent, paler for less: 0 is an empty cell. */
@Composable
private fun levelColor(level: Int): Color = when (level) {
    0 -> stillPalette.faint
    else -> stillPalette.accent.copy(alpha = LEVEL_ALPHAS[level - 1])
}

private val LEVEL_ALPHAS = listOf(0.25f, 0.45f, 0.7f, 1f)

/** A week's apps with their time and opens; a month's with their time a day against the month before. */
@Composable
private fun AppsSection(view: PeriodView, labels: Map<String, String>, before: String) {
    val apps = view.usage.apps.filter { it.packageName in labels }.take(PERIOD_APPS)
    if (apps.isEmpty()) return
    val isMonth = view.kind == PeriodKind.MONTH
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldLabel(stringResource(if (isMonth) R.string.insights_apps_a_day else R.string.insights_apps))
        val longest = apps.maxOf { if (isMonth) it.perDayMillis else it.millis }.coerceAtLeast(1)
        apps.forEach { app ->
            val opens = pluralStringResource(R.plurals.insights_opens, app.opens, app.opens)
            if (isMonth) {
                val compared = app.perDayBeforeMillis?.let { appCompared(app.perDayMillis, it, before) }
                AppUsageRow(labels.getValue(app.packageName), formatDuration(Duration.ofMillis(app.perDayMillis)), compared ?: opens, app.perDayMillis.toFloat() / longest)
            } else {
                AppUsageRow(labels.getValue(app.packageName), formatDuration(Duration.ofMillis(app.millis)), opens, app.millis.toFloat() / longest)
            }
        }
    }
}

/** "12m less than September". */
@Composable
private fun appCompared(now: Long, before: Long, beforeName: String): String {
    val difference = formatDuration(Duration.ofMillis(kotlin.math.abs(now - before)))
    return when {
        kotlin.math.abs(now - before) < SAME_MILLIS -> stringResource(R.string.insights_app_same, beforeName)
        now < before -> stringResource(R.string.insights_app_less, difference, beforeName)
        else -> stringResource(R.string.insights_app_more, difference, beforeName)
    }
}

/** When in the day the phone is used: an hour to a cell, from [startHour], when days start. */
@Composable
private fun HoursSection(hourMillis: List<Long>, startHour: Int) {
    if (hourMillis.all { it == 0L }) return
    val locale = currentLocale()
    val hourFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "j"), locale) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_when, LocalTime.of(startHour, 0).format(hourFormat)))
        val busiest = hourMillis.max()
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hourMillis.forEach { millis ->
                Box(Modifier.weight(1f).height(22.dp).background(levelColor(level(millis, busiest)), RoundedCornerShape(2.dp)))
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            listOf(0, 8, 16, HOURS - 1).forEach {
                Text(
                    text = LocalTime.of((startHour + it) % HOURS, 0).format(hourFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Each week of a month with its time on an average day. */
@Composable
private fun WeeksSection(view: PeriodView) {
    val locale = currentLocale()
    val dayMonth = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_weeks))
        view.weeks.forEach { week ->
            val end = week.start.plusDays(6)
            val endText = if (end.month == week.start.month) end.dayOfMonth.toString() else end.format(dayMonth)
            Row {
                Text(
                    text = "${week.start.format(dayMonth)} – $endText",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.insights_per_day, formatDuration(Duration.ofMillis(week.perDayMillis))), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SteppedInSection(view: PeriodView, labels: Map<String, String>) {
    val stepped = view.steppedIn
    val overLimitDays = view.usage.overLimitDays
    // A month shows its days over the limit on the calendar.
    val showOverLimit = overLimitDays > 0 && view.kind == PeriodKind.WEEK
    if (stepped.isEmpty && !showOverLimit) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_stepped_in))
        val style = MaterialTheme.typography.titleMedium
        if (stepped.paused > 0) Text(pluralStringResource(R.plurals.insights_turned_back, stepped.paused, stepped.turnedBack, stepped.paused), style = style)
        if (stepped.keptClosed > 0) Text(pluralStringResource(R.plurals.insights_kept_closed, stepped.keptClosed, stepped.keptClosed), style = style)
        if (stepped.shortVideos > 0) Text(pluralStringResource(R.plurals.insights_short_videos, stepped.shortVideos, stepped.shortVideos), style = style)
        if (stepped.ranOut > 0) Text(pluralStringResource(R.plurals.insights_ran_out, stepped.ranOut, stepped.ranOut), style = style)
        if (showOverLimit) Text(pluralStringResource(R.plurals.insights_over_limit_days, overLimitDays, overLimitDays), style = style)
        stepped.mostOften?.let { labels[it] }?.let {
            Text(stringResource(R.string.insights_most_often, it), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FocusSection(view: PeriodView, before: String) {
    val usage = view.usage
    val lines = buildList {
        if (view.focusSessions > 0) {
            add(pluralStringResource(R.plurals.insights_focus_mode, view.focusSessions, formatDuration(view.focusMode), view.focusSessions))
        }
        if (view.finished > 0) add(pluralStringResource(R.plurals.insights_finished, view.finished, view.finished))
        view.focusTimer?.let { stats ->
            add(
                listOf(
                    pluralStringResource(R.plurals.insights_focus_timer, stats.byTimer, stats.byTimer),
                    pluralStringResource(R.plurals.insights_focus_by_hand, stats.byHand, stats.byHand),
                ).joinToString(" · "),
            )
        }
        usage.unlocksPerDay?.takeIf { it > 0 }?.let { perDay ->
            val unlocks = pluralStringResource(R.plurals.insights_unlocks_per_day, perDay, perDay)
            val compared = usage.unlocksBeforePerDay?.let { previous ->
                when {
                    previous > perDay -> pluralStringResource(R.plurals.insights_unlocks_fewer, previous - perDay, previous - perDay, before)
                    previous < perDay -> pluralStringResource(R.plurals.insights_unlocks_more, perDay - previous, perDay - previous, before)
                    else -> stringResource(R.string.insights_unlocks_same, before)
                }
            }
            add(listOfNotNull(unlocks, compared).joinToString(" · "))
        }
        if (view.qualities.isNotEmpty()) {
            val days = view.qualities.map { (quality, count) -> pluralStringResource(R.plurals.insights_quality_days, count, quality, count) }
            add(stringResource(R.string.insights_qualities, days.joinToString(" · ")))
        }
    }
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.insights_focus_plans))
        lines.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}
