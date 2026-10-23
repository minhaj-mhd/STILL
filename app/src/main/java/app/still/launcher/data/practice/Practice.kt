package app.still.launcher.data.practice

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.still.launcher.data.productivity.ItemType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Where a choice about opening an app was made. */
enum class ChoiceSource {
    /** "What are you opening it for?" */
    PAUSE,

    /** The launch delay's countdown. */
    LAUNCH_DELAY,
}

/** The answer to "What are you opening it for?" */
enum class ChoiceAnswer { PURPOSE, BREAK, CHECKING }

/** One time STILL paused before an app, and what happened: it opened, or you turned back. */
@Entity(tableName = "choices", indices = [Index("at")])
data class Choice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val packageName: String,
    val source: ChoiceSource,
    val answer: ChoiceAnswer? = null,
    val turnedBack: Boolean,
)

/** The quality chosen for one day, the action that expresses it, and the Today item it's practised with. */
@Entity(tableName = "keynotes")
data class Keynote(
    @PrimaryKey val day: LocalDate,
    val quality: String,
    val action: String,
    val itemType: ItemType? = null,
    val itemId: Long? = null,
    val setAt: Instant = Instant.now(),
)

/** When an app was last asked about. */
data class LastAsked(val packageName: String, val at: Instant)

@Dao
interface PracticeDao {

    @Insert
    suspend fun insertChoice(choice: Choice)

    @Query("SELECT * FROM choices WHERE at >= :from AND at < :to ORDER BY at")
    fun observeChoices(from: Instant, to: Instant): Flow<List<Choice>>

    @Query("SELECT packageName, MAX(at) AS at FROM choices WHERE source = 'PAUSE' GROUP BY packageName")
    suspend fun lastAsked(): List<LastAsked>

    @Upsert
    suspend fun upsertKeynote(keynote: Keynote)

    @Query("SELECT * FROM keynotes WHERE day = :day")
    fun observeKeynote(day: LocalDate): Flow<Keynote?>

    @Query("SELECT * FROM keynotes WHERE day < :day ORDER BY day DESC LIMIT 1")
    suspend fun latestKeynoteBefore(day: LocalDate): Keynote?
}
