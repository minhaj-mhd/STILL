package app.still.launcher.read

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/**
 * Opens EPUBs with the Readium toolkit. Books are local files, so nothing goes online; the HTTP
 * client is only what Readium's parser expects to be handed.
 */
class Publications(context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(
        DefaultPublicationParser(context.applicationContext, httpClient, assetRetriever, pdfFactory = null),
    )

    /** The publication in [file], or null if it can't be read as one. Close it when done. */
    suspend fun open(file: File): Publication? = withContext(Dispatchers.IO) {
        val asset = assetRetriever.retrieve(file).getOrElse { return@withContext null }
        opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            null
        }
    }
}
