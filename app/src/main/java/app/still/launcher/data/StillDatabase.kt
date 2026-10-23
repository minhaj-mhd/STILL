package app.still.launcher.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.still.launcher.data.blocking.AppBlock
import app.still.launcher.data.blocking.AppBlockDao
import app.still.launcher.data.blocking.BlockingSchedule
import app.still.launcher.data.blocking.BlockingScheduleDao
import app.still.launcher.data.gates.GateEvent
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.gates.TaskGateDao
import app.still.launcher.data.notes.Note
import app.still.launcher.data.notes.NoteDao
import app.still.launcher.data.notifications.HeldNotification
import app.still.launcher.data.notifications.HeldNotificationDao
import app.still.launcher.data.practice.Choice
import app.still.launcher.data.practice.Keynote
import app.still.launcher.data.practice.PracticeDao
import app.still.launcher.data.productivity.Converters
import app.still.launcher.data.productivity.DismissedReminder
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.ProductivityDao
import app.still.launcher.data.productivity.Task

/**
 * Everything STILL keeps on the phone beyond simple settings. Schemas are exported to
 * app/schemas; every version bump needs a migration so existing data survives updates.
 *
 * Version 2 added [DismissedReminder]; 3 added [Note]; 4 added [AppBlock]; 5 added [BlockingSchedule];
 * 6 added [TaskGate] and [GateEvent]; 7 added [HeldNotification]; 8 added [Choice] and [Keynote].
 */
@Database(
    entities = [
        Task::class, Habit::class, OccurrenceRecord::class, DismissedReminder::class, Note::class,
        AppBlock::class, BlockingSchedule::class, TaskGate::class, GateEvent::class, HeldNotification::class,
        Choice::class, Keynote::class,
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class StillDatabase : RoomDatabase() {

    abstract fun productivityDao(): ProductivityDao

    abstract fun noteDao(): NoteDao

    abstract fun appBlockDao(): AppBlockDao

    abstract fun blockingScheduleDao(): BlockingScheduleDao

    abstract fun taskGateDao(): TaskGateDao

    abstract fun heldNotificationDao(): HeldNotificationDao

    abstract fun practiceDao(): PracticeDao

    companion object {
        fun create(context: Context): StillDatabase =
            Room.databaseBuilder(context.applicationContext, StillDatabase::class.java, "still.db").build()
    }
}
