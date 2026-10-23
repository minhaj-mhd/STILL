package app.still.launcher.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.HomeFolder
import app.still.launcher.ui.LauncherApp

/** "Move to folder": the folders on home, the app's own marked, and a way to make a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    app: LauncherApp,
    folders: List<HomeFolder>,
    onMove: (folderId: String) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Text(stringResource(R.string.folder_move_title, app.label), style = MaterialTheme.typography.headlineSmall)
            folders.forEach { folder ->
                val isCurrent = folder.id == app.folderId
                SheetRow(
                    text = if (isCurrent) stringResource(R.string.folder_current, folder.name) else folder.name,
                    dimmed = isCurrent,
                ) { if (!isCurrent) onMove(folder.id) }
            }
            SheetRow(stringResource(R.string.folder_new)) { onNewFolder() }
        }
    }
}

/** A folder's long-press menu on home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionsSheet(folder: HomeFolder, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Text(folder.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.folder_add_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SheetRow(stringResource(R.string.folder_rename), onClick = onRename)
            SheetRow(stringResource(R.string.folder_delete)) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.folder_delete_title, folder.name)) },
            text = { Text(stringResource(R.string.folder_delete_body)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.item_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Names a new folder, or renames one. */
@Composable
fun FolderNameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val name = text.text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) R.string.folder_new else R.string.folder_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotEmpty()) { Text(stringResource(R.string.rename_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SheetRow(text: String, dimmed: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
