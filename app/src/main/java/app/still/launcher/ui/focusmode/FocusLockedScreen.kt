package app.still.launcher.ui.focusmode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.theme.stillPalette

/** Shown instead of an app focus mode keeps closed. The only way on is home. */
@Composable
fun FocusLockedScreen(appLabel: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(stringResource(R.string.focus_mode_label), style = MaterialTheme.typography.titleMedium, color = stillPalette.accent)
            Text(stringResource(R.string.focus_locked_title, appLabel), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.focus_locked_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) { Text(stringResource(R.string.limit_go_home)) }
        }
    }
}
