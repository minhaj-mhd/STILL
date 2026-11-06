package app.still.launcher.data

import android.content.Context
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.BookFormat
import app.still.launcher.data.read.BookStatus
import app.still.launcher.data.read.ReadDao
import app.still.launcher.data.read.ReadSeries
import app.still.launcher.drive.DriveAccess
import app.still.launcher.drive.DriveAuth
import app.still.launcher.drive.DriveClient
import app.still.launcher.drive.DriveException
import app.still.launcher.drive.DriveFile
import app.still.launcher.drive.DriveFolder
import app.still.launcher.drive.DriveProblem
import app.still.launcher.read.DailyReadsSchedule
import app.still.launcher.read.NotAWordDocument
import app.still.launcher.read.Publications
import app.still.launcher.read.arrivalOf
import app.still.launcher.read.excerptOf
import app.still.launcher.read.firstHeadingOf
import app.still.launcher.read.headlineOf
import app.still.launcher.read.isOnlyDate
import app.still.launcher.read.keepFrom
import app.still.launcher.read.minutesToRead
import app.still.launcher.read.nextSeriesCheck
import app.still.launcher.read.proseOfHtml
import app.still.launcher.read.readTitle
import app.still.launcher.read.readWord
import app.still.launcher.read.saveEpub
import app.still.launcher.read.scheduledSaves
import app.still.launcher.read.textOfHtml
import app.still.launcher.read.usualLandingMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** What a call that needs Drive came to. */
sealed interface DriveResult<out T> {
    data class Done<T>(val value: T) : DriveResult<T>

    /** Google wants the person again before STILL can reach Drive. */
    data object NeedsYou : DriveResult<Nothing>

    data class Failed(val problem: DriveProblem) : DriveResult<Nothing>
}

/** What's in a folder before it becomes a series. */
data class FolderPreview(val reads: Int, val latest: Instant?, val landsAtMinute: Int?)

/** An EPUB's words: all of them, those outside headings, and its first heading. */
private data class EpubText(val all: String, val prose: String, val firstHeading: String?)

/**
 * Daily reads: each ChatGPT scheduled chat saves its reads to a Google Drive folder, and each
 * folder is a [ReadSeries] here. STILL looks for new reads a few minutes after each series is due,
 * keeps the latest days' reads on the phone to read offline, and fetches older ones when asked.
 * The reads are [Book]s, so the reader, reading time and the daily goal treat them like any book.
 * Google Docs come down as EPUBs and Word files are turned into them here, so both open in the
 * EPUB reader.
 */
