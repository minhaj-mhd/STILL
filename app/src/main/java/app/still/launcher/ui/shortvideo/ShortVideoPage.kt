package app.still.launcher.ui.shortvideo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.shortvideo.BROWSERS
import app.still.launcher.shortvideo.BROWSERS_ID
import app.still.launcher.shortvideo.SHORT_VIDEO_RULES
import app.still.launcher.shortvideo.nameRes
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.SwitchRow

/**
 * Settings > Short videos. Says what the service reads, and that it's apart from Precision mode,
 * before sending you to Android's Accessibility settings: Google Play asks for this disclosure and
 * an explicit agreement first. Once it's on, a switch for each app.
 */
@Composable
fun ShortVideoPage(
    apps: List<LauncherApp>,
    onBack: () -> Unit,
    onOpenAppInfo: () -> Unit,
    viewModel: ShortVideoViewModel = viewModel(factory = ShortVideoViewModel.Factory),
) {
    val context = LocalContext.current
    val running by viewModel.running.collectAsStateWithLifecycle()
    val rulesOff by viewModel.rulesOff.collectAsStateWithLifecycle()
    var switchedOn by remember { mutableStateOf(viewModel.isSwitchedOn()) }
    LifecycleResumeEffect(Unit) {
        switchedOn = viewModel.isSwitchedOn()
        onPauseOrDispose { }
    }
    val installed = remember(apps) { apps.mapTo(HashSet()) { it.component.packageName } }

    EditorScaffold(title = stringResource(R.string.short_videos_title), onDone = onBack) {
        Text(stringResource(R.string.short_videos_intro), style = MaterialTheme.typography.bodyLarge)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.precision_what_it_does), Modifier.padding(bottom = 4.dp))
            Text(stringResource(R.string.short_videos_does_leave), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.short_videos_does_browsers), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.short_videos_does_anywhere), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.short_videos_does_updates), style = MaterialTheme.typography.bodyLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.precision_what_it_sees), Modifier.padding(bottom = 4.dp))
            Text(stringResource(R.string.short_videos_sees), style = MaterialTheme.typography.bodyLarge)
        }

        if (running) {
            Text(stringResource(R.string.short_videos_on), style = MaterialTheme.typography.titleMedium)
            SHORT_VIDEO_RULES.forEach { rule ->
                SwitchRow(
                    title = stringResource(rule.nameRes),
                    detail = if (rule.packages.none { it in installed }) stringResource(R.string.short_videos_not_installed) else null,
                    checked = rule.id !in rulesOff,
                    onCheckedChange = { viewModel.setBlocks(rule, it) },
                )
            }
            SwitchRow(
                title = stringResource(R.string.short_videos_browsers),
                detail = stringResource(
                    if (BROWSERS.none { it.packageName in installed }) R.string.short_videos_not_installed else R.string.short_videos_browsers_detail,
                ),
                checked = BROWSERS_ID !in rulesOff,
                onCheckedChange = viewModel::setBlocksInBrowsers,
            )
            OutlinedButton(onClick = viewModel::turnOff) { Text(stringResource(R.string.precision_turn_off)) }
        } else {
            if (switchedOn) Text(stringResource(R.string.short_videos_stuck), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { context.startActivity(viewModel.settingsIntent()) }) { Text(stringResource(R.string.precision_agree)) }
            Hint(stringResource(R.string.short_videos_steps))
            Hint(stringResource(R.string.precision_restricted))
            TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.precision_app_info)) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
