package app.still.launcher.data.productivity

import androidx.room.Entity
import androidx.room.Index
import app.still.launcher.schedule.Occurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ItemType { TASK, HABIT }

/**
 * What happened to one occurrence of a recurring task or a habit. No row means nothing was
 * done; once the day has passed, that counts as missed.
 */
@Entity(
    tableName = "occurrence_records",
    primaryKeys = ["itemType", "itemId", "date", "slotMinute"],
    indices = [Index("date")],
)
data class OccurrenceRecord(
    val itemType: ItemType,
    val itemId: Long,
    val date: LocalDate,
    /** Minute of the day of the occurrence's time slot, or [ALL_DAY]. */
    val slotMinute: Int = ALL_DAY,
    /** Progress toward the target: 1 for a check, otherwise the count, minutes or amount so far. */
    val value: Double = 0.0,
    /** Excused for this occurrence: not pending, but missed in streaks. */
    val skipped: Boolean = false,
    /** Finished by a STILL timer rather than ticked by hand; task gates can trust these more. */
    val viaTimer: Boolean = false,
    val updatedAt: Instant = Instant.now(),
) {
    companion object {
        /** Slot value for occurrences without a time. */
        const val ALL_DAY = -1

        fun slotMinuteOf(occurrence: Occurrence): Int =
            occurrence.time?.let { it.hour * 60 + it.minute } ?: ALL_DAY
    }
}

fun OccurrenceRecord.toOccurrence(): Occurrence =
    Occurrence(date, if (slotMinute == OccurrenceRecord.ALL_DAY) null else LocalTime.of(slotMinute / 60, slotMinute % 60))
