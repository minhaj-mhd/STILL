package app.still.launcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.still.launcher.data.productivity.Converters
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.ProductivityDao
import app.still.launcher.data.productivity.Task

/** Everything STILL keeps on the phone beyond simple settings. Schemas are exported to app/schemas. */
@Database(
    entities = [Task::class, Habit::class, OccurrenceRecord::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class StillDatabase : RoomDatabase() {

    abstract fun productivityDao(): ProductivityDao

    companion object {
        fun create(context: Context): StillDatabase =
            Room.databaseBuilder(context.applicationContext, StillDatabase::class.java, "still.db").build()
    }
}
