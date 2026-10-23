package app.still.launcher.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.still.launcher.data.blocking.AppBlock
import app.still.launcher.data.blocking.AppBlockDao
import app.still.launcher.data.notes.Note
import app.still.launcher.data.notes.NoteDao
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
 * Version 2 added [DismissedReminder]; version 3 added [Note]; version 4 added [AppBlock].
 */
@Database(
    entities = [Task::class, Habit::class, OccurrenceRecord::class, DismissedReminder::class, Note::class, AppBlock::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
abstract class StillDatabase : RoomDatabase() {

    abstract fun productivityDao(): ProductivityDao

    abstract fun noteDao(): NoteDao

    abstract fun appBlockDao(): AppBlockDao

    companion object {
        fun create(context: Context): StillDatabase =
            Room.databaseBuilder(context.applicationContext, StillDatabase::class.java, "still.db").build()
    }
}
