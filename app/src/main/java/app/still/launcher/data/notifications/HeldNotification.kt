package app.still.launcher.data.notifications

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A notification the filter took out of the shade, kept so it can be read later. Keyed by the
 * notification's own key, so an app updating a notification replaces it instead of adding one.
 */
@Entity(tableName = "held_notifications", indices = [Index("postedAt")])
data class HeldNotification(
    @PrimaryKey val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Instant,
)

@Dao
interface HeldNotificationDao {

    /** Newest first. */
    @Query("SELECT * FROM held_notifications ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<HeldNotification>>

    @Query("SELECT COUNT(*) FROM held_notifications")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(notification: HeldNotification)

    @Query("DELETE FROM held_notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM held_notifications WHERE packageName = :packageName")
    suspend fun deleteForPackage(packageName: String)

    @Query("DELETE FROM held_notifications WHERE postedAt < :before")
    suspend fun deleteOlderThan(before: Instant)
}
