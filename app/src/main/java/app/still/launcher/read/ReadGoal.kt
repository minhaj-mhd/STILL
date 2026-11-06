package app.still.launcher.read

import java.time.LocalDate

/**
 * Days in a row with reading, up to [today]: days meeting [goalMillis], or any reading of a
 * minute or more without a goal. Today only adds once it counts, so the streak isn't lost
 * before the day is over.
 */
fun readingStreak(millisByDate: Map<LocalDate, Long>, goalMillis: Long, today: LocalDate): Int {
    val needed = if (goalMillis > 0) goalMillis else MINUTE_MILLIS
    fun counts(date: LocalDate) = (millisByDate[date] ?: 0L) >= needed
    var day = if (counts(today)) today else today.minusDays(1)
    var streak = 0
    while (counts(day)) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** Whole minutes in [millis], as a duration habit logs them. */
fun readingMinutes(millis: Long): Double = (millis / MINUTE_MILLIS).toDouble()

/** How far through a PDF on [page] (from 0) of [pageCount]: the last page is the end. */
fun pdfProgress(page: Int, pageCount: Int): Double =
    if (pageCount <= 0) 0.0 else ((page + 1).toDouble() / pageCount).coerceIn(0.0, 1.0)

/** Whether an EPUB at [progress] is done: its last pages are a sliver short of 1. */
fun isAtEnd(progress: Double): Boolean = progress >= END_PROGRESS

private const val MINUTE_MILLIS = 60_000L
private const val END_PROGRESS = 0.995

/** The choices for a daily reading goal, in minutes. */
val READ_GOAL_CHOICES = listOf(10, 15, 20, 30, 45, 60)
