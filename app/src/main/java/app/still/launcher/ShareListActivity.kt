package app.still.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.still.launcher.productivity.MAX_PASTED_CHARS

/**
 * "Import as plan" in Android's Share menu. Hands the shared text, e.g. an AI chat's reply, to
 * STILL, which reads it into the plan editor, and closes; nothing is saved until the person taps Create.
 */
class ShareListActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.take(MAX_PASTED_CHARS)
        if (!text.isNullOrBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_PASTED_LIST, text),
            )
        }
        finish()
    }
}
