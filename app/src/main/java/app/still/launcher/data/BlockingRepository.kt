package app.still.launcher.data

import app.still.launcher.data.blocking.AppBlock
import app.still.launcher.data.blocking.AppBlockDao
import app.still.launcher.data.blocking.BlockingSchedule
import app.still.launcher.data.blocking.BlockingScheduleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class BlockingRepository(
    private val dao: AppBlockDao,
    private val scheduleDao: BlockingScheduleDao,
) {

    /** Every block by package, including ones that have already run out; compare with the time. */
    val blocks: Flow<Map<String, Instant>> = dao.observeBlocks().map { blocks ->
        blocks.associate { it.packageName to it.blockedUntil }
    }

    /** Blocking schedules, oldest first. */
    val schedules: Flow<List<BlockingSchedule>> = scheduleDao.observeSchedules()

    /**
     * Blocks [packageName] until [until]. A block can only be made longer this way, never
     * shorter, so blocking again can't be used to cut a running block short.
     */
    suspend fun block(packageName: String, until: Instant) {
        val current = dao.getBlock(packageName)?.blockedUntil
        if (current != null && current >= until) return
        dao.upsert(AppBlock(packageName, until))
    }

    suspend fun clearExpired(now: Instant = Instant.now()) = dao.deleteExpired(now)

    suspend fun getSchedule(scheduleId: Long): BlockingSchedule? = scheduleDao.getSchedule(scheduleId)

    /** Saves a new or edited schedule; returns its id. */
    suspend fun saveSchedule(schedule: BlockingSchedule): Long =
        if (schedule.id == 0L) scheduleDao.insert(schedule) else schedule.id.also { scheduleDao.update(schedule) }

    suspend fun deleteSchedule(scheduleId: Long) = scheduleDao.delete(scheduleId)
}
