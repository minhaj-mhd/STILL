package app.still.launcher.ui.read.daily

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.launcher.R
import app.still.launcher.ReaderActivity
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.BookStatus
import app.still.launcher.data.read.ReadSeries
import app.still.launcher.drive.DriveFolder
import app.still.launcher.read.WeekDay
import app.still.launcher.read.keepFrom
import app.still.launcher.read.weekOf
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.formatDateTime
import app.still.launcher.ui.components.formatMonth
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.components.formatWeekdayShortDay
import app.still.launcher.ui.read.DoneMark
import app.still.launcher.ui.read.SeriesLabel
import app.still.launcher.ui.read.arrivalDate
import app.still.launcher.ui.read.message
import app.still.launcher.ui.theme.stillPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToInt

private const val HOME = "home"
private const val ADD = "add"
private const val SERIES = "series:"

/**
 * Daily reads, outside the launcher: connecting Google Drive, the series, each series' reads, and
 * adding a chat's folder. [startSeries] opens straight on that series.
 */
@Composable
fun DailyReadsScreens(viewModel: DailyReadsViewModel, startSeries: Long?, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var stack by rememberSaveable { mutableStateOf(listOf(startSeries?.let { "$SERIES$it" } ?: HOME)) }
    val route = stack.last()
    fun push(next: String) {
        stack = stack + next
    }
    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1) else onClose()
    }
    BackHandler(enabled = stack.size > 1) { back() }

    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Google's screen sends its errors back too, not only its answers.
        if (result.resultCode == Activity.RESULT_OK || result.data != null) viewModel.onSignIn(result.data) else viewModel.onSignInCancelled()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DailyEvent.SignIn -> signIn.launch(IntentSenderRequest.Builder(event.intent).build())
                is DailyEvent.Open -> context.startActivity(ReaderActivity.intent(context, event.bookId))
                is DailyEvent.Problem -> Toast.makeText(context, resources.getString(event.problem.message()), Toast.LENGTH_LONG).show()
                is DailyEvent.Added -> stack = stack.dropLast(1) + "$SERIES${event.seriesId}"
            }
        }
    }

    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val current = overview ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            route == ADD -> AddFolderScreen(viewModel, current, busy, onBack = ::back)
            route.startsWith(SERIES) -> {
                val id = route.removePrefix(SERIES).toLongOrNull()
                val series = current.series.firstOrNull { it.series.id == id }?.series
                if (series == null) {
                    LaunchedEffect(id) { back() }
                } else {
                    SeriesScreen(viewModel, series, current.keepDays, onBack = ::back)
                }
            }
            else -> HomeScreen(
                viewModel = viewModel,
                overview = current,
                busy = busy,
                onSeries = { push("$SERIES$it") },
                onAdd = {
                    viewModel.loadFolders()
                    push(ADD)
                },
                onBack = ::back,
            )
        }
    }
}

@Composable
private fun Page(onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 48.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        item(key = "back") {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        content()
    }
}

