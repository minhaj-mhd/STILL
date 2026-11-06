package app.still.launcher.data

import app.still.launcher.data.history.FocusModePeriod
import app.still.launcher.data.history.HistoryDao
import app.still.launcher.data.history.InterventionRecord
import app.still.launcher.data.history.InterventionType
import app.still.launcher.data.history.UsageAppRecord
import app.still.launcher.data.history.UsageDayRecord
import app.still.launcher.history.DayLimit
import app.still.launcher.history.DayUsage
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Phone use day by day, the times STILL stepped in, and focus mode sessions, kept for Insights. */
class HistoryRepository(private val dao: HistoryDao) {

    /** The last intervention noted, so a screen shown twice for one opening counts once. */
    private var lastIntervention: InterventionRecord? = null

    suspend fun lastDay(): LocalDate? = dao.lastDay()

    /** The first day saved, the earliest Insights can go back to; null before any. */
    val firstDay: Flow<LocalDate?> = dao.observeFirstDay()

    suspend fun saveDay(day: LocalDate, dayStartHour: Int, usage: DayUsage, limit: DayLimit) {
        val record = UsageDayRecord(
            day = day,
            dayStartHour = dayStartHour,
            screenMillis = usage.screenMillis,
            stillMillis = usage.stillMillis,
            opens = usage.opens,
            unlocks = usage.unlocks,
            hourMillis = usage.hourMillis,
            countedMillis = limit.countedMillis,
            limitMillis = limit.limitMillis,
        )
        dao.saveDay(record, usage.apps.map { (packageName, app) -> UsageAppRecord(day, packageName, app.millis, app.opens) })
    }

    fun days(from: LocalDate, to: LocalDate): Flow<List<UsageDayRecord>> = dao.observeDays(from, to)

    fun apps(from: LocalDate, to: LocalDate): Flow<List<UsageAppRecord>> = dao.observeApps(from, to)

    /** Notes that STILL stepped in front of [packageName]. The same again within a few seconds counts once. */
    suspend fun recordIntervention(packageName: String, type: InterventionType, now: Instant = Instant.now()) {
        val record = InterventionRecord(time = now, packageName = packageName, type = type)
        synchronized(this) {
            val last = lastIntervention
            if (last != null && last.packageName == packageName && last.type == type && Duration.between(last.time, now) < SAME_STEP) return
            lastIntervention = record
        }
        dao.insertIntervention(record)
    }

    fun interventions(from: Instant, to: Instant): Flow<List<InterventionRecord>> = dao.observeInterventions(from, to)

    suspend fun recordFocusPeriod(startedAt: Instant, endedAt: Instant) {
        if (endedAt > startedAt) dao.insertFocusPeriod(FocusModePeriod(startedAt, endedAt))
    }

    fun focusPeriods(from: Instant, to: Instant): Flow<List<FocusModePeriod>> = dao.observeFocusPeriods(from, to)

    private companion object {
        val SAME_STEP: Duration = Duration.ofSeconds(5)
    }
}
