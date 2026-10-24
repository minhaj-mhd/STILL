package app.still.launcher.gates

import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.gates.GateItem
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.util.EssentialApps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A change held back because it would open [opens] (packages) without doing the item; it waits for the slide. */
data class PendingEdit<T>(val draft: T, val opens: Set<String>)

/**
 * Works out which apps saving an edited task or habit would open, against today's gates, so the
 * editors can hold such a change back until it's confirmed (see [appsOpenedByEdit]).
 */
class EditGuard(
    private val taskGates: TaskGateMonitor,
    private val essentialApps: EssentialApps,
    private val preferences: LauncherPreferences,
) {
    private var alwaysOpen: Set<String> = emptySet()

    /** Reads the apps the user keeps open whatever the gates say; call when an editor opens. */
    suspend fun load() {
        alwaysOpen = preferences.current().gateAlwaysOpenPackages
    }

    /** The packages [item] keeps locked now and wouldn't once edited; [edited] builds its agenda for a day. */
    fun opens(item: GateItem, edited: (LocalDate) -> TodayAgenda): Set<String> {
        val inputs = taskGates.inputs.value ?: return emptySet()
        return appsOpenedByEdit(item, edited(inputs.agenda.date), inputs, Instant.now(), ZoneId.systemDefault()) {
            essentialApps.canGate(it, alwaysOpen)
        }
    }
}
