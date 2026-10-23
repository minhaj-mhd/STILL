package app.still.launcher.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.AppLabel
import java.text.Normalizer

@Composable
fun AppsPage(
    apps: List<LauncherApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    isCurrentPage: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showHidden by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            focusManager.clearFocus()
            keyboard?.hide()
            showHidden = false
        }
    }

    val visibleApps = remember(apps) { apps.filterNot { it.isHidden } }
    val hiddenApps = remember(apps) { apps.filter { it.isHidden } }
    val results = remember(visibleApps, query) { search(visibleApps, query) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Go),
            // Enter opens the best match, so typing a few letters is enough to launch an app.
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.onBackground,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(results, key = { it.key }) { app ->
                AppLabel(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
            }

            if (query.isNotBlank() && results.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        text = stringResource(R.string.apps_no_results, query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            // Hidden apps stay reachable, one deliberate tap away, and are never in search results.
            if (query.isBlank() && hiddenApps.isNotEmpty()) {
                item(key = "hidden-toggle") {
                    Text(
                        text = stringResource(R.string.apps_hidden_section, hiddenApps.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHidden = !showHidden }
                            .padding(top = 32.dp, bottom = 12.dp),
                    )
                }
                if (showHidden) {
                    items(hiddenApps, key = { "hidden:${it.key}" }) { app ->
                        AppLabel(
                            app = app,
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongPress(app) },
                            dimmed = true,
                        )
                    }
                }
            }
        }
    }
}

/** Prefix matches first, then matches at the start of a word, then anywhere; ignores case and accents. */
private fun search(apps: List<LauncherApp>, query: String): List<LauncherApp> {
    val needle = query.normalizedForSearch()
    if (needle.isEmpty()) return apps
    return apps
        .mapNotNull { app ->
            val label = app.label.normalizedForSearch()
            val rank = when {
                label.startsWith(needle) -> 0
                label.split(' ', '-', '_', '.').any { it.startsWith(needle) } -> 1
                needle in label -> 2
                else -> return@mapNotNull null
            }
            rank to app
        }
        .sortedBy { it.first }
        .map { it.second }
}

private val combiningMarks = Regex("\\p{Mn}+")

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")
