package app.still.launcher.data.blocking

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** An app blocked until [blockedUntil], in every profile. */
@Entity(tableName = "app_blocks")
data class AppBlock(
    @PrimaryKey val packageName: String,
    val blockedUntil: Instant,
    val createdAt: Instant = Instant.now(),
)

@Dao
interface AppBlockDao {

    @Query("SELECT * FROM app_blocks")
    fun observeBlocks(): Flow<List<AppBlock>>

    @Query("SELECT * FROM app_blocks WHERE packageName = :packageName")
    suspend fun getBlock(packageName: String): AppBlock?

    @Upsert
    suspend fun upsert(block: AppBlock)

    @Query("DELETE FROM app_blocks WHERE blockedUntil <= :now")
    suspend fun deleteExpired(now: Instant)
}