class DailyReadsRepository(
    context: Context,
    private val dao: ReadDao,
    private val drive: DriveClient,
    private val auth: DriveAuth,
    private val publications: Publications,
    private val preferences: LauncherPreferences,
    private val schedule: DailyReadsSchedule,
) {
    private val folder = File(context.applicationContext.filesDir, ReadRepository.BOOKS_FOLDER)
    private val syncing = Mutex()

    val series: Flow<List<ReadSeries>> = dao.observeSeries()

    /** How many reads each series has, by series id. */
    val counts: Flow<Map<Long, Int>> = dao.observeSeriesCounts().map { counts -> counts.associate { it.seriesId to it.count } }

    fun reads(seriesId: Long): Flow<List<Book>> = dao.observeSeriesReads(seriesId)

    fun readsSince(since: Instant): Flow<List<Book>> = dao.observeDailyReadsSince(since)

    /** Remembers the account [token] belongs to, so later checks use it without asking. */
    suspend fun connect(token: String): DriveResult<String> = try {
        val email = drive.email(token) ?: return DriveResult.Failed(DriveProblem.OTHER)
        preferences.setDriveAccount(email)
        sync()
        DriveResult.Done(email)
    } catch (e: IOException) {
        DriveResult.Failed(problemOf(e))
    }

    /** Stops reaching Drive. Series and the reads on the phone stay, for connecting again. */
    suspend fun disconnect() {
        preferences.current().driveAccount?.let { auth.revoke(it) }
        preferences.setDriveAccount(null)
        schedule.stop()
    }

    suspend fun folders(): DriveResult<List<DriveFolder>> = withDrive { token -> drive.folders(token) }

    suspend fun preview(folderId: String): DriveResult<FolderPreview> = withDrive { token ->
        val files = drive.readsIn(token, folderId)
        FolderPreview(files.size, files.maxOfOrNull { it.createdAt }, landingOf(files, ZoneId.systemDefault()))
    }

    /** Makes [folder] a series called [name] and fetches its latest reads; gives the series' id. */
    suspend fun addSeries(folder: DriveFolder, name: String): DriveResult<Long> {
        val existing = dao.allSeries().firstOrNull { it.folderId == folder.id }
        val id = existing?.id ?: dao.insertSeries(ReadSeries(name = name.trim().ifBlank { folder.name }, folderId = folder.id, folderName = folder.name))
        return when (val result = sync()) {
            is DriveResult.Done -> DriveResult.Done(id)
            DriveResult.NeedsYou -> DriveResult.NeedsYou
            is DriveResult.Failed -> result
        }
    }

    suspend fun rename(series: ReadSeries, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.updateSeries(series.copy(name = trimmed))
    }

    /** Takes the series out of STILL, with its reads; what's in Drive stays. */
    suspend fun removeSeries(series: ReadSeries) = withContext(Dispatchers.IO) {
        dao.seriesReads(series.id).forEach { File(folder, it.fileName).delete() }
        dao.deleteSeriesReads(series.id)
        dao.deleteSeries(series.id)
        planChecks()
    }

    suspend fun setKeepDays(days: Int) {
        preferences.setDailyKeepDays(days)
        prune(LocalDate.now(), days)
    }

    /** Looks for new reads when the last look was more than [maxAge] ago. */
    suspend fun syncIfStale(maxAge: Duration = STALE_AFTER) {
        val settings = preferences.current()
        if (settings.driveAccount == null || settings.driveNeedsYou) return
        if (System.currentTimeMillis() - settings.dailyCheckedAt < maxAge.toMillis()) return
        if (dao.allSeries().isEmpty()) return
        sync()
    }

    /**
     * Looks in every series' folder: adds new reads, fetches the days kept on the phone, lets go of
     * older copies, and plans the next look. Gives how many reads were new.
     */
    suspend fun sync(): DriveResult<Int> = syncing.withLock {
        if (preferences.current().driveAccount == null) return@withLock DriveResult.Done(0)
        val all = dao.allSeries()
        if (all.isEmpty()) {
            schedule.stop()
            return@withLock DriveResult.Done(0)
        }
        val result = withDrive { token -> all.sumOf { syncSeries(token, it) } }
        if (result is DriveResult.Done) preferences.setDailyChecked(System.currentTimeMillis(), result.value)
        planChecks()
        result
    }

    /** Fetches a read that isn't on the phone, such as an older one, so it can be opened. */
    suspend fun download(book: Book): DriveResult<Book> {
        val current = dao.getBook(book.id) ?: return DriveResult.Failed(DriveProblem.OTHER)
        if (current.onDevice && File(folder, current.fileName).exists()) return DriveResult.Done(current)
        return withDrive { token -> fetch(token, current) }
    }

    private suspend fun syncSeries(token: String, series: ReadSeries): Int {
        val zone = ZoneId.systemDefault()
        val files = drive.readsIn(token, series.folderId)
        val known = dao.seriesReads(series.id).associateBy { it.driveFileId }
        val new = files.filter { it.id !in known }
        if (new.isNotEmpty()) dao.insertBooks(new.map { readOf(series, it) })
        files.forEach { file ->
            val book = known[file.id] ?: return@forEach
            // A title taken from the read's own heading stays while its name is only a date.
            val headline = headlineOf(file.name)
            val title = if (isOnlyDate(headline) && !isOnlyDate(book.title)) book.title else headline
            val arrived = arrivalOf(file.name, file.createdAt, zone)
            if (book.title != title || book.arrivedAt != arrived) dao.updateBook(book.copy(title = title, arrivedAt = arrived))
        }
        // Reads taken out of the folder go too, unless they've been opened or are on the phone.
        val inDrive = files.mapTo(HashSet()) { it.id }
        known.values
            .filter { it.driveFileId !in inDrive && !it.onDevice && it.status == BookStatus.TO_READ }
            .forEach { dao.deleteBook(it.id) }
        dao.updateSeries(
            series.copy(
                landsAtMinute = if (files.isEmpty()) series.landsAtMinute else landingOf(files, zone),
                lastCheckedAt = Instant.now(),
            ),
        )

        val today = LocalDate.now(zone)
        val keepDays = preferences.current().dailyKeepDays
        val from = keepFrom(today, keepDays).atStartOfDay(zone).toInstant()
        dao.seriesReads(series.id)
            .filter { !it.onDevice && (it.arrivedAt ?: Instant.EPOCH) >= from }
            .forEach { read ->
                try {
                    fetch(token, read)
                } catch (e: DriveException) {
                    if (e.code == UNAUTHORIZED) throw e
                } catch (_: NotAWordDocument) {
                    // One unreadable file doesn't hold the others up; opening it says why.
                }
            }
        retitle(series)
        prune(today, keepDays)
        return new.size
    }

    /** When [files] usually land, going by the ones saved on the chat's schedule. */
    private fun landingOf(files: List<DriveFile>, zone: ZoneId): Int? =
        usualLandingMinute(scheduledSaves(files.map { it.name to it.createdAt }, zone), zone)

    /** Names reads on the phone that are still called by their date after their first heading. */
    private suspend fun retitle(series: ReadSeries) {
        dao.seriesReads(series.id)
            .filter { it.onDevice && it.format == BookFormat.EPUB && isOnlyDate(it.title) }
            .forEach { read ->
                val file = File(folder, read.fileName)
                if (!file.exists()) return@forEach
                val described = describe(read, file)
                if (described != read) dao.updateBook(described)
            }
    }

    private fun readOf(series: ReadSeries, file: DriveFile): Book {
        val format = if (file.mimeType == DriveClient.PDF) BookFormat.PDF else BookFormat.EPUB
        return Book(
            title = headlineOf(file.name),
            format = format,
            fileName = "daily-${file.id}.${format.name.lowercase()}",
            seriesId = series.id,
            driveFileId = file.id,
            driveMimeType = file.mimeType,
            arrivedAt = arrivalOf(file.name, file.createdAt, ZoneId.systemDefault()),
            onDevice = false,
        )
    }

    /** Downloads [book]'s file and notes its opening words and length. */
    private suspend fun fetch(token: String, book: Book): Book {
        val fileId = book.driveFileId ?: throw IOException("Not from Drive")
        folder.mkdirs()
        val file = File(folder, book.fileName)
        val type = book.driveMimeType ?: DriveClient.GOOGLE_DOC
        if (type == DriveClient.WORD) {
            val word = File(folder, book.fileName + ".docx")
            try {
                drive.download(token, fileId, type, word)
                withContext(Dispatchers.IO) {
                    val body = readWord(word)
                    saveEpub(file, readTitle(book.title, body.headings.firstOrNull()?.text), body, Locale.getDefault().language)
                }
            } finally {
                word.delete()
            }
        } else {
            drive.download(token, fileId, type, file)
        }
        // Where it's been read to may have changed while it came down.
        val current = dao.getBook(book.id) ?: book
        val described = describe(current, file).copy(onDevice = true)
        dao.updateBook(described)
        return described
    }

    private suspend fun describe(book: Book, file: File): Book = when (book.format) {
        BookFormat.PDF -> book.copy(pageCount = withContext(Dispatchers.IO) { pdfPageCount(file) })
        BookFormat.EPUB -> {
            val text = textOf(file)
            if (text == null || text.all.isBlank()) {
                book
            } else {
                val title = readTitle(book.title, text.firstHeading)
                book.copy(
                    title = title,
                    excerpt = excerptOf(text.prose.ifBlank { text.all }, title),
                    minutes = minutesToRead(text.all),
                )
            }
        }
    }

    /** The words of an EPUB, chapter after chapter, and its first heading. */
    private suspend fun textOf(file: File): EpubText? {
        val publication = publications.open(file) ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val all = mutableListOf<String>()
                val prose = mutableListOf<String>()
                var firstHeading: String? = null
                for (link in publication.readingOrder.take(MAX_CHAPTERS)) {
                    val resource = publication.get(link) ?: continue
                    try {
                        val page = resource.read().getOrNull()?.decodeToString() ?: continue
                        all += textOfHtml(page)
                        prose += proseOfHtml(page)
                        if (firstHeading == null) firstHeading = firstHeadingOf(page)
                    } finally {
                        resource.close()
                    }
                }
                EpubText(all.joinToString(" "), prose.joinToString(" "), firstHeading)
            }
        } finally {
            publication.close()
        }
    }

    /** Lets go of copies older than the days kept, unless they're being read. */
    private suspend fun prune(today: LocalDate, keepDays: Int) = withContext(Dispatchers.IO) {
        val from = keepFrom(today, keepDays).atStartOfDay(ZoneId.systemDefault()).toInstant()
        dao.dailyReadsOnDevice()
            .filter { (it.arrivedAt ?: Instant.EPOCH) < from && it.status != BookStatus.READING && (it.lastReadAt ?: Instant.EPOCH) < from }
            .forEach { read ->
                File(folder, read.fileName).delete()
                dao.updateBook(read.copy(onDevice = false))
            }
    }

    /** Sets the next look for whichever series is due first, and the regular look besides. */
    suspend fun planChecks() {
        val all = dao.allSeries()
        if (all.isEmpty() || preferences.current().driveAccount == null) {
            schedule.stop()
            return
        }
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val next = all.mapNotNull { series ->
            val arrivedToday = dao.seriesReads(series.id).any { it.arrivedAt?.atZone(zone)?.toLocalDate() == now.toLocalDate() }
            nextSeriesCheck(now, series.landsAtMinute, arrivedToday)
        }.minOrNull()
        schedule.keepChecking()
        if (next != null) schedule.checkAt(next.toInstant()) else schedule.cancelNext()
    }

    /** Runs [block] with a Drive token, asking again once if Drive turns the token down. */
    private suspend fun <T> withDrive(block: suspend (String) -> T): DriveResult<T> {
        repeat(2) { attempt ->
            val token = when (val access = auth.access(preferences.current().driveAccount)) {
                is DriveAccess.Granted -> access.token
                is DriveAccess.NeedsYou -> {
                    preferences.setDriveNeedsYou(true)
                    return DriveResult.NeedsYou
                }
                is DriveAccess.Failed -> return DriveResult.Failed(access.reason)
            }
            try {
                return DriveResult.Done(block(token))
            } catch (e: DriveException) {
                if (e.code != UNAUTHORIZED || attempt > 0) return DriveResult.Failed(problemOf(e))
                auth.clear(token)
            } catch (e: IOException) {
                return DriveResult.Failed(problemOf(e))
            }
        }
        return DriveResult.Failed(DriveProblem.OTHER)
    }

    private fun problemOf(e: IOException): DriveProblem = when {
        e is NotAWordDocument -> DriveProblem.UNREADABLE
        e is DriveException && e.code == FORBIDDEN -> DriveProblem.SETUP
        e is DriveException -> DriveProblem.OTHER
        else -> DriveProblem.OFFLINE
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(15)
        const val MAX_CHAPTERS = 40
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
    }
}
