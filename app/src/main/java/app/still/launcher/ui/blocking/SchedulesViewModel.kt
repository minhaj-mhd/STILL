package app.still.launcher.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.blocking.backOn
import app.still.launcher.blocking.offForToday
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.blocking.BlockingSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class SchedulesViewModel(private val repository: BlockingRepository) : ViewModel() {

    /** Null until read once. */
    val schedules: StateFlow<List<BlockingSchedule>?> =
        repository.schedules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Switching on also undoes "off for today". */
    fun turnOn(schedule: BlockingSchedule) {
        save(schedule.backOn(LocalDateTime.now()).copy(enabled = true))
    }

    fun turnOffForToday(schedule: BlockingSchedule) {
        save(schedule.offForToday(LocalDateTime.now()))
    }

    fun turnOff(schedule: BlockingSchedule) {
        save(schedule.copy(enabled = false))
    }

    private fun save(schedule: BlockingSchedule) {
        viewModelScope.launch { repository.saveSchedule(schedule) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                SchedulesViewModel(container.blockingRepository)
            }
        }
    }
}
