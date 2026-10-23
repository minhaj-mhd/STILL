package app.still.launcher.data

import app.still.launcher.data.gates.GateEvent
import app.still.launcher.data.gates.GateEventType
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.gates.TaskGateDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Task gates and what happened at them. */
class GateRepository(private val dao: TaskGateDao) {

    /** Oldest first. */
    val gates: Flow<List<TaskGate>> = dao.observeGates()

    fun eventsSince(since: Instant): Flow<List<GateEvent>> = dao.observeEventsSince(since)

    suspend fun getGate(gateId: Long): TaskGate? = dao.getGate(gateId)

    /** Saves a new or edited gate; returns its id. */
    suspend fun saveGate(gate: TaskGate): Long =
        if (gate.id == 0L) dao.insert(gate) else gate.id.also { dao.update(gate) }

    suspend fun deleteGate(gateId: Long) = dao.delete(gateId)

    /** Lets [packageName] past each of [gates] until [until]; each gate counts it as one of today's emergency opens. */
    suspend fun recordEmergencyOpen(packageName: String, gates: List<TaskGate>, now: Instant, until: Instant) {
        dao.insertEvents(gates.map { GateEvent(time = now, gateId = it.id, packageName = packageName, type = GateEventType.EMERGENCY_OPEN, openUntil = until) })
    }
}
