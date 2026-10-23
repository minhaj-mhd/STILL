package app.still.launcher.focus

import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.schedule.LocalDateSerializer
import app.still.launcher.schedule.Occurrence
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil

/**
 * A focus timer running for one occurrence of a duration habit. It runs for what was left of the
 * habit's target when it started; finishing it logs those minutes as done by the timer.
 */
@Serializable
data class FocusSession(
    val habitId: Long,
    val title: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** The occurrence's time slot, as in [OccurrenceRecord.slotMinute]. */
    val slotMinute: Int,
    val startMillis: Long,
    val minutes: Int,
    /** Minutes already logged when the timer started. */
    val startProgress: Double,
) {
    val endMillis: Long get() = startMillis + minutes * MILLIS_PER_MINUTE

    val occurrence: Occurrence get() = Occurrence(
        date,
        if (slotMinute == OccurrenceRecord.ALL_DAY) null else LocalTime.of(slotMinute / 60, slotMinute % 60),
    )

    fun isFor(habitId: Long, occurrence: Occurrence): Boolean = this.habitId == habitId && this.occurrence == occurrence

    fun isDue(nowMillis: Long): Boolean = nowMillis >= endMillis

    /** Whole minutes focused by [nowMillis], never more than the timer's length. */
    fun minutesDone(nowMillis: Long): Int = ((nowMillis - startMillis) / MILLIS_PER_MINUTE).toInt().coerceIn(0, minutes)
}

// Top level rather than in a companion: the serialization plugin puts serializer() in the companion.
private const val MILLIS_PER_MINUTE = 60_000L

/** How long a timer runs to reach a habit's [target] minutes from [progress]: what's left, at least a minute. */
fun timerMinutes(target: Double, progress: Double): Int = ceil(target - progress).toInt().coerceAtLeast(1)
