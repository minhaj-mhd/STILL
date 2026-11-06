package app.still.launcher.data.read

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

enum class BookFormat { PDF, EPUB }

enum class BookStatus { TO_READ, READING, FINISHED }

/**
 * A book or PDF in STILL Read. Its file is a copy kept in STILL's own storage. A daily read is a
 * book too, from a [ReadSeries]; its file can be fetched again from Google Drive when it's gone.
 */
@Entity(tableName = "books", indices = [Index("seriesId"), Index("driveFileId")])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val format: BookFormat,
    /** The copy's name in STILL's books folder. */
    val fileName: String,
    val addedAt: Instant = Instant.now(),
    val status: BookStatus = BookStatus.TO_READ,
    /** How far through, from 0 to 1. */
    val progress: Double = 0.0,
    /** Where to open again: the page for a PDF, Readium's locator as JSON for an EPUB. */
    val position: String? = null,
    /** Where that is in words: a PDF's page, or an EPUB's chapter. */
    val positionLabel: String? = null,
    val pageCount: Int? = null,
    val lastReadAt: Instant? = null,
    val finishedAt: Instant? = null,
    /** The series a daily read comes from; null for the library's own books. */
    val seriesId: Long? = null,
    /** A daily read's file in Google Drive, and that file's type there. */
    val driveFileId: String? = null,
    val driveMimeType: String? = null,
    /** When a daily read landed in Drive. */
    val arrivedAt: Instant? = null,
    /** A daily read's opening words, shown before it's opened. */
    val excerpt: String? = null,
    /** About how many minutes it takes to read. */
    val minutes: Int? = null,
    /** Whether the file is on the phone; older daily reads are fetched again when opened. */
    @ColumnInfo(defaultValue = "1") val onDevice: Boolean = true,
) {
    val isDaily: Boolean get() = seriesId != null
}

/**
 * Daily reads from one ChatGPT scheduled chat: the Google Drive folder the chat saves them to,
 * which STILL looks in for new ones.
 */
@Entity(tableName = "read_series", indices = [Index(value = ["folderId"], unique = true)])
data class ReadSeries(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: String,
    val folderName: String,
    val addedAt: Instant = Instant.now(),
    /** When new reads usually land, in minutes after midnight; learned from the folder. */
    val landsAtMinute: Int? = null,
    val lastCheckedAt: Instant? = null,
)

/** How many reads a series has. */
data class SeriesCount(val seriesId: Long, val count: Int)

/** Time spent reading one book on one day. */
@Entity(tableName = "reading_sessions", indices = [Index("bookId"), Index("date")])
data class ReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val date: LocalDate,
    val startedAt: Instant,
    val millis: Long,
)

/** A book's reading time so far. */
data class BookTime(val bookId: Long, val millis: Long)

/** Reading time on one day, every book together. */
data class DayReading(val date: LocalDate, val millis: Long)

@Dao
interface ReadDao {

    /** The library's own books; daily reads are listed by series. */
    @Query("SELECT * FROM books WHERE seriesId IS NULL ORDER BY COALESCE(lastReadAt, addedAt) DESC")
    fun observeBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE seriesId IS NOT NULL AND arrivedAt >= :since ORDER BY arrivedAt DESC")
    fun observeDailyReadsSince(since: Instant): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY arrivedAt DESC")
    fun observeSeriesReads(seriesId: Long): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId")
    suspend fun seriesReads(seriesId: Long): List<Book>

    @Query("SELECT * FROM books WHERE seriesId IS NOT NULL AND onDevice = 1")
    suspend fun dailyReadsOnDevice(): List<Book>

    @Insert
    suspend fun insertBooks(books: List<Book>)

    @Query("DELETE FROM books WHERE seriesId = :seriesId")
    suspend fun deleteSeriesReads(seriesId: Long)

    @Query("SELECT seriesId, COUNT(*) AS count FROM books WHERE seriesId IS NOT NULL GROUP BY seriesId")
    fun observeSeriesCounts(): Flow<List<SeriesCount>>

    @Query("SELECT * FROM read_series ORDER BY addedAt")
    fun observeSeries(): Flow<List<ReadSeries>>

    @Query("SELECT * FROM read_series ORDER BY addedAt")
    suspend fun allSeries(): List<ReadSeries>

    @Query("SELECT * FROM read_series WHERE id = :id")
    suspend fun getSeries(id: Long): ReadSeries?

    @Insert
    suspend fun insertSeries(series: ReadSeries): Long

    @Update
    suspend fun updateSeries(series: ReadSeries)

    @Query("DELETE FROM read_series WHERE id = :id")
    suspend fun deleteSeries(id: Long)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): Book?

    @Insert
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteSessions(bookId: Long)

    @Insert
    suspend fun insertSession(session: ReadingSession)

    @Query("SELECT bookId, SUM(millis) AS millis FROM reading_sessions GROUP BY bookId")
    fun observeBookTimes(): Flow<List<BookTime>>

    @Query("SELECT date, SUM(millis) AS millis FROM reading_sessions WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<DayReading>>

    @Query("SELECT COALESCE(SUM(millis), 0) FROM reading_sessions WHERE date = :date")
    suspend fun millisOn(date: LocalDate): Long
}
