package app.still.launcher.ui.read

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.ReaderActivity
import app.still.launcher.data.ReadRepository
import app.still.launcher.data.read.BookStatus
import app.still.launcher.read.READ_GOAL_CHOICES
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.productivity.ActionRow
import app.still.launcher.ui.productivity.ActionsSheet
import app.still.launcher.ui.productivity.SectionTitle
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration
import kotlin.math.roundToInt

/** The file types the picker offers. */
private val BOOK_TYPES = arrayOf("application/pdf", "application/epub+zip")

/**
 * The Read tab: today's reading against the goal, then the books being read, those waiting, and
 * those finished. Books are added from the file picker or the share menu.
 */
@Composable
fun ReadPage(
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReadViewModel = viewModel(factory = ReadViewModel.Factory),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val library by viewModel.library.collectAsStateWithLifecycle()
    var menuBook by rememberSaveable { mutableStateOf<Long?>(null) }
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var finishedOpen by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(viewModel) {
        viewModel.imported.collect { result ->
            val message = result.fold(
                onSuccess = { resources.getString(R.string.read_added, it.title) },
                onFailure = {
                    val error = (it as? ReadRepository.ImportFailure)?.error
                    resources.getString(if (error == ReadRepository.ImportError.NOT_A_BOOK) R.string.read_not_a_book else R.string.read_unreadable)
                },
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 32.dp, top = 48.dp, end = 32.dp, bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column {
                    tabs()
                    library?.let { GoalLine(it) { goalOpen = true } }
                }
            }
            val current = library ?: return@LazyColumn
            if (current.isEmpty) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.read_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            if (current.reading.isNotEmpty()) {
                item(key = "reading-title") { SectionTitle(R.string.read_reading) }
                items(current.reading, key = { "book:${it.book.id}" }) { entry ->
                    BookRow(entry, onOpen = { context.startActivity(ReaderActivity.intent(context, entry.book.id)) }, onLongClick = { menuBook = entry.book.id })
                }
            }
            if (current.toRead.isNotEmpty()) {
                item(key = "to-read-title") { SectionTitle(R.string.read_to_read) }
                items(current.toRead, key = { "book:${it.book.id}" }) { entry ->
                    BookRow(entry, onOpen = { context.startActivity(ReaderActivity.intent(context, entry.book.id)) }, onLongClick = { menuBook = entry.book.id })
                }
            }
            if (current.finished.isNotEmpty()) {
                item(key = "finished-title") { SectionTitle(R.string.read_finished) }
                if (finishedOpen) {
                    items(current.finished, key = { "book:${it.book.id}" }) { entry ->
                        BookRow(entry, onOpen = { context.startActivity(ReaderActivity.intent(context, entry.book.id)) }, onLongClick = { menuBook = entry.book.id })
                    }
                } else {
                    item(key = "finished-count") {
                        Text(
                            text = pluralStringResource(R.plurals.read_finished_count, current.finished.size, current.finished.size) + "  ›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { finishedOpen = true }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable { picker.launch(BOOK_TYPES) }
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(stringResource(R.string.read_add), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.read_share_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val entry = library?.let { all -> (all.reading + all.toRead + all.finished).firstOrNull { it.book.id == menuBook } }
    if (entry != null) {
        BookActionsSheet(
            entry = entry,
            onDismiss = { menuBook = null },
            onSetStatus = { status ->
                viewModel.setStatus(entry.book, status)
                menuBook = null
            },
            onRemove = {
                viewModel.remove(entry.book)
                menuBook = null
            },
        )
    }
    if (goalOpen) {
        library?.let { GoalSheet(it, viewModel, onDismiss = { goalOpen = false }) }
    }
}

/** "Read today 18 of 20 min", then the streak and the habit it fills, or a way to set a goal. */
@Composable
private fun GoalLine(library: Library, onClick: () -> Unit) {
    val minutes = Duration.ofMillis(library.todayMillis).toMinutes().toInt()
    val details = buildList {
        if (library.streak > 0) add(pluralStringResource(R.plurals.read_streak, library.streak, library.streak))
        library.habit?.let { add(stringResource(R.string.read_counts_for, it.title)) }
        if (library.goalMinutes == 0) add(stringResource(R.string.read_set_goal) + "  ›")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 16.dp, bottom = 4.dp),
    ) {
        Row {
            Text(stringResource(R.string.read_today) + "  ", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (library.goalMinutes > 0) stringResource(R.string.read_today_goal, minutes, library.goalMinutes)
                else stringResource(R.string.read_today_minutes, minutes),
                style = MaterialTheme.typography.titleMedium,
                color = if (library.goalMinutes > 0) stillPalette.accent else MaterialTheme.colorScheme.onBackground,
            )
        }
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(entry: LibraryBook, onOpen: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val book = entry.book
    val started = book.status != BookStatus.TO_READ
    val details = listOfNotNull(
        book.author.takeIf { !started },
        book.positionLabel.takeIf { started && !it.isNullOrBlank() },
        entry.readMillis.takeIf { it >= MINUTE_MILLIS }?.let { stringResource(R.string.read_time_read, formatDuration(Duration.ofMillis(it))) },
        book.format.name,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (started) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (book.status == BookStatus.READING) {
                Text(
                    text = stringResource(R.string.read_percent, (book.progress * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (book.status == BookStatus.READING) ProgressTrack(book.progress.toFloat())
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressTrack(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(stillPalette.faint, RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                .height(3.dp)
                .background(stillPalette.accent, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun BookActionsSheet(entry: LibraryBook, onDismiss: () -> Unit, onSetStatus: (BookStatus) -> Unit, onRemove: () -> Unit) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val book = entry.book
    ActionsSheet(title = book.title, onDismiss = onDismiss) {
        if (book.status != BookStatus.FINISHED) ActionRow(stringResource(R.string.read_mark_finished)) { onSetStatus(BookStatus.FINISHED) }
        if (book.status == BookStatus.FINISHED && book.position != null) ActionRow(stringResource(R.string.read_move_reading)) { onSetStatus(BookStatus.READING) }
        if (book.status != BookStatus.TO_READ) ActionRow(stringResource(R.string.read_move_to_read)) { onSetStatus(BookStatus.TO_READ) }
        ActionRow(stringResource(R.string.read_remove)) { confirmRemove = true }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.read_remove_title, book.title)) },
            text = { Text(stringResource(R.string.read_remove_detail)) },
            confirmButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.read_remove_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The daily goal, and the duration habit reading fills. */
@Composable
private fun GoalSheet(library: Library, viewModel: ReadViewModel, onDismiss: () -> Unit) {
    ActionsSheet(title = stringResource(R.string.read_goal_title), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            FieldLabel(stringResource(R.string.read_goal_daily))
            ChipRow {
                Chip(stringResource(R.string.read_goal_off), library.goalMinutes == 0) { viewModel.setGoal(0) }
                READ_GOAL_CHOICES.forEach { minutes ->
                    Chip(stringResource(R.string.read_today_minutes, minutes), library.goalMinutes == minutes) { viewModel.setGoal(minutes) }
                }
            }
            FieldLabel(stringResource(R.string.read_goal_habit), Modifier.padding(top = 8.dp))
            ChipRow {
                Chip(stringResource(R.string.read_goal_habit_none), library.habit == null) { viewModel.setHabit(null) }
                library.durationHabits.forEach { habit ->
                    Chip(habit.title, library.habit?.id == habit.id) { viewModel.setHabit(habit) }
                }
            }
            Text(
                text = stringResource(if (library.durationHabits.isEmpty()) R.string.read_goal_no_habits else R.string.read_goal_habit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

private const val MINUTE_MILLIS = 60_000L
