package app.still.launcher.ui.read

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.DailyReadsRepository
import app.still.launcher.data.DriveResult
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.ReadRepository
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.BookStatus
import app.still.launcher.data.read.ReadSeries
import app.still.launcher.drive.DriveProblem
import app.still.launcher.read.readingStreak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** A book in the library with its reading time so far. */
data class LibraryBook(val book: Book, val readMillis: Long)

/** Whether daily reads can reach Google Drive. */
enum class DailyState { OFF, ON, NEEDS_YOU }

/** A series' read today, if one has come, and the time spent on it. */
data class TodayRead(val series: ReadSeries, val read: Book?, val readMillis: Long)

/** Something to say after a daily read couldn't be fetched; null [problem] means Google needs the person. */
data class DailyProblem(val problem: DriveProblem?)

/** What the Read tab shows. */
data class Library(
    val reading: List<LibraryBook>,
    val toRead: List<LibraryBook>,
    val finished: List<LibraryBook>,
    val todayMillis: Long,
    /** Minutes a day, 0 for no goal. */
    val goalMinutes: Int,
    val streak: Int,
    /** The duration habit reading fills, if one is chosen. */
    val habit: Habit?,
    val durationHabits: List<Habit>,
    val dailyState: DailyState,
    /** One per series, in the order they were added. */
    val todayReads: List<TodayRead>,
    val hasSeries: Boolean,
) {
    val isEmpty: Boolean get() = reading.isEmpty() && toRead.isEmpty() && finished.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadViewModel(
    private val read: ReadRepository,
    private val dailyReads: DailyReadsRepository,
    private val preferences: LauncherPreferences,
    productivity: ProductivityRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    /** Today's date, changing at midnight while the tab is open. */
    private val currentDate: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(DATE_TICK_MILLIS)
        }
    }.distinctUntilChanged()

    private val dailyToday = combine(
        dailyReads.series,
        currentDate.flatMapLatest { date -> dailyReads.readsSince(date.atStartOfDay(ZoneId.systemDefault()).toInstant()) },
        ::Pair,
    )

    val library: StateFlow<Library?> = combine(
        combine(read.books, read.bookTimes, ::Pair),
        read.days(today.minusDays(STREAK_LOOKBACK_DAYS), today),
        preferences.settings,
        productivity.habits,
        dailyToday,
    ) { (books, times), days, settings, habits, (series, todays) ->
        val millis = times.associate { it.bookId to it.millis }
        val entries = books.map { LibraryBook(it, millis[it.id] ?: 0L) }
        val byDate = days.associate { it.date to it.millis }
        val durationHabits = habits.filter { it.measure == HabitMeasure.DURATION && !it.archived }
        Library(
            reading = entries.filter { it.book.status == BookStatus.READING },
            toRead = entries.filter { it.book.status == BookStatus.TO_READ }.sortedByDescending { it.book.addedAt },
            finished = entries.filter { it.book.status == BookStatus.FINISHED }.sortedByDescending { it.book.finishedAt },
            todayMillis = byDate[LocalDate.now()] ?: 0L,
            goalMinutes = settings.readGoalMinutes,
            streak = readingStreak(byDate, settings.readGoalMinutes * MINUTE_MILLIS, LocalDate.now()),
            habit = durationHabits.firstOrNull { it.id == settings.readHabitId },
            durationHabits = durationHabits,
            dailyState = when {
                settings.driveAccount == null -> DailyState.OFF
                settings.driveNeedsYou -> DailyState.NEEDS_YOU
                else -> DailyState.ON
            },
            todayReads = series.map { one ->
                val latest = todays.filter { it.seriesId == one.id }.maxByOrNull { it.arrivedAt ?: it.addedAt }
                TodayRead(one, latest, latest?.let { millis[it.id] } ?: 0L)
            },
            hasSeries = series.isNotEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _downloading = MutableStateFlow<Set<Long>>(emptySet())

    /** Daily reads being fetched to open. */
    val downloading: StateFlow<Set<Long>> = _downloading.asStateFlow()

    private val _opens = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Books ready to open in the reader, by id. */
    val opens: SharedFlow<Long> = _opens

    private val _problems = MutableSharedFlow<DailyProblem>(extraBufferCapacity = 1)

    /** Daily reads that couldn't be fetched. */
    val problems: SharedFlow<DailyProblem> = _problems

    /** Looks for new daily reads if it's been a while. */
    fun refreshDaily() {
        viewModelScope.launch { dailyReads.syncIfStale() }
    }

    /** Opens a daily read, fetching it from Drive first if it isn't on the phone. */
    fun openDaily(book: Book) {
        if (book.id in _downloading.value) return
        if (book.onDevice) {
            _opens.tryEmit(book.id)
            return
        }
        _downloading.update { it + book.id }
        viewModelScope.launch {
            when (val result = dailyReads.download(book)) {
                is DriveResult.Done -> _opens.emit(result.value.id)
                DriveResult.NeedsYou -> _problems.emit(DailyProblem(null))
                is DriveResult.Failed -> _problems.emit(DailyProblem(result.problem))
            }
            _downloading.update { it - book.id }
        }
    }

    private val _imported = MutableSharedFlow<Result<Book>>(extraBufferCapacity = 1)

    /** Each import's outcome, to say what happened. */
    val imported: SharedFlow<Result<Book>> = _imported

    fun import(uri: Uri) {
        viewModelScope.launch { _imported.emit(read.import(uri)) }
    }

    fun setStatus(book: Book, status: BookStatus) {
        viewModelScope.launch { read.setStatus(book, status) }
    }

    fun remove(book: Book) {
        viewModelScope.launch { read.remove(book) }
    }

    fun setGoal(minutes: Int) {
        viewModelScope.launch { preferences.setReadGoal(minutes) }
    }

    fun setHabit(habit: Habit?) {
        viewModelScope.launch { preferences.setReadHabit(habit?.id) }
    }

    companion object {
        private const val STREAK_LOOKBACK_DAYS = 400L
        private const val MINUTE_MILLIS = 60_000L
        private const val DATE_TICK_MILLIS = 60_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                ReadViewModel(container.readRepository, container.dailyReads, container.preferences, container.productivityRepository)
            }
        }
    }
}
