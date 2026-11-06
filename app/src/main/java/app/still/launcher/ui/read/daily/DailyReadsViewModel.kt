package app.still.launcher.ui.read.daily

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.DailyReadsRepository
import app.still.launcher.data.DriveResult
import app.still.launcher.data.FolderPreview
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ReadRepository
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.ReadSeries
import app.still.launcher.drive.DriveAccess
import app.still.launcher.drive.DriveAuth
import app.still.launcher.drive.DriveFolder
import app.still.launcher.drive.DriveProblem
import app.still.launcher.ui.read.DailyProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A series with how many reads it has. */
data class SeriesRow(val series: ReadSeries, val count: Int)

/** The daily reads page: whether Drive is connected, the series, and how fetching is going. */
data class DailyOverview(
    val account: String?,
    val needsYou: Boolean,
    val keepDays: Int,
    val checkedAtMillis: Long,
    val newAtCheck: Int,
    val series: List<SeriesRow>,
) {
    val connected: Boolean get() = account != null
}

/** Drive's folders while picking one for a new series. */
sealed interface FolderList {
    data object Loading : FolderList

    data class Loaded(val folders: List<DriveFolder>) : FolderList

    data class Failed(val problem: DailyProblem) : FolderList
}

/** What a chosen folder holds, once Drive has said. */
data class ChosenFolder(val folder: DriveFolder, val preview: FolderPreview?)

/** What's going on that the page should show as busy. */
enum class Busy { CONNECTING, CHECKING, ADDING }

/** Things for the activity to do once. */
sealed interface DailyEvent {
    /** Show Google's screen to choose an account or agree. */
    data class SignIn(val intent: PendingIntent) : DailyEvent

    data class Open(val bookId: Long) : DailyEvent

    data class Problem(val problem: DailyProblem) : DailyEvent

    data class Added(val seriesId: Long) : DailyEvent
}

class DailyReadsViewModel(
    private val repository: DailyReadsRepository,
    private val auth: DriveAuth,
    private val preferences: LauncherPreferences,
    read: ReadRepository,
) : ViewModel() {

    val overview: StateFlow<DailyOverview?> = combine(preferences.settings, repository.series, repository.counts) { settings, series, counts ->
        DailyOverview(
            account = settings.driveAccount,
            needsYou = settings.driveNeedsYou,
            keepDays = settings.dailyKeepDays,
            checkedAtMillis = settings.dailyCheckedAt,
            newAtCheck = settings.dailyNewAtCheck,
            series = series.map { SeriesRow(it, counts[it.id] ?: 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reading time per book, by id. */
    val readTimes: StateFlow<Map<Long, Long>> = read.bookTimes
        .map { times -> times.associate { it.bookId to it.millis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun reads(seriesId: Long): Flow<List<Book>> = repository.reads(seriesId)

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    private val _downloading = MutableStateFlow<Set<Long>>(emptySet())
    val downloading: StateFlow<Set<Long>> = _downloading.asStateFlow()

    private val _folders = MutableStateFlow<FolderList?>(null)
    val folders: StateFlow<FolderList?> = _folders.asStateFlow()

    private val _chosen = MutableStateFlow<ChosenFolder?>(null)
    val chosen: StateFlow<ChosenFolder?> = _chosen.asStateFlow()

    private val _events = MutableSharedFlow<DailyEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DailyEvent> = _events

    /** Asks Google for Drive access; shows Google's screen when it needs the person. */
    fun connect() {
        if (_busy.value != null) return
        _busy.value = Busy.CONNECTING
        viewModelScope.launch {
            handleAccess(auth.access(preferences.current().driveAccount))
        }
    }

    /** What Google's screen came back with. */
    fun onSignIn(data: Intent?) {
        viewModelScope.launch { handleAccess(auth.accessFrom(data)) }
    }

    private suspend fun handleAccess(access: DriveAccess) {
        when (access) {
            is DriveAccess.Granted -> {
                when (val result = repository.connect(access.token)) {
                    is DriveResult.Done -> Unit
                    DriveResult.NeedsYou -> _events.emit(DailyEvent.Problem(DailyProblem(null)))
                    is DriveResult.Failed -> _events.emit(DailyEvent.Problem(DailyProblem(result.problem)))
                }
                _busy.value = null
            }
            is DriveAccess.NeedsYou -> _events.emit(DailyEvent.SignIn(access.intent))
            is DriveAccess.Failed -> {
                if (access.reason != DriveProblem.CANCELLED) _events.emit(DailyEvent.Problem(DailyProblem(access.reason)))
                _busy.value = null
            }
        }
    }

    /** Google's screen was closed without an answer. */
    fun onSignInCancelled() {
        _busy.value = null
    }

    fun checkNow() {
        if (_busy.value != null) return
        _busy.value = Busy.CHECKING
        viewModelScope.launch {
            report(repository.sync())
            _busy.value = null
        }
    }

    fun loadFolders() {
        _chosen.value = null
        _folders.value = FolderList.Loading
        viewModelScope.launch {
            _folders.value = when (val result = repository.folders()) {
                is DriveResult.Done -> FolderList.Loaded(result.value)
                DriveResult.NeedsYou -> FolderList.Failed(DailyProblem(null))
                is DriveResult.Failed -> FolderList.Failed(DailyProblem(result.problem))
            }
        }
    }

    fun choose(folder: DriveFolder) {
        _chosen.value = ChosenFolder(folder, null)
        viewModelScope.launch {
            val result = repository.preview(folder.id)
            if (_chosen.value?.folder?.id != folder.id) return@launch
            if (result is DriveResult.Done) _chosen.value = ChosenFolder(folder, result.value) else report(result)
        }
    }

    fun addSeries(folder: DriveFolder, name: String) {
        if (_busy.value != null) return
        _busy.value = Busy.ADDING
        viewModelScope.launch {
            when (val result = repository.addSeries(folder, name)) {
                is DriveResult.Done -> _events.emit(DailyEvent.Added(result.value))
                else -> report(result)
            }
            _busy.value = null
        }
    }

    fun rename(series: ReadSeries, name: String) {
        viewModelScope.launch { repository.rename(series, name) }
    }

    fun remove(series: ReadSeries) {
        viewModelScope.launch { repository.removeSeries(series) }
    }

    fun setKeepDays(days: Int) {
        viewModelScope.launch { repository.setKeepDays(days) }
    }

    fun disconnect() {
        viewModelScope.launch { repository.disconnect() }
    }

    /** Opens a read, fetching it from Drive first if it isn't on the phone. */
    fun open(book: Book) {
        if (book.id in _downloading.value) return
        if (book.onDevice) {
            _events.tryEmit(DailyEvent.Open(book.id))
            return
        }
        _downloading.update { it + book.id }
        viewModelScope.launch {
            when (val result = repository.download(book)) {
                is DriveResult.Done -> _events.emit(DailyEvent.Open(result.value.id))
                else -> report(result)
            }
            _downloading.update { it - book.id }
        }
    }

    private suspend fun report(result: DriveResult<*>) {
        when (result) {
            is DriveResult.Done -> Unit
            DriveResult.NeedsYou -> _events.emit(DailyEvent.Problem(DailyProblem(null)))
            is DriveResult.Failed -> _events.emit(DailyEvent.Problem(DailyProblem(result.problem)))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                DailyReadsViewModel(container.dailyReads, container.driveAuth, container.preferences, container.readRepository)
            }
        }
    }
}
