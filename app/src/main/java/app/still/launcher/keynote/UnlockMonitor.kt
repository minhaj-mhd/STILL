package app.still.launcher.keynote

import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.limit.dayStart
import app.still.launcher.limit.limitDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Counts unlocks from Usage access: today's so far, and each of the last [BASELINE_DAYS] days'
 * by the same time of day, for "fewer than usual by now". Earlier days don't change, so they're
 * read once a day.
 */
class UnlockMonitor(private val screenTime: ScreenTimeRepository) {

    private val mutex = Mutex()
    private var baselineFor: LocalDate? = null

    /** Each earlier day's unlock times, as offsets from that day's start; days with none are left out. */
    private var baseline: List<List<Long>> = emptyList()

    /** Today's unlocks against the usual by now, with days starting at [dayStartHour]; null without Usage access. */
    suspend fun observe(now: ZonedDateTime, dayStartHour: Int): UnlockObservation? = mutex.withLock {
        if (!screenTime.hasAccess()) return@withLock null
        val zone = now.zone
        val day = limitDay(now.toLocalDateTime(), dayStartHour)
        fun startOf(date: LocalDate): Long = dayStart(date, dayStartHour).atZone(zone).toInstant().toEpochMilli()
        if (baselineFor != day) {
            baseline = (1L..BASELINE_DAYS).map { back ->
                val from = startOf(day.minusDays(back))
                screenTime.unlockTimes(from, startOf(day.minusDays(back - 1))).map { it - from }
            }.filter { it.isNotEmpty() }
            baselineFor = day
        }
        val start = startOf(day)
        val nowMillis = now.toInstant().toEpochMilli()
        unlockObservation(screenTime.unlockTimes(start, nowMillis).size, unlocksByNow(baseline, nowMillis - start))
    }
}
