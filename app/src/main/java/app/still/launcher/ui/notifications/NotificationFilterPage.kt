package app.still.launcher.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

/**
 * Settings > Notification filter. Explains what Notification access lets STILL read before
 * sending you to turn it on, then the filter's switch, the apps let through, and the count.
 */
@Composable
fun NotificationFilterPage(
    apps: List<LauncherApp>,
    onOpenHeld: () -> Unit,
    onBack: () -> Unit,
    onOpenAppInfo: () -> Unit,
    viewModel: NotificationFilterViewModel = viewModel(factory = NotificationFilterViewModel.Factory),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val held by viewModel.held.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(viewModel.hasAccess()) }
    LifecycleResumeEffect(Unit) {
        hasAccess = viewModel.hasAccess()
        onPauseOrDispose { }
    }
    var pickingApps by rememberSaveable { mutableStateOf(false) }
    // One row per package; apps that always come through aren't worth choosing.
    val choosable = remember(apps) { apps.filterNot { viewModel.alwaysThrough(it.component.packageName) }.distinctBy { it.component.packageName } }

    EditorScaffold(title = stringResource(R.string.held_settings_title), onDone = onBack) {
        Text(stringResource(R.string.held_intro), style = MaterialTheme.typography.bodyLarge)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.precision_what_it_sees), Modifier.padding(bottom = 4.dp))
            Text(stringResource(R.string.held_sees), style = MaterialTheme.typography.bodyLarge)
        }

        if (!hasAccess) {
            Button(onClick = { context.startActivity(viewModel.accessSettingsIntent()) }) { Text(stringResource(R.string.held_allow_access)) }
            Hint(stringResource(R.string.held_restricted))
            TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.precision_app_info)) }
            return@EditorScaffold
        }

        SwitchRow(
            title = stringResource(R.string.held_switch),
            detail = stringResource(R.string.held_always_through),
            checked = settings.on,
            onCheckedChange = viewModel::setOn,
        )
        // Access is on but Android hasn't connected the listener (seen after updates).
        if (settings.on && !connected) Hint(stringResource(R.string.held_not_connected))

        if (settings.on) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(R.string.held_allowed))
                val allowed = choosable.filter { it.component.packageName in settings.allowed }
                if (allowed.isEmpty()) Hint(stringResource(R.string.held_allowed_none))
                allowed.forEach { app -> Text(app.label, style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = { pickingApps = true }) { Text(stringResource(R.string.schedules_choose_apps)) }
            }
            SwitchRow(
                title = stringResource(R.string.held_count_switch),
                detail = stringResource(R.string.held_count_switch_detail),
                checked = settings.countShown,
                onCheckedChange = viewModel::setCountShown,
            )
        }

        TextButton(onClick = onOpenHeld) {
            Text(pluralStringResource(R.plurals.held_open, held.size, held.size))
        }
    }

    if (pickingApps) {
        AppPickerSheet(
            apps = choosable,
            usage = emptyMap(),
            selected = settings.allowed,
            onChange = viewModel::setAllowed,
            onDismiss = { pickingApps = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
