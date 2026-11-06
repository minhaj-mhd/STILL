package app.still.launcher.ui.read

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.ReadRepository
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.BookStatus
import app.still.launcher.read.readingStreak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A book in the library with its reading time so far. */
data class LibraryBook(val book: Book, val readMillis: Long)

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
) {
    val isEmpty: Boolean get() = reading.isEmpty() && toRead.isEmpty() && finished.isEmpty()
}

class ReadViewModel(
    private val read: ReadRepository,
    private val preferences: LauncherPreferences,
    productivity: ProductivityRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    val library: StateFlow<Library?> = combine(
        combine(read.books, read.bookTimes, ::Pair),
        read.days(today.minusDays(STREAK_LOOKBACK_DAYS), today),
        preferences.settings,
        productivity.habits,
    ) { (books, times), days, settings, habits ->
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
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                ReadViewModel(container.readRepository, container.preferences, container.productivityRepository)
            }
        }
    }
}
