package app.still.launcher.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.BookFormat
import app.still.launcher.data.read.BookStatus
import app.still.launcher.data.read.BookTime
import app.still.launcher.data.read.DayReading
import app.still.launcher.data.read.ReadDao
import app.still.launcher.data.read.ReadingSession
import app.still.launcher.read.Publications
import app.still.launcher.read.readingMinutes
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.occurrencesBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * STILL Read's library: books and PDFs copied into STILL's own storage, where you are in each,
 * and the time spent reading. Reading time can fill a duration habit, as the focus timer does.
 */
class ReadRepository(
    context: Context,
    private val dao: ReadDao,
    private val publications: Publications,
    private val preferences: LauncherPreferences,
    private val productivity: ProductivityRepository,
) {
    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, BOOKS_FOLDER)

    val books: Flow<List<Book>> = dao.observeBooks()

    val bookTimes: Flow<List<BookTime>> = dao.observeBookTimes()

    fun days(from: LocalDate, to: LocalDate): Flow<List<DayReading>> = dao.observeDays(from, to)

    suspend fun book(id: Long): Book? = dao.getBook(id)

    fun fileOf(book: Book): File = File(folder, book.fileName)

    /** Why an import didn't work. */
    enum class ImportError { NOT_A_BOOK, UNREADABLE }

    class ImportFailure(val error: ImportError) : Exception(error.name)

    /**
     * Copies the PDF or EPUB at [uri] into STILL and adds it to To read. The title comes from the
     * book itself where it has one, otherwise from the file's name.
     */
    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val format = formatOf(resolver.getType(uri), name)
            ?: return@withContext Result.failure(ImportFailure(ImportError.NOT_A_BOOK))
        folder.mkdirs()
        val file = File(folder, "${UUID.randomUUID()}.${format.name.lowercase()}")
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!copied) {
            file.delete()
            return@withContext Result.failure(ImportFailure(ImportError.UNREADABLE))
        }
        val fallbackTitle = name.substringBeforeLast(".").ifBlank { name }
        val book = when (format) {
            BookFormat.PDF -> pdfPageCount(file)?.let { Book(title = fallbackTitle, format = format, fileName = file.name, pageCount = it) }
            BookFormat.EPUB -> publications.open(file)?.let { publication ->
                try {
                    Book(
                        title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                        author = publication.metadata.authors.joinToString(", ") { it.localizedName.string }.ifBlank { null },
                        format = format,
                        fileName = file.name,
                    )
                } finally {
                    publication.close()
                }
            }
        }
        if (book == null) {
            file.delete()
            return@withContext Result.failure(ImportFailure(ImportError.UNREADABLE))
        }
        Result.success(book.copy(id = dao.insertBook(book)))
    }

    /** Remembers where [book] is open, and moves it to Reading, or Finished once [atEnd]. */
    suspend fun savePosition(book: Book, position: String, label: String?, progress: Double, atEnd: Boolean) {
        val current = dao.getBook(book.id) ?: return
        val now = Instant.now()
        val status = when {
            atEnd -> BookStatus.FINISHED
            current.status == BookStatus.FINISHED -> BookStatus.FINISHED
            else -> BookStatus.READING
        }
        dao.updateBook(
            current.copy(
                position = position,
                positionLabel = label,
                progress = progress.coerceIn(0.0, 1.0),
                status = status,
                lastReadAt = now,
                finishedAt = if (status == BookStatus.FINISHED) current.finishedAt ?: now else null,
            ),
        )
    }

    suspend fun setStatus(book: Book, status: BookStatus) {
        val current = dao.getBook(book.id) ?: return
        dao.updateBook(current.copy(status = status, finishedAt = if (status == BookStatus.FINISHED) Instant.now() else null))
    }

    /** Takes [book] out of STILL, its copy and reading time with it. */
    suspend fun remove(book: Book) = withContext(Dispatchers.IO) {
        fileOf(book).delete()
        dao.deleteSessions(book.id)
        dao.deleteBook(book.id)
    }

    /** Adds a sitting's reading time to its day, and to the habit reading fills, if there is one. */
    suspend fun addReading(bookId: Long, startedAt: Instant, millis: Long) {
        if (millis <= 0) return
        val date = startedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        dao.insertSession(ReadingSession(bookId = bookId, date = date, startedAt = startedAt, millis = millis))
        fillHabit(date)
    }

    /** Sets the linked duration habit to the day's reading, never lowering what's logged. */
    private suspend fun fillHabit(date: LocalDate) {
        val habitId = preferences.current().readHabitId ?: return
        val habit = productivity.getHabit(habitId)?.takeIf { !it.archived } ?: return
        val occurrence = habit.schedule?.occurrencesBetween(date, date)?.firstOrNull() ?: Occurrence(date)
        val minutes = readingMinutes(dao.millisOn(date))
        val logged = productivity.getHabitProgress(habit.id, occurrence)
        if (minutes > logged) productivity.setHabitProgress(habit, occurrence, minutes, viaTimer = true)
    }

    companion object {
        /** Where books, PDFs and daily reads are kept, inside STILL's own storage. */
        const val BOOKS_FOLDER = "books"

        private fun formatOf(mimeType: String?, name: String): BookFormat? = when {
            mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> BookFormat.PDF
            mimeType == "application/epub+zip" || name.endsWith(".epub", ignoreCase = true) -> BookFormat.EPUB
            else -> null
        }
    }
}

/** How many pages the PDF in [file] has, or null if it can't be read. */
internal fun pdfPageCount(file: File): Int? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { it.pageCount }
    }
}.getOrNull()
