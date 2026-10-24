package app.still.launcher.ui.keynote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.focus.FocusSession
import app.still.launcher.ui.components.rememberCurrentMinute
import app.still.launcher.ui.theme.stillPalette

/**
 * On home after a while away during a focus session: how much is left and the next small step.
 * Coming back is the point, so it never says the session was broken.
 */
@Composable
fun WelcomeBackCard(session: FocusSession, onBackToIt: () -> Unit, onEnd: () -> Unit) {
    val now by rememberCurrentMinute()
    val minutesLeft = ((session.endMillis - now.toInstant().toEpochMilli() + MINUTE - 1) / MINUTE).toInt().coerceAtLeast(1)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.titleMedium, color = stillPalette.accent)
            Text(pluralStringResource(R.plurals.welcome_back_left, minutesLeft, minutesLeft, session.title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.welcome_back_next), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onEnd) { Text(stringResource(R.string.welcome_back_end)) }
                Button(
                    onClick = onBackToIt,
                    colors = ButtonDefaults.buttonColors(containerColor = stillPalette.accent, contentColor = MaterialTheme.colorScheme.background),
                ) { Text(stringResource(R.string.welcome_back_resume)) }
            }
        }
    }
}

private const val MINUTE = 60_000L
