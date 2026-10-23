package app.still.launcher.gates

import app.still.launcher.data.GateRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.productivity.buildAgenda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps what task gates are decided from (the gates, today's agenda and today's gate events) up
 * to date for the launcher and Precision mode alike, so both see the same thing at once.
 */
class TaskGateMonitor(
    productivity: ProductivityRepository,
    private val repository: GateRepository,
    scope: CoroutineScope,
) {
    private val today = MutableStateFlow(LocalDate.now())

    /** Null until read once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val inputs: StateFlow<GateInputs?> = today
        .flatMapLatest { date ->
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            combine(
                repository.gates,
                productivity.tasks,
                productivity.habits,
                productivity.records(date.minusDays(RECORD_LOOKBACK_DAYS), date),
                repository.eventsSince(startOfDay),
            ) { gates, tasks, habits, records, events ->
                // Without gates there's nothing to decide; skip building the agenda.
                val agenda = if (gates.isEmpty()) TodayAgenda(date) else buildAgenda(date, tasks, habits, records)
                GateInputs(gates, agenda, events)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Moves to a new day at midnight; whoever keeps time calls this. */
    fun setToday(date: LocalDate) {
        today.value = date
    }

    /** Apps closed by a gate at [now]; see [closedGates]. */
    fun closedGates(now: Instant, canGate: (String) -> Boolean): Map<String, List<GateCheck>> {
        val current = inputs.value ?: return emptyMap()
        return closedGates(current, now, ZoneId.systemDefault(), canGate)
    }

    private companion object {
        /** Long enough for a yearly task to find its previous occurrence. */
        const val RECORD_LOOKBACK_DAYS = 366L
    }
}
