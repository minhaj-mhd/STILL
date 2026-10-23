package app.still.launcher.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.AppScreenTime
import app.still.launcher.ui.components.formatDuration

/**
 * Precision mode puts this in front of an app when the planned time runs out. Leaving is the
 * bigger button, and Back leaves too; "More time" asks how long again.
 */
@Composable
fun TimeUpScreen(
    appLabel: String,
    plannedMinutes: Int,
    screenTime: AppScreenTime?,
    onLeave: () -> Unit,
    onMoreTime: () -> Unit,
) {
    BackHandler(onBack = onLeave)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(stringResource(R.string.time_reminder_time_up, appLabel), style = MaterialTheme.typography.headlineMedium)
            if (plannedMinutes > 0) {
                Text(
                    text = stringResource(R.string.time_reminder_planned, plannedMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            screenTime?.let {
                Text(
                    text = stringResource(R.string.time_ask_today, formatDuration(it.today)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onLeave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                ) { Text(stringResource(R.string.time_reminder_leave, appLabel)) }
                OutlinedButton(onClick = onMoreTime) { Text(stringResource(R.string.time_up_more)) }
            }
        }
    }
}
