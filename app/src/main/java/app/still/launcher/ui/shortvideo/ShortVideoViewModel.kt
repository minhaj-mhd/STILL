package app.still.launcher.ui.shortvideo

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.shortvideo.ShortVideoBlocker
import app.still.launcher.shortvideo.ShortVideoRule
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Short-video blocking, as Settings shows it. */
class ShortVideoViewModel(private val blocker: ShortVideoBlocker) : ViewModel() {

    val running: StateFlow<Boolean> = blocker.running

    val rulesOff: StateFlow<Set<String>> = blocker.rulesOff

    fun isSwitchedOn(): Boolean = blocker.isSwitchedOn()

    fun settingsIntent(): Intent = blocker.settingsIntent()

    fun turnOff() = blocker.turnOff()

    fun setBlocks(rule: ShortVideoRule, on: Boolean) {
        viewModelScope.launch { blocker.setBlocks(rule, on) }
    }

    fun setBlocksInBrowsers(on: Boolean) {
        viewModelScope.launch { blocker.setBlocksInBrowsers(on) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ShortVideoViewModel((this[APPLICATION_KEY] as StillApplication).container.shortVideos)
            }
        }
    }
}
