package app.still.launcher.ui.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.components.SlideToConfirm
import kotlinx.coroutines.delay

/** How long the friction waits before the slide appears. */
private const val WAIT_SECONDS = 10

/**
 * Friction before skipping or deleting an item that keeps apps locked, since that opens them
 * without doing it: says what would open, waits a few seconds, then asks for a slide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BypassConfirmSheet(
    title: String,
    body: String,
    slideText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var remaining by rememberSaveable { mutableIntStateOf(WAIT_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (remaining > 0) {
                Text(stringResource(R.string.bypass_wait, remaining), style = MaterialTheme.typography.titleMedium)
            } else {
                SlideToConfirm(text = slideText, accessibilityLabel = slideText, onConfirm = onConfirm)
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/**
 * Confirms deleting a task or habit: a plain dialog, or [BypassConfirmSheet] when the item keeps
 * the apps in [locks] closed.
 */
@Composable
fun DeleteItemConfirm(name: String, detail: String, locks: List<String>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (locks.isEmpty()) {
        DeleteConfirmDialog(name = name, detail = detail, onDismiss = onDismiss, onConfirm = onConfirm)
    } else {
        BypassConfirmSheet(
            title = stringResource(R.string.item_delete_title, name),
            body = stringResource(R.string.bypass_delete_body, locks.joinToString(", ")),
            slideText = stringResource(R.string.bypass_slide_delete),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/** "Skip today" for an item that keeps the apps in [locks] closed. */
@Composable
fun SkipItemConfirm(name: String, locks: List<String>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BypassConfirmSheet(
        title = stringResource(R.string.bypass_skip_title, name),
        body = stringResource(R.string.bypass_skip_body, locks.joinToString(", ")),
        slideText = stringResource(R.string.bypass_slide_skip),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
