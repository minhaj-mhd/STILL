package app.still.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat

/**
 * "Add to STILL Read" in Android's Share menu. Hands the shared PDF or EPUB to STILL, which
 * copies it into the library and shows the Read tab, and closes.
 */
class ShareBookActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }
        if (uri != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(MainActivity.EXTRA_IMPORT_BOOK, true),
            )
        }
        finish()
    }
}
