package app.still.launcher.ui.limit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.limit.LimitState
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.TickRing
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.theme.stillPalette

/**
 * Shown instead of an app once the day's phone limit is used up: when everything opens again,
 * the apps that are allowed now, and a way home. An emergency opening, while any are left,
 * waits through a countdown first.
 */
@Composable
fun LimitScreen(
    app: LauncherApp,
    limit: LimitState,
    allowedApps: List<LauncherApp>,
    emergenciesLeft: Int,
    onOpenAllowed: (LauncherApp) -> Unit,
    onEmergency: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val palette = stillPalette
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            TickRing(fraction = 1f, lit = palette.danger, unlit = palette.faint, modifier = Modifier.size(112.dp))
            Text(stringResource(R.string.limit_screen_title, app.label), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.limit_screen_body, formatDuration(limit.limit), resetText(limit)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                FieldLabel(stringResource(R.string.limit_open_now))
                if (allowedApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.limit_no_allowed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                allowedApps.forEach { allowed ->
                    Text(
                        text = allowed.label,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAllowed(allowed) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) { Text(stringResource(R.string.limit_go_home)) }
            if (emergenciesLeft > 0) {
                TextButton(onClick = onEmergency) {
                    Text(pluralStringResource(R.plurals.limit_emergency, emergenciesLeft, emergenciesLeft))
                }
            }
        }
    }
}

/** "midnight", or the time the next day starts, e.g. "04:00". */
@Composable
fun resetText(limit: LimitState): String =
    if (limit.resetsAt.hour == 0 && limit.resetsAt.minute == 0) stringResource(R.string.limit_midnight)
    else formatTime(limit.resetsAt.toLocalTime())
