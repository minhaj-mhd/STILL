package app.still.launcher.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.HistoryRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.PracticeRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.data.ScreenTimeSummary
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Task
import app.still.launcher.history.AppDay
import app.still.launcher.history.DayLimit
import app.still.launcher.history.HistoryDay
import app.still.launcher.history.PeriodKind
import app.still.launcher.history.PeriodUsage
import app.still.launcher.history.SteppedIn
import app.still.launcher.history.UsageHistory
import app.still.launcher.history.WeekAverage
import app.still.launcher.history.focusModeTime
import app.still.launcher.history.periodDays
import app.still.launcher.history.periodUsage
import app.still.launcher.history.steppedIn
import app.still.launcher.history.weekAverages
import app.still.launcher.history.weekStart
import app.still.launcher.keynote.qualityDays
import app.still.launcher.limit.dayStart
import app.still.launcher.limit.limitDay
import app.still.launcher.productivity.HabitStats
import app.still.launcher.productivity.habitStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/** A habit and its current streak, for the Insights page. */
data class HabitStreak(val habit: Habit, val stats: HabitStats)

/** Duration habits finished: by the focus timer, and logged by hand. */
data class FocusStats(val byTimer: Int, val byHand: Int)

/** A week or month as Insights shows it. */
data class PeriodView(
    val kind: PeriodKind,
    /** 0 for the current week or month, -1 for the one before, and so on. */
    val offset: Int,
    val from: LocalDate,
    val to: LocalDate,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    /** The hour days start at, as for the daily limit. */
    val dayStartHour: Int,
    /** Whether anything is saved from before this period. */
    val canGoBack: Boolean,
    val usage: PeriodUsage,
    /** For a month, its weeks. */
    val weeks: List<WeekAverage>,
    val steppedIn: SteppedIn,
    val focusMode: Duration,
    val focusSessions: Int,
    /** Tasks and habits finished. */
    val finished: Int,
    /** Null when no duration habit was finished. */
    val focusTimer: FocusStats?,
    val qualities: List<Pair<String, Int>>,
) {
    val isCurrent: Boolean get() = offset == 0
}

