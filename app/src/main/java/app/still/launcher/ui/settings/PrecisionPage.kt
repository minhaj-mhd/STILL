package app.still.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.editor.EditorScaffold

/**
 * Explains Precision mode, and what it can and can't see, before sending you to Android's
 * Accessibility settings to turn it on. Google Play asks for this disclosure and an explicit
 * agreement before an app leads the user there.
 */
@Composable
fun PrecisionPage(
    isOn: Boolean,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDone: () -> Unit,
) {
    EditorScaffold(title = stringResource(R.string.precision_title), onDone = onDone) {
        Text(stringResource(R.string.precision_intro), style = MaterialTheme.typography.bodyLarge)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.precision_what_it_does), Modifier.padding(bottom = 4.dp))
            Text(stringResource(R.string.precision_does_everywhere), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.precision_does_time_up), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.precision_does_lock), style = MaterialTheme.typography.bodyLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.precision_what_it_sees), Modifier.padding(bottom = 4.dp))
            Text(stringResource(R.string.precision_sees), style = MaterialTheme.typography.bodyLarge)
        }

        if (isOn) {
            Text(stringResource(R.string.precision_on), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onTurnOff) { Text(stringResource(R.string.precision_turn_off)) }
        } else {
            Button(onClick = onTurnOn) { Text(stringResource(R.string.precision_agree)) }
            Text(
                text = stringResource(R.string.precision_steps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.precision_restricted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.precision_app_info)) }
        }
    }
}