@Composable
private fun PageTitle(text: String, serif: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.let { if (serif) it.copy(fontFamily = FontFamily.Serif) else it },
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun Section(text: String, trailing: String? = null) {
    Row(Modifier.padding(top = 28.dp, bottom = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Action(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = stillPalette.accent,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** A wide accent button for the one thing to do on a page. */
@Composable
private fun MainButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .background(stillPalette.accent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun HomeScreen(
    viewModel: DailyReadsViewModel,
    overview: DailyOverview,
    busy: Busy?,
    onSeries: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    Page(onBack) {
        item(key = "title") { PageTitle(stringResource(R.string.daily_reads)) }
        if (!overview.connected) {
            item(key = "intro") {
                Column {
                    Text(
                        text = stringResource(R.string.daily_intro),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    MainButton(stringResource(if (busy == Busy.CONNECTING) R.string.daily_connecting else R.string.daily_connect_drive), viewModel::connect)
                    Muted(stringResource(R.string.daily_connect_detail), Modifier.padding(top = 10.dp))
                }
            }
        } else {
            item(key = "account") {
                Column {
                    Muted(stringResource(R.string.daily_connected))
                    overview.account?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (overview.needsYou) {
                item(key = "needs-you") {
                    Column(
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .border(0.5.dp, stillPalette.accent, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                    ) {
                        Text(stringResource(R.string.daily_needs_you_title), style = MaterialTheme.typography.titleMedium)
                        Muted(stringResource(R.string.daily_needs_you_detail))
                        Action(stringResource(if (busy == Busy.CONNECTING) R.string.daily_connecting else R.string.daily_reconnect) + "  ›", onClick = viewModel::connect)
                    }
                }
            }
        }
        if (overview.connected || overview.series.isNotEmpty()) {
            item(key = "series-title") { Section(stringResource(R.string.daily_your_series)) }
            if (overview.series.isEmpty()) item(key = "no-series") { Muted(stringResource(R.string.daily_no_series)) }
            items(overview.series, key = { "series:${it.series.id}" }) { row -> SeriesListRow(row) { onSeries(row.series.id) } }
            if (overview.connected) item(key = "add") { Action(stringResource(R.string.daily_add_folder), onClick = onAdd) }
        }
        if (overview.connected) {
            item(key = "phone") {
                Column {
                    Section(stringResource(R.string.daily_on_phone))
                    Text(stringResource(R.string.daily_keep), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    ChipRow {
                        LauncherSettings.KEEP_DAYS_CHOICES.forEach { days ->
                            Chip(pluralStringResource(R.plurals.daily_keep_days, days, days), overview.keepDays == days) { viewModel.setKeepDays(days) }
                        }
                    }
                }
            }
            item(key = "fetching") {
                Column {
                    Section(stringResource(R.string.daily_fetching))
                    Muted(stringResource(R.string.daily_fetching_detail))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Muted(lastCheckText(overview), Modifier.weight(1f))
                        Action(stringResource(if (busy == Busy.CHECKING) R.string.daily_checking else R.string.daily_check_now), onClick = viewModel::checkNow)
                    }
                }
            }
            item(key = "disconnect") {
                Text(
                    text = stringResource(R.string.daily_disconnect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .clickable { confirmDisconnect = true }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.daily_disconnect_title)) },
            text = { Text(stringResource(R.string.daily_disconnect_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) { Text(stringResource(R.string.daily_disconnect_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun lastCheckText(overview: DailyOverview): String {
    if (overview.checkedAtMillis <= 0L) return stringResource(R.string.daily_never_checked)
    val checked = Instant.ofEpochMilli(overview.checkedAtMillis).atZone(ZoneId.systemDefault())
    val time = if (checked.toLocalDate() == LocalDate.now()) formatTime(checked.toLocalTime()) else formatDateTime(checked.toInstant())
    return stringResource(R.string.daily_last_check, time, pluralStringResource(R.plurals.daily_new_count, overview.newAtCheck, overview.newAtCheck))
}

@Composable
private fun SeriesListRow(row: SeriesRow, onClick: () -> Unit) {
    val series = row.series
    val count = pluralStringResource(R.plurals.daily_reads_count, row.count, row.count)
    val detail = series.landsAt()?.let { stringResource(R.string.daily_series_detail, series.folderName, formatTime(it), count) }
        ?: stringResource(R.string.daily_series_detail_untimed, series.folderName, count)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(series.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
            Muted(detail)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = stillPalette.accent)
    }
}

private fun ReadSeries.landsAt(): LocalTime? = landsAtMinute?.let { LocalTime.of(it / 60, it % 60) }

@Composable
private fun SeriesScreen(viewModel: DailyReadsViewModel, series: ReadSeries, keepDays: Int, onBack: () -> Unit) {
    val reads by remember(series.id) { viewModel.reads(series.id) }.collectAsStateWithLifecycle(emptyList())
    val times by viewModel.readTimes.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    var shownMonths by rememberSaveable(series.id) { mutableIntStateOf(1) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }

    val today = LocalDate.now()
    val from = keepFrom(today, keepDays)
    val (recent, earlier) = reads.partition { (it.arrivedAt?.arrivalDate() ?: today) >= from }
    val months = earlier.groupBy { it.arrivedAt?.arrivalDate()?.withDayOfMonth(1) }.toList()

    Page(onBack) {
        item(key = "head") {
            Column {
                SeriesLabel(stringResource(R.string.daily_series_from))
                PageTitle(series.name, serif = true)
                Muted(
                    series.landsAt()?.let { stringResource(R.string.daily_series_where, formatTime(it), series.folderName) }
                        ?: stringResource(R.string.daily_series_where_untimed, series.folderName),
                )
                WeekStrip(reads, today, Modifier.padding(top = 16.dp))
            }
        }
        if (reads.isEmpty()) {
            item(key = "empty") { Muted(stringResource(R.string.daily_no_reads), Modifier.padding(top = 24.dp)) }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent-title") { Section(pluralStringResource(R.plurals.daily_on_phone_days, keepDays, keepDays), stringResource(R.string.daily_ready_offline)) }
            items(recent, key = { "read:${it.id}" }) { read ->
                ReadRow(read, times[read.id] ?: 0L, read.id in downloading, today) { viewModel.open(read) }
            }
        }
        if (earlier.isNotEmpty()) {
            item(key = "earlier-title") {
                Section(stringResource(R.string.daily_earlier), pluralStringResource(R.plurals.daily_reads_count, earlier.size, earlier.size))
            }
            months.take(shownMonths).forEach { (month, monthReads) ->
                if (month != null && shownMonths > 1) item(key = "month:$month") { Muted(formatMonth(month), Modifier.padding(top = 12.dp)) }
                items(monthReads, key = { "read:${it.id}" }) { read ->
                    ReadRow(read, times[read.id] ?: 0L, read.id in downloading, today) { viewModel.open(read) }
                }
            }
            val next = months.getOrNull(shownMonths)?.first
            if (next != null) {
                item(key = "more") { Action(stringResource(R.string.daily_show_month, formatMonth(next)) + "  ›") { shownMonths++ } }
            }
        }
        item(key = "manage") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 28.dp)) {
                Muted(stringResource(R.string.daily_rename), Modifier.clickable { renaming = true }.padding(vertical = 8.dp))
                Muted(stringResource(R.string.daily_remove_series), Modifier.clickable { removing = true }.padding(vertical = 8.dp))
            }
        }
    }

    if (renaming) {
        var name by rememberSaveable { mutableStateOf(series.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.daily_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.daily_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(series, name)
                    renaming = false
                }) { Text(stringResource(R.string.daily_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.daily_remove_title, series.name)) },
            text = { Text(stringResource(R.string.daily_remove_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = false
                    viewModel.remove(series)
                    onBack()
                }) { Text(stringResource(R.string.daily_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The last seven days: read, came but not finished, still to come today, or nothing. */
@Composable
private fun WeekStrip(reads: List<Book>, today: LocalDate, modifier: Modifier = Modifier) {
    val byDate = reads.filter { it.arrivedAt != null }.groupBy({ it.arrivedAt!!.arrivalDate() }, { it.status == BookStatus.FINISHED })
    val locale = LocalLocale.current.platformLocale
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        weekOf(byDate, today).forEach { (date, day) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val shape = RoundedCornerShape(3.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            when (day) {
                                WeekDay.READ -> stillPalette.accent
                                WeekDay.NONE -> stillPalette.faint
                                else -> Color.Transparent
                            },
                            shape,
                        )
                        .border(
                            width = if (day == WeekDay.WAITING || day == WeekDay.TODAY) 1.dp else 0.dp,
                            color = if (day == WeekDay.WAITING) stillPalette.accent else if (day == WeekDay.TODAY) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                            shape = shape,
                        ),
                )
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == today) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadRow(read: Book, readMillis: Long, getting: Boolean, today: LocalDate, onClick: () -> Unit) {
    val date = read.arrivedAt?.arrivalDate()
    val dateText = when {
        date == null -> null
        date == today -> stringResource(R.string.daily_today)
        date > today.minusDays(7) -> formatWeekdayShortDay(date)
        else -> formatShortDate(date)
    }
    val minutes = read.minutes
    val state = when {
        read.status == BookStatus.FINISHED -> (minutes ?: (readMillis / MINUTE_MILLIS).toInt().takeIf { it > 0 })?.let { stringResource(R.string.daily_minutes, it) }
        read.status == BookStatus.READING && minutes != null -> stringResource(R.string.daily_progress_of, (minutes * read.progress).roundToInt(), minutes)
        read.status == BookStatus.READING -> stringResource(R.string.read_percent, (read.progress * 100).roundToInt())
        date != null && date < today -> stringResource(R.string.daily_not_opened)
        minutes != null -> stringResource(R.string.daily_minutes, minutes)
        else -> null
    }
    val quiet = !read.onDevice && read.status == BookStatus.TO_READ
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = read.title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Muted(
                text = when {
                    getting -> listOfNotNull(dateText, stringResource(R.string.daily_getting)).joinToString(" · ")
                    else -> listOfNotNull(dateText, state).joinToString(" · ")
                },
            )
        }
        Box(Modifier.padding(start = 12.dp)) {
            when {
                read.status == BookStatus.FINISHED -> DoneMark(true)
                getting -> Unit
                !read.onDevice -> Text("↓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                read.status == BookStatus.TO_READ && date == today -> Text(stringResource(R.string.daily_new), style = MaterialTheme.typography.labelLarge, color = stillPalette.accent)
                else -> Text("›", style = MaterialTheme.typography.titleMedium, color = stillPalette.accent)
            }
        }
    }
}

@Composable
private fun AddFolderScreen(viewModel: DailyReadsViewModel, overview: DailyOverview, busy: Busy?, onBack: () -> Unit) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    val taken = overview.series.mapTo(HashSet()) { it.series.folderId }
    Page(onBack) {
        item(key = "title") {
            Column {
                PageTitle(stringResource(R.string.daily_add_title))
                Muted(stringResource(R.string.daily_add_detail), Modifier.padding(bottom = 12.dp))
            }
        }
        when (val list = folders) {
            null, FolderList.Loading -> item(key = "loading") { Muted(stringResource(R.string.daily_looking)) }
            is FolderList.Failed -> item(key = "failed") {
                Column {
                    Muted(stringResource(list.problem.message()))
                    Action(stringResource(R.string.daily_try_again), onClick = viewModel::loadFolders)
                }
            }
            is FolderList.Loaded -> {
                if (list.folders.isEmpty()) item(key = "none") { Muted(stringResource(R.string.daily_no_folders)) }
                items(list.folders, key = { "folder:${it.id}" }) { folder ->
                    FolderRow(folder, chosen?.takeIf { it.folder.id == folder.id }, taken = folder.id in taken) { viewModel.choose(folder) }
                }
            }
        }
        chosen?.let { choice ->
            item(key = "chosen:${choice.folder.id}") { ChosenSeries(viewModel, choice, busy) }
        }
        item(key = "prompt") { PromptForChat(chosen?.folder?.name) }
    }
}

@Composable
private fun FolderRow(folder: DriveFolder, chosen: ChosenFolder?, taken: Boolean, onClick: () -> Unit) {
    val selected = chosen != null
    val detail = when {
        taken -> stringResource(R.string.daily_already_series)
        chosen?.preview != null && chosen.preview.reads == 0 -> stringResource(R.string.daily_preview_empty)
        chosen?.preview != null -> stringResource(
            R.string.daily_preview,
            pluralStringResource(R.plurals.daily_reads_count, chosen.preview.reads, chosen.preview.reads),
            chosen.preview.latest?.let { formatDateTime(it) }.orEmpty(),
        )
        selected -> stringResource(R.string.daily_looking)
        else -> folder.modifiedAt?.let { stringResource(R.string.daily_folder_changed, formatShortDate(it.arrivalDate())) }.orEmpty()
    }
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .border(if (selected) 1.dp else 0.5.dp, if (selected) stillPalette.accent else stillPalette.faint, RoundedCornerShape(12.dp))
            .clickable(enabled = !taken, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (taken) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) Muted(detail)
    }
}

@Composable
private fun ChosenSeries(viewModel: DailyReadsViewModel, choice: ChosenFolder, busy: Busy?) {
    var name by rememberSaveable(choice.folder.id) { mutableStateOf(choice.folder.name) }
    val keepDays = viewModel.overview.collectAsStateWithLifecycle().value?.keepDays ?: LauncherSettings.DEFAULT_KEEP_DAYS
    Column {
        Section(stringResource(R.string.daily_series_section))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.daily_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        DetailRow(
            stringResource(R.string.daily_lands_around),
            choice.preview?.landsAtMinute?.let { formatTime(LocalTime.of(it / 60, it % 60)) } ?: stringResource(R.string.daily_lands_unknown),
        )
        DetailRow(stringResource(R.string.daily_get_now), pluralStringResource(R.plurals.daily_get_now_detail, keepDays, keepDays))
        MainButton(stringResource(if (busy == Busy.ADDING) R.string.daily_adding else R.string.daily_add_series)) {
            if (busy == null) viewModel.addSeries(choice.folder, name)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Muted(value)
    }
}

/** The line to add to a scheduled chat so it saves its reads where STILL looks. */
@Composable
private fun PromptForChat(folderName: String?) {
    val context = LocalContext.current
    val prompt = stringResource(R.string.daily_prompt, folderName ?: stringResource(R.string.daily_reads))
    val copied = stringResource(R.string.daily_copied)
    Column {
        Section(stringResource(R.string.daily_not_saving))
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, stillPalette.faint, RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
        Action(stringResource(R.string.daily_copy)) {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(prompt, prompt))
            // Android 13 and later say so themselves.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
}

private const val MINUTE_MILLIS = 60_000L
