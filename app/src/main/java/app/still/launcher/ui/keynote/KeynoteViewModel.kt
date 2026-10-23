package app.still.launcher.ui.keynote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.PracticeRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.practice.Choice
import app.still.launcher.data.practice.Keynote
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Task
import app.still.launcher.keynote.Completion
import app.still.launcher.keynote.EVENING_HOUR
import app.still.launcher.keynote.MORNING_FROM_HOUR
import app.still.launcher.keynote.Moment
import app.still.launcher.keynote.UnlockMonitor
import app.still.launcher.keynote.UnlockObservation
import app.still.launcher.keynote.practiceMoments
import app.still.launcher.limit.DailyLimitMonitor
import app.still.launcher.limit.limitDay
import app.still.launcher.schedule.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Inner practice on home: today's quality, what counted as practising it, and today's unlocks. */
data class KeynoteState(
    /** The daily quality is switched on. */
    val enabled: Boolean = false,
    val keynote: Keynote? = null,
    /** The morning card is due: on, nothing chosen today, past [MORNING_FROM_HOUR], and not put off today. */
    val asking: Boolean = false,
    /** The last quality chosen before today, to start from. */
    val previous: Keynote? = null,
    /** The Today item it's practised with, while it exists. */
    val linkedTitle: String? = null,
    val moments: List<Moment> = emptyList(),
    /** Null when unlocks on home are off, or without Usage access. */
    val unlocks: UnlockObservation? = null,
    /** Phone time today; null when it isn't known. */
    val phoneToday: Duration? = null,
)

/** What today's quality is practised with: a Today item, a new task made from the action, or nothing. */
sealed interface KeynoteLink {
    data class Item(val type: ItemType, val id: Long) : KeynoteLink
    data object NewTask : KeynoteLink
    data object None : KeynoteLink
}

/** The minute-to-minute inputs, apart from the database. */
private data class Live(
    val time: LocalDateTime,
    val usage: Map<String, Long>,
    val unlocks: UnlockObservation?,
    val previous: Keynote?,
)

