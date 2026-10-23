package app.still.launcher.data.productivity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A reminder card swiped off the home screen. [key] is the reminder's occurrence key. */
@Entity(tableName = "dismissed_reminders", indices = [Index("date")])
data class DismissedReminder(
    @PrimaryKey val key: String,
    /** The occurrence's date, so old entries can be cleared out. */
    val date: LocalDate,
)
