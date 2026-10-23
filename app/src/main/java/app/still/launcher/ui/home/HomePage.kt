package app.still.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.launcher.R
import app.still.launcher.productivity.Reminder
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.AppLabel
import app.still.launcher.ui.components.rememberCurrentMinute
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomePage(
    favorites: List<LauncherApp>,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    onClockClick: () -> Unit,
    dueTasks: Int,
    overdueTasks: Int,
    /** Apps task gates keep closed right now, and how many items they wait for. */
    lockedApps: Int,
    itemsToGo: Int,
    onDueTasksClick: () -> Unit,
    reminders: List<Reminder>,
    today: LocalDate,
    onReminderDone: (Reminder) -> Unit,
    onReminderDismiss: (Reminder) -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    onSwipeUp: () -> Unit,
    /** Double-tapping empty space; null when the gesture is turned off. */
    onDoubleTap: (() -> Unit)?,
    /** Precision mode was on and Android turned it off. */
    precisionLost: Boolean,
    onTurnOnPrecision: () -> Unit,
    onDismissPrecisionLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .swipeUp(onSwipeUp)
            .doubleTap(onDoubleTap)
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        if (!isDefaultLauncher) {
            HomeBanner(stringResource(R.string.home_not_default_title)) {
                TextButton(onClick = onSetDefaultLauncher) { Text(stringResource(R.string.home_not_default_action)) }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (precisionLost) {
            HomeBanner(stringResource(R.string.precision_lost_title)) {
                TextButton(onClick = onDismissPrecisionLost) { Text(stringResource(R.string.precision_lost_dismiss)) }
                TextButton(onClick = onTurnOnPrecision) { Text(stringResource(R.string.precision_lost_turn_on)) }
            }
            Spacer(Modifier.height(24.dp))
        }
        Clock(onClick = onClockClick)
        if (dueTasks > 0) DueTasks(dueTasks, overdueTasks, onDueTasksClick)
        if (lockedApps > 0) LockedApps(lockedApps, itemsToGo, onDueTasksClick)
        if (reminders.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                reminders.take(MAX_REMINDER_CARDS).forEach { reminder ->
                    key(reminder.key) {
                        ReminderCard(
                            reminder = reminder,
                            today = today,
                            onDone = { onReminderDone(reminder) },
                            onDismiss = { onReminderDismiss(reminder) },
                            onOpen = onDueTasksClick,
                        )
                    }
                }
                val hidden = reminders.size - MAX_REMINDER_CARDS
                if (hidden > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.home_more_reminders, hidden, hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onDueTasksClick),
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // A plain column, not a scrolling list, so a swipe that starts on a favorite still
                // reaches the swipe-up gesture. Home is for a handful of apps anyway.
                Column {
                    favorites.forEach { app ->
                        key(app.key) {
                            AppLabel(
                                app = app,
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calls [onSwipeUp] after an upward drag of at least [SWIPE_UP_DISTANCE]. Taps and sideways
 * swipes (to the other pages) pass through untouched.
 */
@Composable
private fun Modifier.swipeUp(onSwipeUp: () -> Unit): Modifier {
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val distance = with(LocalDensity.current) { SWIPE_UP_DISTANCE.toPx() }
    return pointerInput(Unit) {
        var dragged = 0f
        detectVerticalDragGestures(
            onDragStart = { dragged = 0f },
            onVerticalDrag = { change, amount ->
                dragged += amount
                change.consume()
            },
            onDragEnd = { if (dragged <= -distance) currentOnSwipeUp() },
        )
    }
}

private val SWIPE_UP_DISTANCE = 72.dp

/**
 * Calls [onDoubleTap] for a double-tap on empty space. Apps, the clock and cards handle their
 * own taps, so a double-tap on them doesn't count. Does nothing when [onDoubleTap] is null.
 */
@Composable
private fun Modifier.doubleTap(onDoubleTap: (() -> Unit)?): Modifier {
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    if (onDoubleTap == null) return this
    return pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { currentOnDoubleTap?.invoke() })
    }
}

@Composable
private fun Clock(onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val now by rememberCurrentMinute()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(timePattern, locale)),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern(datePattern, locale)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** More reminders than this are summarised as "+N more" so favorites keep their space. */
private const val MAX_REMINDER_CARDS = 3

/** "3 tasks due · 1 overdue" under the date; tapping it opens the Today page. Hidden when nothing is due. */
@Composable
private fun DueTasks(dueTasks: Int, overdueTasks: Int, onClick: () -> Unit) {
    val text = buildList {
        add(pluralStringResource(R.plurals.home_tasks_due, dueTasks, dueTasks))
        if (overdueTasks > 0) add(pluralStringResource(R.plurals.home_tasks_overdue, overdueTasks, overdueTasks))
    }.joinToString(" · ")
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/** "3 apps locked · 2 to go" under the due count; tapping it opens the Today page. */
@Composable
private fun LockedApps(lockedApps: Int, itemsToGo: Int, onClick: () -> Unit) {
    val text = listOf(
        pluralStringResource(R.plurals.home_apps_locked, lockedApps, lockedApps),
        pluralStringResource(R.plurals.home_items_to_go, itemsToGo, itemsToGo),
    ).joinToString(" · ")
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/** Something on home that needs fixing, with buttons to fix or dismiss it. */
@Composable
private fun HomeBanner(title: String, actions: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.align(Alignment.End), content = actions)
        }
    }
}
