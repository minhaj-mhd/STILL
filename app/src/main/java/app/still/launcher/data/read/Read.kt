package app.still.launcher.data.read

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

/** A book or PDF in STILL Read. Its file is a copy kept in STILL's own storage. */
@Entity(tableName = "books")
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
)

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

    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAt, addedAt) DESC")
    fun observeBooks(): Flow<List<Book>>

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
