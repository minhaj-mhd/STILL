package app.still.launcher.data

import app.still.launcher.data.practice.Choice
import app.still.launcher.data.practice.Keynote
import app.still.launcher.data.practice.PracticeDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Inner practice: choices made before opening apps, and each day's chosen quality. */
class PracticeRepository(private val dao: PracticeDao) {

    /** Choices made from [from] up to [to]. */
    fun choices(from: Instant, to: Instant): Flow<List<Choice>> = dao.observeChoices(from, to)

    suspend fun record(choice: Choice) = dao.insertChoice(choice)

    /** When each app was last asked what it was opened for, in epoch milliseconds. */
    suspend fun lastAsked(): Map<String, Long> = dao.lastAsked().associate { it.packageName to it.at.toEpochMilli() }

    fun keynote(day: LocalDate): Flow<Keynote?> = dao.observeKeynote(day)

    /** The qualities chosen from [from] to [to], both included. */
    fun keynotes(from: LocalDate, to: LocalDate): Flow<List<Keynote>> = dao.observeKeynotes(from, to)

    /** The most recent quality chosen before [day], to start the next one from. */
    suspend fun latestKeynoteBefore(day: LocalDate): Keynote? = dao.latestKeynoteBefore(day)

    suspend fun saveKeynote(keynote: Keynote) = dao.upsertKeynote(keynote)
}
