package app.still.launcher.ui.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.PlanPause
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.PlanProgress
import app.still.launcher.productivity.planProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The Plans page and each plan's own page: how every plan stands today, and pausing or deleting one. */
class PlansViewModel(private val repository: ProductivityRepository) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    /** Every plan as it stands today, newest first. Null until the database has been read once. */
    val plans: StateFlow<List<PlanProgress>?> = combine(repository.plans, repository.tasks, today) { plans, tasks, date ->
        plans.map { planProgress(it, tasks, date) }.sortedByDescending { it.plan.createdAt }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The pages call this when they're shown, so a new day is picked up. */
    fun refreshDay() {
        today.value = LocalDate.now()
    }

    /** Paused from today: its days wait, and come back on Today when it carries on. */
    fun pause(plan: Plan) {
        if (plan.isPaused) return
        viewModelScope.launch { repository.updatePlan(plan.copy(pauses = plan.pauses + PlanPause(from = LocalDate.now()))) }
    }

    /** Carries on from the day it was paused on. A pause started and ended the same day leaves no trace. */
    fun resume(plan: Plan) {
        val pause = plan.pauses.lastOrNull()?.takeIf { it.until == null } ?: return
        val today = LocalDate.now()
        val pauses = if (pause.from >= today) plan.pauses.dropLast(1) else plan.pauses.dropLast(1) + pause.copy(until = today)
        viewModelScope.launch { repository.updatePlan(plan.copy(pauses = pauses)) }
    }

    fun delete(plan: Plan) {
        viewModelScope.launch { repository.deletePlan(plan.id) }
    }

    /** Ticks a plan task off, or back on, from the plan's page. */
    fun setTaskDone(task: Task, done: Boolean) {
        viewModelScope.launch { repository.setTaskDone(task, occurrence = null, done = done) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                PlansViewModel(container.productivityRepository)
            }
        }
    }
}
