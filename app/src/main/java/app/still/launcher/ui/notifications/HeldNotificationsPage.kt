package app.still.launcher.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.notifications.HeldNotification
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatDateTime
import app.still.launcher.ui.editor.EditorScaffold

/**
 * The notifications the filter held back, newest app first. Tapping one opens its app the way
 * STILL opens any app, so blocks, gates and launch delays still apply.
 */
@Composable
fun HeldNotificationsPage(
    apps: List<LauncherApp>,
    onOpenApp: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    viewModel: NotificationFilterViewModel = viewModel(factory = NotificationFilterViewModel.Factory),
) {
    val held by viewModel.held.collectAsStateWithLifecycle()
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    // An app can be in both profiles; name and open the personal one, which comes last here so it wins.
    val appsByPackage = apps.sortedByDescending { it.isWorkProfile }.associateBy { it.component.packageName }

    EditorScaffold(title = stringResource(R.string.held_title), onDone = onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.insights_settings)) }
            if (held.isNotEmpty()) TextButton(onClick = { confirmClearAll = true }) { Text(stringResource(R.string.held_clear_all)) }
        }
        if (held.isEmpty()) {
            Text(
                text = stringResource(R.string.held_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        held.groupBy { it.packageName }.forEach { (packageName, items) ->
            val app = appsByPackage[packageName]
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel(app?.label ?: packageName, Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clear(packageName) }) { Text(stringResource(R.string.held_clear)) }
                }
                items.forEach { item -> HeldRow(item, onClick = { app?.let(onOpenApp) }) }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.held_clear_all_title)) },
            text = { Text(stringResource(R.string.held_clear_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClearAll = false
                }) { Text(stringResource(R.string.held_clear_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun HeldRow(item: HeldNotification, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (item.title.isNotBlank()) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.text.isNotBlank()) {
            Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = formatDateTime(item.postedAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
