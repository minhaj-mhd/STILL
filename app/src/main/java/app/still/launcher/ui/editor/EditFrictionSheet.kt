package app.still.launcher.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.productivity.BypassConfirmSheet

/**
 * The wait and slide before an edit that would open the apps in [opens] (packages) without
 * doing [itemName]. [onConfirm] makes the change; [onDismiss] keeps the item as it was.
 */
@Composable
fun EditFrictionSheet(
    itemName: String,
    opens: Set<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory),
) {
    val apps = launcherViewModel.uiState.collectAsStateWithLifecycle().value.apps
    // Gates cover a package in every profile; name it after the personal app.
    val labels = opens.map { packageName ->
        apps.firstOrNull { it.component.packageName == packageName && !it.isWorkProfile }?.label ?: packageName
    }.sorted()
    BypassConfirmSheet(
        title = stringResource(R.string.bypass_edit_title, itemName),
        body = stringResource(R.string.bypass_edit_body, labels.joinToString(", ")),
        slideText = stringResource(R.string.bypass_slide_edit),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
