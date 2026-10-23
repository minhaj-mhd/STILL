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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.launcher.R
import app.still.launcher.data.AppScreenTime
import app.still.launcher.ui.components.formatDuration
import kotlinx.coroutines.delay

/**
 * A pause before a chosen app opens. The countdown has to finish before "Open" works, and
 * "Not now" is the bigger button; Back also cancels. [question] replaces "Do you really want
 * to open it?", e.g. for an emergency open.
 */
@Composable
fun LaunchDelayScreen(
    appLabel: String,
    seconds: Int,
    screenTime: AppScreenTime?,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    question: String? = null,
) {
    BackHandler(onBack = onCancel)
    var remaining by rememberSaveable { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(stringResource(R.string.delay_breathe), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = remaining.toString(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
                color = if (remaining > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            )
            Text(question ?: stringResource(R.string.delay_question, appLabel), style = MaterialTheme.typography.headlineSmall)
            screenTime?.let {
                Text(
                    text = stringResource(R.string.time_ask_today, formatDuration(it.today)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                ) { Text(stringResource(R.string.delay_not_now)) }
                OutlinedButton(onClick = onOpen, enabled = remaining == 0) {
                    Text(stringResource(R.string.delay_open, appLabel))
                }
            }
        }
    }
}