class InsightsViewModel(
    private val screenTimeRepository: ScreenTimeRepository,
    private val productivity: ProductivityRepository,
    private val practice: PracticeRepository,
    private val history: HistoryRepository,
    private val usageHistory: UsageHistory,
    private val preferences: LauncherPreferences,
) : ViewModel() {

    /** Today's date, where days start, and today so far; set each time the page opens. */
    private data class Now(val today: LocalDate, val dayStartHour: Int, val live: HistoryDay?)

    private val now = MutableStateFlow<Now?>(null)
    private val choice = MutableStateFlow(PeriodKind.WEEK to 0)

    /** The apps that can be opened from STILL; system screens aren't shown. Null until known. */
    private val shownApps = MutableStateFlow<Set<String>?>(null)

    private val _screenTime = MutableStateFlow<ScreenTimeSummary?>(null)

    /** Null until loaded, and while Usage access is off. */
    val screenTime: StateFlow<ScreenTimeSummary?> = _screenTime

    /** The week or month chosen; null until loaded. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val period: StateFlow<PeriodView?> = combine(choice, now.filterNotNull(), ::Pair)
        .flatMapLatest { (choice, now) -> periodView(choice.first, choice.second, now) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Habits with a running streak, longest first. Records cover a year, so longer streaks show as a year. */
    val streaks: StateFlow<List<HabitStreak>> = combine(
        productivity.habits,
        productivity.records(LocalDate.now().minusDays(STREAK_LOOKBACK_DAYS), LocalDate.now()),
    ) { habits, records ->
        val today = LocalDate.now()
        val byHabit = records.filter { it.itemType == ItemType.HABIT }.groupBy { it.itemId }
        habits
            .map { habit -> HabitStreak(habit, habitStats(habit, byHabit[habit.id].orEmpty(), today)) }
            .filter { it.stats.currentStreak > 0 }
            .sortedByDescending { it.stats.currentStreak }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hasUsageAccess(): Boolean = screenTimeRepository.hasAccess()

    fun setShownApps(packages: Set<String>) {
        shownApps.value = packages
    }

    /** Shows the current week or month, unless that kind is showing already. */
    fun show(kind: PeriodKind) {
        if (choice.value.first != kind) choice.value = kind to 0
    }

    /** Goes back ([by] below 0) or forward, never past the current week or month. */
    fun move(by: Int) {
        choice.update { (kind, offset) -> kind to (offset + by).coerceAtMost(0) }
    }

    /** Reads screen time again; called when the page opens and when STILL comes back to the front. */
    fun refresh() {
        viewModelScope.launch { _screenTime.value = screenTimeRepository.summary() }
        viewModelScope.launch {
            // Days finished since STILL last looked are saved first, so they show.
            usageHistory.catchUp()
            val dayStartHour = preferences.current().limit.dayStartHour
            val time = ZonedDateTime.now()
            now.value = Now(limitDay(time.toLocalDateTime(), dayStartHour), dayStartHour, usageHistory.today(time))
        }
    }

    private fun periodView(kind: PeriodKind, offset: Int, now: Now): Flow<PeriodView> {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val range = periodDays(kind, offset, now.today, firstDayOfWeek)
        val before = periodDays(kind, offset - 1, now.today, firstDayOfWeek)
        // A month's first and last weeks reach into the months either side.
        val loadFrom = minOf(before.start, weekStart(range.start, firstDayOfWeek))
        val loadTo = range.endInclusive.plusDays(DAYS_IN_WEEK - 1)
        val zone = ZoneId.systemDefault()
        val from = dayStart(range.start, now.dayStartHour).atZone(zone).toInstant()
        val to = dayStart(range.endInclusive.plusDays(1), now.dayStartHour).atZone(zone).toInstant()
        return combine(
            combine(history.days(loadFrom, loadTo), history.apps(loadFrom, loadTo), ::Pair),
            combine(history.interventions(from, to), practice.choices(from, to), history.focusPeriods(from, to), ::Triple),
            combine(productivity.tasks, productivity.habits, productivity.records(range.start, range.endInclusive), ::Triple),
            practice.keynotes(range.start, range.endInclusive),
            combine(shownApps.filterNotNull(), history.firstDay, ::Pair),
        ) { (dayRecords, appRecords), (interventions, choices, focusPeriods), (tasks, habits, records), keynotes, (shown, firstDay) ->
            val appsByDay = appRecords.groupBy { it.day }
            val saved = dayRecords.filter { it.day != now.today }.map { record ->
                HistoryDay(
                    day = record.day,
                    apps = appsByDay[record.day].orEmpty().associate { it.packageName to AppDay(it.millis, it.opens) },
                    unlocks = record.unlocks,
                    hourMillis = record.hourMillis,
                    limit = DayLimit(record.countedMillis, record.limitMillis),
                )
            }
            val days = saved + listOfNotNull(now.live)
            val isShown: (String) -> Boolean = { it in shown }
            val (focusMode, focusSessions) = focusModeTime(focusPeriods, from, to)
            PeriodView(
                kind = kind,
                offset = offset,
                from = range.start,
                to = range.endInclusive,
                today = now.today,
                firstDayOfWeek = firstDayOfWeek,
                dayStartHour = now.dayStartHour,
                canGoBack = firstDay != null && firstDay <= before.endInclusive,
                usage = periodUsage(range.start, range.endInclusive, days, days.filter { it.day in before }, now.today, now.dayStartHour, isShown),
                weeks = if (kind == PeriodKind.MONTH) weekAverages(range.start, range.endInclusive, days, now.today, firstDayOfWeek, isShown) else emptyList(),
                steppedIn = steppedIn(interventions.map { it.packageName to it.type }, choices.map { it.packageName to it.turnedBack }),
                focusMode = focusMode,
                focusSessions = focusSessions,
                finished = finishedCount(tasks, habits, records, range, zone),
                focusTimer = focusTimerStats(habits, records),
                qualities = qualityDays(keynotes.map { it.quality }),
            )
        }
    }

    companion object {
        private const val STREAK_LOOKBACK_DAYS = 366L
        private const val DAYS_IN_WEEK = 7L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                InsightsViewModel(
                    container.screenTimeRepository,
                    container.productivityRepository,
                    container.practiceRepository,
                    container.historyRepository,
                    container.usageHistory,
                    container.preferences,
                )
            }
        }
    }
}

/** Repeating tasks and habits met in [records], and one-time tasks completed within [days]. */
private fun finishedCount(tasks: List<Task>, habits: List<Habit>, records: List<OccurrenceRecord>, days: ClosedRange<LocalDate>, zone: ZoneId): Int {
    val habitsById = habits.associateBy { it.id }
    val repeating = records.count { record ->
        !record.skipped && when (record.itemType) {
            ItemType.TASK -> record.value >= 1.0
            ItemType.HABIT -> habitsById[record.itemId]?.let { record.value >= it.target } == true
        }
    }
    // One-time and undated tasks keep their completion on the task itself.
    val oneTime = tasks.count { task ->
        task.parentId == null && task.schedule?.isRecurring != true &&
            task.completedAt?.atZone(zone)?.toLocalDate()?.let { it in days } == true
    }
    return repeating + oneTime
}

/** Duration habits finished in [records], by the focus timer and by hand; null without any. */
private fun focusTimerStats(habits: List<Habit>, records: List<OccurrenceRecord>): FocusStats? {
    val durationHabits = habits.filter { it.measure == HabitMeasure.DURATION }.associateBy { it.id }
    val finished = records.filter { record ->
        record.itemType == ItemType.HABIT && !record.skipped &&
            durationHabits[record.itemId]?.let { record.value >= it.target } == true
    }
    return FocusStats(byTimer = finished.count { it.viaTimer }, byHand = finished.count { !it.viaTimer }).takeIf { finished.isNotEmpty() }
}
