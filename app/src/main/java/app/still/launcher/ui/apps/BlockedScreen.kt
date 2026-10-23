package app.still.launcher.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.components.formatDateTime
import java.time.Instant

/** Shown instead of opening a blocked app: what's blocked, until when, and a way out. */
@Composable
fun BlockedScreen(appLabel: String, blockedUntil: Instant, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, alignment = androidx.compose.ui.Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(stringResource(R.string.blocked_title, appLabel), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.blocked_body, formatDateTime(blockedUntil)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.blocked_close)) }
        }
    }
}
