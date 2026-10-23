package app.still.launcher.ui.blocking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.formatDuration
import java.time.Duration

/**
 * Pick apps to block, the ones you spent most time in last week first. Ticks apply straight
 * away; close the sheet when done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<LauncherApp>,
    usage: Map<String, Duration>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sorted = remember(apps, usage) {
        apps.sortedWith(
            compareByDescending<LauncherApp> { usage[it.component.packageName] ?: Duration.ZERO }.thenBy { it.label.lowercase() },
        )
    }
    val shown = sorted.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.schedules_choose_apps), style = MaterialTheme.typography.headlineSmall)
            if (usage.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.schedules_sorted_by_use),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            LazyColumn {
                items(shown, key = { it.component.packageName }) { app ->
                    val packageName = app.component.packageName
                    val isChosen = packageName in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChange(if (isChosen) selected - packageName else selected + packageName) },
                    ) {
                        Checkbox(checked = isChosen, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        usage[packageName]?.takeIf { !it.isZero }?.let { time ->
                            Text(
                                text = formatDuration(time),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
