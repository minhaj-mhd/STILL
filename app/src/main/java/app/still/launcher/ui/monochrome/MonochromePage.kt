package app.still.launcher.ui.monochrome

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.blocking.AppPickerSheet
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.SwitchRow
import app.still.launcher.ui.theme.stillPalette

/**
 * Settings > Monochrome: the one-time permission, given from a computer, then when the screen
 * turns grey.
 */
@Composable
fun MonochromePage(
    apps: List<LauncherApp>,
    onBack: () -> Unit,
    viewModel: MonochromeViewModel = viewModel(factory = MonochromeViewModel.Factory),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val precisionRunning by viewModel.precisionRunning.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(viewModel.isAllowed()) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = viewModel.isAllowed()
        if (allowed) viewModel.refresh()
        onPauseOrDispose { }
    }
    val chosenNames = remember(apps, settings.apps) {
        apps.filter { it.component.packageName in settings.apps }.map { it.label }.distinct().sorted()
    }

    EditorScaffold(title = stringResource(R.string.monochrome_title), onDone = onBack) {
        Text(stringResource(R.string.monochrome_intro), style = MaterialTheme.typography.bodyLarge)

        if (allowed) {
            Text(stringResource(R.string.monochrome_allowed), style = MaterialTheme.typography.titleMedium, color = stillPalette.accent)
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, stillPalette.faint, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text(stringResource(R.string.monochrome_setup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.monochrome_setup_body), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(
                        text = viewModel.grantCommand,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.monochrome_not_allowed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = stillPalette.warning,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val command = viewModel.grantCommand
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(command, command))
                            Toast.makeText(context, R.string.monochrome_copied, Toast.LENGTH_SHORT).show()
                        },
                    ) { Text(stringResource(R.string.monochrome_copy)) }
                }
                Hint(stringResource(R.string.monochrome_setup_steps))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.monochrome_grey_when), Modifier.padding(bottom = 4.dp))
            SwitchRow(
                title = stringResource(R.string.monochrome_for_apps),
                detail = when {
                    !precisionRunning -> stringResource(R.string.monochrome_needs_precision)
                    chosenNames.isEmpty() -> stringResource(R.string.monochrome_no_apps)
                    else -> chosenNames.joinToString(", ")
                },
                checked = settings.forApps,
                onCheckedChange = viewModel::setForApps,
            )
            TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.monochrome_choose_apps)) }
            SwitchRow(
                title = stringResource(R.string.monochrome_for_focus),
                detail = null,
                checked = settings.forFocus,
                onCheckedChange = viewModel::setForFocus,
            )
        }

        Hint(stringResource(R.string.monochrome_stuck_hint))
    }

    if (choosing) {
        AppPickerSheet(
            apps = apps,
            usage = emptyMap(),
            selected = settings.apps,
            onChange = viewModel::setApps,
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
