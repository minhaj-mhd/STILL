package app.still.launcher.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.AppScreenTime
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.limit.appLimitSummary

/** Things you can do with an app; opened by long-pressing it on home or in the app list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: LauncherApp,
    screenTime: AppScreenTime?,
    hasUsageAccess: Boolean,
    onDismiss: () -> Unit,
    onAllowUsageAccess: () -> Unit,
    onBlock: () -> Unit,
    onLimits: () -> Unit,
    onSetLaunchDelay: (Boolean) -> Unit,
    onSetTimeReminder: (Boolean) -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    /** The folder the app is in, if any. */
    folderName: String?,
    onMoveToFolder: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onRename: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Text(text = app.label, style = MaterialTheme.typography.headlineSmall)
            when {
                screenTime != null -> Text(
                    text = stringResource(
                        R.string.screen_time_summary,
                        formatDuration(screenTime.today),
                        formatDuration(screenTime.lastSevenDays),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !hasUsageAccess -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.screen_time_needs_access),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAllowUsageAccess) { Text(stringResource(R.string.screen_time_allow)) }
                }
            }
            if (app.isGated) {
                val toGo = app.gates.flatMap { check -> check.tasks.map { it.task.id to false } + check.habits.map { it.habit.id to true } }.distinct().size
                Text(
                    text = pluralStringResource(R.plurals.app_locked_by, toGo, app.gates.joinToString(", ") { it.gate.name }, toGo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (app.canBlock && !app.isBlocked) {
                ActionRow(stringResource(R.string.action_block), onBlock)
            }
            if (app.canBlock) {
                val limit = app.appLimit
                ActionRow(if (limit != null) stringResource(R.string.action_limits_set, appLimitSummary(limit.limit)) else stringResource(R.string.action_limits), onLimits)
                if (app.hasLaunchDelay) ActionRow(stringResource(R.string.action_remove_launch_delay)) { onSetLaunchDelay(false) }
                else ActionRow(stringResource(R.string.action_add_launch_delay)) { onSetLaunchDelay(true) }
                if (app.hasTimeReminder) ActionRow(stringResource(R.string.action_remove_time_reminder)) { onSetTimeReminder(false) }
                else ActionRow(stringResource(R.string.action_add_time_reminder)) { onSetTimeReminder(true) }
            }
            if (!app.isHidden) {
                if (app.isFavorite) {
                    ActionRow(stringResource(R.string.action_remove_favorite)) { onSetFavorite(false) }
                } else {
                    ActionRow(stringResource(R.string.action_add_favorite)) { onSetFavorite(true) }
                }
            }
            ActionRow(stringResource(R.string.folder_move), onMoveToFolder)
            folderName?.let { ActionRow(stringResource(R.string.folder_remove_from, it), onRemoveFromFolder) }
            ActionRow(stringResource(R.string.action_rename), onRename)
            if (app.isHidden) {
                ActionRow(stringResource(R.string.action_unhide)) { onSetHidden(false) }
            } else {
                ActionRow(stringResource(R.string.action_hide)) { onSetHidden(true) }
            }
            ActionRow(stringResource(R.string.action_app_info), onAppInfo)
            if (app.canUninstall) {
                ActionRow(stringResource(R.string.action_uninstall), onUninstall)
            }
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
fun RenameAppDialog(
    app: LauncherApp,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember {
        mutableStateOf(TextFieldValue(app.label, selection = TextRange(0, app.label.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title, app.originalLabel)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (app.isRenamed) {
                    TextButton(onClick = { onSave(app.originalLabel) }) {
                        Text(stringResource(R.string.rename_restore))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.text) }) { Text(stringResource(R.string.rename_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rename_cancel)) }
        },
    )
}
