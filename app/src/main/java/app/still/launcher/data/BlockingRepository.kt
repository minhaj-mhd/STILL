package app.still.launcher.data

import app.still.launcher.data.blocking.AppBlock
import app.still.launcher.data.blocking.AppBlockDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class BlockingRepository(private val dao: AppBlockDao) {

    /** Every block by package, including ones that have already run out; compare with the time. */
    val blocks: Flow<Map<String, Instant>> = dao.observeBlocks().map { blocks ->
        blocks.associate { it.packageName to it.blockedUntil }
    }

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
}
