package app.still.launcher.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.apps.listSections
import app.still.launcher.apps.normalizedForSearch
import app.still.launcher.apps.sectionLetter
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.AppLabel
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.theme.stillPalette
import kotlinx.coroutines.launch

/**
 * The right page: every app by name, with search at the top. A large letter beside the names
 * marks the section at the top of the list, and letters down the edge jump through it.
 * Recently installed apps come first. With [fadeWithUse], apps that have taken a lot of today
 * fade. While searching, the best match is picked out in the accent colour, and Enter opens it.
 */
@Composable
fun AppsPage(
    apps: List<LauncherApp>,
    recentlyInstalled: List<LauncherApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    isCurrentPage: Boolean,
    /** Put the cursor in search and bring up the keyboard: the page is open and the option is on. */
    openKeyboard: Boolean,
    fadeWithUse: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            focusManager.clearFocus()
            keyboard?.hide()
            // Back at the top next time, where the recently installed apps are.
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    // Scrolling the list by hand puts the keyboard away, so the list gets the room.
    val listDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listDragged) {
        if (listDragged) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // Hidden apps are neither listed nor searchable here; they live in Settings > Hidden apps.
    val visibleApps = remember(apps) { apps.filterNot { it.isHidden } }
    val searching = query.isNotBlank()
    val results = remember(visibleApps, query) { search(visibleApps, query) }
    val recent = if (searching) emptyList() else recentlyInstalled
    // Rows above the alphabetical list: "Recently installed", its apps and "All apps".
    val listStart = if (recent.isEmpty()) 0 else recent.size + 2
    val sections = remember(visibleApps) { listSections(visibleApps.map { it.label }) }
    val sectionStarts = remember(sections) { sections.mapTo(HashSet()) { it.index } }
    // The section at the top of the list; none above the alphabetical list or while searching.
    val topLetter by remember(results, listStart, searching) {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex - listStart
            if (searching || index < 0) null else results.getOrNull(index)?.let { sectionLetter(it.label) }
        }
    }

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
            keyboardActions = KeyboardActions(onGo = { if (searching) results.firstOrNull()?.let(onLaunch) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.onBackground,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(searchFocus)
                .onFocusChanged { searchFocused = it.isFocused },
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = NAMES_START, end = 32.dp, top = 8.dp, bottom = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (recent.isNotEmpty()) {
                    item(key = "recent-heading") { FieldLabel(stringResource(R.string.apps_recently_installed)) }
                    items(recent, key = { "recent:${it.key}" }) { app ->
                        AppLabel(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }, fadeWithUse = fadeWithUse)
                    }
                    item(key = "all-heading") {
                        FieldLabel(stringResource(R.string.apps_all), Modifier.padding(top = 24.dp))
                    }
                }

                itemsIndexed(results, key = { _, app -> app.key }) { index, app ->
                    AppLabel(
                        app = app,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        highlighted = searching && index == 0,
                        fadeWithUse = fadeWithUse,
                        // A little room before each new letter.
                        modifier = if (!searching && index > 0 && index in sectionStarts) Modifier.padding(top = SECTION_GAP) else Modifier,
                    )
                }

                if (searching && results.isEmpty()) {
                    item(key = "no-results") {
                        Text(
                            text = stringResource(R.string.apps_no_results, query.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }

            BigLetter(
                letter = topLetter,
                order = { letter -> sections.indexOfFirst { it.letter == letter } },
                modifier = Modifier.padding(start = BIG_LETTER_START, top = 4.dp),
            )

            // Only for the full list: search results are ranked, not alphabetical.
            if (!searching && !searchFocused && sections.size > 1) {
                FastScroller(
                    sections = sections,
                    onSection = { section -> scope.launch { listState.scrollToItem(listStart + section.index) } },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                )
            }
        }
    }
}

/**
 * The letter of the section at the top of the list, large and quiet beside the names. A new
 * letter slides in the way the list is moving.
 */
@Composable
private fun BigLetter(letter: String?, order: (String) -> Int, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = letter,
        transitionSpec = {
            val sign = if ((targetState?.let(order) ?: -1) >= (initialState?.let(order) ?: -1)) 1 else -1
            (slideInVertically { sign * it / 2 } + fadeIn()) togetherWith (slideOutVertically { -sign * it / 2 } + fadeOut())
        },
        label = "big letter",
        modifier = modifier,
    ) { shown ->
        Text(
            text = shown.orEmpty(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = stillPalette.accent.copy(alpha = BIG_LETTER_ALPHA),
        )
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

/** The big letter's column, then the names. */
private val BIG_LETTER_START = 24.dp
private val NAMES_START = 88.dp
private val SECTION_GAP = 14.dp
private const val BIG_LETTER_ALPHA = 0.55f
