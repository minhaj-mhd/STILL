package app.still.launcher.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** A folder in Drive. */
data class DriveFolder(val id: String, val name: String, val modifiedAt: Instant?)

/** A file in Drive that STILL can read: a Google Doc, a Word document, a PDF or an EPUB. */
data class DriveFile(val id: String, val name: String, val mimeType: String, val createdAt: Instant)

/** Drive answered with an error; [code] is its HTTP status, 401 for a token it no longer takes. */
class DriveException(val code: Int, message: String) : IOException(message)

/**
 * The few Drive v3 REST calls daily reads need, read-only. Google Docs come down as EPUBs, which
 * Drive converts them to, so they open in STILL Read's EPUB reader.
 */
class DriveClient {

    /** The signed-in account's address. */
    suspend fun email(token: String): String? =
        getJson(token, "$API/about?fields=user(emailAddress)").optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }

    /** The person's folders, most recently changed first. */
    suspend fun folders(token: String): List<DriveFolder> {
        val query = "mimeType = '$FOLDER' and trashed = false"
        return pages(token, query, "files(id,name,modifiedTime)", "modifiedTime desc", MAX_FOLDERS) { file ->
            DriveFolder(file.getString("id"), file.optString("name"), file.optString("modifiedTime").toInstantOrNull())
        }
    }

    /** Everything readable in [folderId], newest first. */
    suspend fun readsIn(token: String, folderId: String): List<DriveFile> {
        val types = READABLE.joinToString(" or ") { "mimeType = '$it'" }
        val query = "'${folderId.replace("'", "\\'")}' in parents and trashed = false and ($types)"
        return pages(token, query, "files(id,name,mimeType,createdTime)", "createdTime desc", MAX_READS) { file ->
            val created = file.optString("createdTime").toInstantOrNull() ?: return@pages null
            DriveFile(file.getString("id"), file.optString("name"), file.getString("mimeType"), created)
        }
    }

    /** Saves [fileId] to [into]: a Google Doc as an EPUB, anything else as it is. */
    suspend fun download(token: String, fileId: String, mimeType: String, into: File) = withContext(Dispatchers.IO) {
        val id = URLEncoder.encode(fileId, UTF_8)
        val url = if (mimeType == GOOGLE_DOC) "$API/files/$id/export?mimeType=${URLEncoder.encode(EPUB, UTF_8)}" else "$API/files/$id?alt=media"
        val connection = open(token, url)
        try {
            check(connection)
            val partial = File(into.parentFile, into.name + ".part")
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(into)) {
                partial.delete()
                throw IOException("Couldn't save ${into.name}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> pages(
        token: String,
        query: String,
        fields: String,
        orderBy: String,
        max: Int,
        parse: (JSONObject) -> T?,
    ): List<T> {
        val found = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$API/files?q=").append(URLEncoder.encode(query, UTF_8))
                append("&fields=").append(URLEncoder.encode("nextPageToken,$fields", UTF_8))
                append("&orderBy=").append(URLEncoder.encode(orderBy, UTF_8))
                append("&pageSize=$PAGE_SIZE")
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, UTF_8)) }
            }
            val page = getJson(token, url)
            val files = page.optJSONArray("files")
            if (files != null) for (i in 0 until files.length()) parse(files.getJSONObject(i))?.let(found::add)
            pageToken = page.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null && found.size < max)
        return found
    }

    private suspend fun getJson(token: String, url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = open(token, url)
        try {
            check(connection)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun open(token: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun check(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            throw DriveException(code, "Drive answered $code ${detail.take(200)}")
        }
    }

    companion object {
        const val GOOGLE_DOC = "application/vnd.google-apps.document"
        const val PDF = "application/pdf"
        const val EPUB = "application/epub+zip"
        const val WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val FOLDER = "application/vnd.google-apps.folder"
        private val READABLE = listOf(GOOGLE_DOC, WORD, PDF, EPUB)
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UTF_8 = "UTF-8"
        private const val PAGE_SIZE = 100
        private const val MAX_FOLDERS = 300
        private const val MAX_READS = 2_000
        private const val TIMEOUT_MILLIS = 30_000
    }
}

private fun String.toInstantOrNull(): Instant? = takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