class KeynoteViewModel(
    private val practice: PracticeRepository,
    private val productivity: ProductivityRepository,
    private val preferences: LauncherPreferences,
    dailyLimit: DailyLimitMonitor,
    private val unlockMonitor: UnlockMonitor,
) : ViewModel() {

    /** The current minute, reported by the UI (see [setNow]). */
    private val now = MutableStateFlow(LocalDateTime.now())
    private val today = now.map { it.toLocalDate() }.distinctUntilChanged()
    private val unlocks = MutableStateFlow<UnlockObservation?>(null)
    private val previous = MutableStateFlow<Keynote?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<KeynoteState> = today
        .flatMapLatest { day ->
            val zone = ZoneId.systemDefault()
            combine(
                preferences.settings,
                practice.keynote(day),
                practice.choices(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()),
                combine(productivity.tasks, productivity.habits, productivity.records(day, day), ::Triple),
                combine(now, dailyLimit.appUsage, unlocks, previous, ::Live),
            ) { settings, keynote, choices, (tasks, habits, records), live ->
                build(day, zone, settings, keynote, choices, tasks, habits, records, live)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeynoteState())

    /** The UI reports the time every minute; unlocks are counted again then. */
    fun setNow(time: LocalDateTime) {
        val day = now.value.toLocalDate()
        now.value = time.withSecond(0).withNano(0)
        viewModelScope.launch {
            val settings = preferences.current()
            if (previous.value == null || day != time.toLocalDate()) previous.value = practice.latestKeynoteBefore(time.toLocalDate())
            unlocks.value = if (settings.unlocksOnHome) unlockMonitor.observe(ZonedDateTime.now(), settings.limit.dayStartHour) else null
        }
    }

    /** Sets today's [quality] and [action], practised with [link]. A new task is due today. */
    fun startDay(quality: String, action: String, link: KeynoteLink) {
        val day = now.value.toLocalDate()
        viewModelScope.launch {
            val item: Pair<ItemType, Long>? = when (link) {
                is KeynoteLink.Item -> link.type to link.id
                KeynoteLink.NewTask -> ItemType.TASK to productivity.addTask(Task(title = action.trim(), schedule = Schedule(start = day)))
                KeynoteLink.None -> null
            }
            practice.saveKeynote(Keynote(day, quality.trim(), action.trim(), item?.first, item?.second))
        }
    }

    /** "Not today": the morning card doesn't ask again until tomorrow. */
    fun putOff() {
        val day = now.value.toLocalDate()
        viewModelScope.launch { preferences.setKeynotePutOff(day) }
    }

    private fun build(
        day: LocalDate,
        zone: ZoneId,
        settings: LauncherSettings,
        keynote: Keynote?,
        choices: List<Choice>,
        tasks: List<Task>,
        habits: List<Habit>,
        records: List<OccurrenceRecord>,
        live: Live,
    ): KeynoteState {
        if (!settings.dailyQuality) return KeynoteState(unlocks = live.unlocks.takeIf { settings.unlocksOnHome })
        val linked = keynote?.let { practisedWith(it, tasks, habits, records, day, zone) }
        val focused = records
            .filter { it.itemType == ItemType.HABIT && it.viaTimer && it.value > 0 && it.date == day }
            // Finishing the linked habit with the timer is one act, already marked as practised.
            .filterNot { keynote?.itemType == ItemType.HABIT && keynote.itemId == it.itemId }
            .mapNotNull { record ->
                habits.firstOrNull { it.id == record.itemId }?.let { Completion(it.title, record.updatedAt.toEpochMilli(), record.value.toInt()) }
            }
        // Within its budget only counts once the evening comes, and only when time is being read.
        val limitDay = limitDay(live.time, settings.limit.dayStartHour)
        val within = if (live.time.hour < EVENING_HOUR || live.usage.isEmpty()) {
            emptyList()
        } else {
            settings.appLimits.mapNotNull { (packageName, entry) ->
                val daily = entry.limitOn(limitDay).dailyMinutes
                if (daily > 0 && (live.usage[packageName] ?: 0L) < daily * MILLIS_PER_MINUTE) packageName to daily else null
            }
        }
        return KeynoteState(
            enabled = true,
            keynote = keynote,
            asking = keynote == null && settings.keynotePutOffOn != day && live.time.hour >= MORNING_FROM_HOUR,
            previous = live.previous,
            linkedTitle = linked?.first,
            moments = if (keynote == null) {
                emptyList()
            } else {
                practiceMoments(
                    turnedBack = choices.filter { it.turnedBack }.map { it.packageName to it.at.toEpochMilli() },
                    practised = linked?.second,
                    focused = focused,
                    withinLimits = within,
                )
            },
            unlocks = live.unlocks.takeIf { settings.unlocksOnHome },
            phoneToday = live.usage.takeIf { it.isNotEmpty() }?.let { usage -> Duration.ofMillis(usage.values.sum()) },
        )
    }

    /** The linked item's title, and when it was finished today if it was. */
    private fun practisedWith(
        keynote: Keynote,
        tasks: List<Task>,
        habits: List<Habit>,
        records: List<OccurrenceRecord>,
        day: LocalDate,
        zone: ZoneId,
    ): Pair<String, Completion?>? {
        val id = keynote.itemId ?: return null
        return when (keynote.itemType) {
            ItemType.TASK -> {
                val task = tasks.firstOrNull { it.id == id } ?: return null
                val doneAt = task.completedAt?.takeIf { it.atZone(zone).toLocalDate() == day }
                    ?: records.firstOrNull { it.itemType == ItemType.TASK && it.itemId == id && it.date == day && it.value >= 1.0 && !it.skipped }?.updatedAt
                task.title to doneAt?.let { Completion(task.title, it.toEpochMilli()) }
            }
            ItemType.HABIT -> {
                val habit = habits.firstOrNull { it.id == id } ?: return null
                val doneAt = records
                    .filter { it.itemType == ItemType.HABIT && it.itemId == id && it.date == day && it.value >= habit.target && !it.skipped }
                    .maxOfOrNull { it.updatedAt }
                habit.title to doneAt?.let { Completion(habit.title, it.toEpochMilli()) }
            }
            null -> null
        }
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                KeynoteViewModel(
                    container.practiceRepository,
                    container.productivityRepository,
                    container.preferences,
                    container.dailyLimit,
                    container.unlockMonitor,
                )
            }
        }
    }
}
