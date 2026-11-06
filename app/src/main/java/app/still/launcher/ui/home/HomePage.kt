package app.still.launcher.ui.home

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.launcher.R
import app.still.launcher.focus.FocusSession
import app.still.launcher.focusmode.holdMillisFor
import app.still.launcher.limit.LimitState
import app.still.launcher.productivity.ItemProgress
import app.still.launcher.productivity.Reminder
import app.still.launcher.data.AppDisplay
import app.still.launcher.data.FolderOpening
import app.still.launcher.data.FolderStyle
import app.still.launcher.ui.HomeFolder
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.AppIcon
import app.still.launcher.ui.components.AppItem
import app.still.launcher.ui.components.TaskRing
import app.still.launcher.ui.components.TickRing
import app.still.launcher.ui.keynote.HomeKeynote
import app.still.launcher.ui.keynote.unlockText
import app.still.launcher.keynote.UnlockObservation
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.components.holdToDrag
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.components.rememberCurrentMinute
import app.still.launcher.ui.components.is24HourTime
import app.still.launcher.ui.theme.currentAppearance
import app.still.launcher.ui.theme.fontWeight
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomePage(
    favorites: List<LauncherApp>,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    onClockClick: () -> Unit,
    /** Today's tasks and habits, for the inner ring and the line under the clock. */
    progress: List<ItemProgress>,
    /** Today against the daily phone limit, for the outer ring; null without a limit. */
    limit: LimitState?,
    /** Apps task gates keep closed right now, and how many items they wait for. */
    lockedApps: Int,
    itemsToGo: Int,
    /** The focus timer that's running, if any. */
    focus: FocusSession?,
    onDueTasksClick: () -> Unit,
    reminders: List<Reminder>,
    today: LocalDate,
    onReminderDone: (Reminder) -> Unit,
    onReminderDismiss: (Reminder) -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    onSwipeUp: () -> Unit,
    /** Swiping down; null when the gesture is turned off. */
    onSwipeDown: (() -> Unit)?,
    /** Double-tapping empty space; null when the gesture is turned off. */
    onDoubleTap: (() -> Unit)?,
    /** Precision mode was on and Android turned it off. */
    precisionLost: Boolean,
    onTurnOnPrecision: () -> Unit,
    onDismissPrecisionLost: () -> Unit,
    folders: List<HomeFolder>,
    folderStyle: FolderStyle,
    folderOpening: FolderOpening,
    appDisplay: AppDisplay,
    /** The folder that's open, if any. */
    openFolderId: String?,
    /** Opens a folder, or closes it with null. */
    onOpenFolder: (String?) -> Unit,
    onFolderLongPress: (HomeFolder) -> Unit,
    /** Long-pressing empty space: opens Customise. */
    onLongPressHome: () -> Unit,
    /** Favourites were dragged into a new order. */
    onReorderFavorites: (List<LauncherApp>) -> Unit,
    /** What the bottom corners open; null leaves a corner empty. */
    phoneButton: LauncherApp?,
    cameraButton: LauncherApp?,
    /** Today's quality and its marks; null when there's none, or the daily quality is off. */
    keynote: HomeKeynote?,
    onKeynoteClick: () -> Unit,
    /** Today's unlocks against the usual; null when that's off or unknown. */
    unlocks: UnlockObservation?,
    /** Cards for home, such as Welcome back and the morning card asking for today's quality. */
    cards: (@Composable () -> Unit)?,
    /** When focus mode started; null while it's off. */
    focusSince: Long?,
    /** The ring was held long enough to start or end focus mode. */
    onFocusHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Focus mode frames the whole screen.
    val accent = stillPalette.accent
    val frameAlpha by animateFloatAsState(if (focusSince != null) 1f else 0f, label = "focus frame")
    val openFolder = folders.firstOrNull { it.id == openFolderId }
    BackHandler(enabled = openFolder != null) { onOpenFolder(null) }
    // Spotlight and Orbit put the folder centre stage, so the clock steps back.
    val clockDimmed = openFolder != null && (folderOpening == FolderOpening.SPOTLIGHT || folderOpening == FolderOpening.ORBIT)
    val clockAlpha by animateFloatAsState(if (!clockDimmed) 1f else if (folderOpening == FolderOpening.ORBIT) 0.12f else 0.25f, label = "clock")
    Column(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (frameAlpha > 0f) {
                    val inset = FRAME_INSET.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = frameAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius(FRAME_RADIUS.toPx()),
                        style = Stroke(FRAME_WIDTH.toPx()),
                    )
                }
            }
            .verticalSwipes(onSwipeUp, onSwipeDown)
            .homeTaps(
                onTap = if (openFolder != null) ({ onOpenFolder(null) }) else null,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPressHome,
            )
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center) {
                BezelClock(
                    limit = limit,
                    progress = progress,
                    focusOn = focusSince != null,
                    onClick = onClockClick,
                    onHoldDone = onFocusHold,
                    modifier = Modifier.graphicsLayer { alpha = clockAlpha },
                )
                if (openFolder != null && folderOpening == FolderOpening.ORBIT) {
                    key(openFolder.id) {
                        Box(Modifier.requiredSize(BEZEL_SIZE + ORBIT_REACH)) {
                            OrbitApps(openFolder, appDisplay, BEZEL_SIZE + ORBIT_REACH, onLaunch, onLongPress)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = clockAlpha }) {
                Spacer(Modifier.height(8.dp))
                DateLine(limit)
                focusSince?.let { FocusLine(it) }
                keynote?.let { KeynoteLine(it, onKeynoteClick) }
                ProgressLine(progress, onDueTasksClick)
                unlocks?.let { UnlockLine(it) }
                if (lockedApps > 0) LockedApps(lockedApps, itemsToGo, onDueTasksClick)
                focus?.let { Focusing(it, onDueTasksClick) }
            }
        }
        cards?.let { content ->
            Spacer(Modifier.height(16.dp))
            content()
        }
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
        if (folders.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            FolderBar(folders, folderStyle, openFolderId, onToggle = { onOpenFolder(if (it.id == openFolderId) null else it.id) }, onLongPress = onFolderLongPress)
        }
        Box(
            contentAlignment = if (openFolder != null) Alignment.TopStart else Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = if (openFolder != null) 12.dp else 0.dp),
        ) {
            if (openFolder != null) {
                key(openFolder.id) {
                    FolderContents(
                        folder = openFolder,
                        opening = folderOpening,
                        display = appDisplay,
                        pointerAt = (folders.indexOf(openFolder) + 0.5f) / folders.size,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                    )
                }
            } else if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // A plain column, not a scrolling list, so a swipe that starts on a favorite still
                // reaches the swipe-up gesture. Home is for a handful of apps anyway.
                Favorites(favorites, appDisplay, onLaunch, onLongPress, onReorderFavorites)
            }
        }
        if (phoneButton != null || cameraButton != null) {
            // The logos line up with the edges of everything above; their touch areas reach past.
            val inset = (CORNER_TOUCH - CORNER_ICON) / 2
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                CornerButton(phoneButton, onLaunch, Modifier.offset(x = -inset))
                CornerButton(cameraButton, onLaunch, Modifier.offset(x = inset))
            }
        }
    }
}

/** A corner button: [app]'s one-colour logo, kept quiet. An empty corner when there's no app. */
@Composable
private fun CornerButton(app: LauncherApp?, onLaunch: (LauncherApp) -> Unit, modifier: Modifier = Modifier) {
    if (app == null) {
        Spacer(modifier.size(CORNER_TOUCH))
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(CORNER_TOUCH)
            .clip(CircleShape)
            .clickable { onLaunch(app) }
            .semantics { contentDescription = app.label },
    ) {
        AppIcon(app, size = CORNER_ICON, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CORNER_TOUCH = 48.dp
private val CORNER_ICON = 26.dp

/**
 * Calls [onSwipeUp] or [onSwipeDown] after a drag of at least [SWIPE_DISTANCE] that way. Taps
 * and sideways swipes (to the other pages) pass through untouched.
 */
@Composable
private fun Modifier.verticalSwipes(onSwipeUp: () -> Unit, onSwipeDown: (() -> Unit)?): Modifier {
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val distance = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    return pointerInput(Unit) {
        var dragged = 0f
        detectVerticalDragGestures(
            onDragStart = { dragged = 0f },
            onVerticalDrag = { change, amount ->
                dragged += amount
                change.consume()
            },
            onDragEnd = {
                when {
                    dragged <= -distance -> currentOnSwipeUp()
                    dragged >= distance -> currentOnSwipeDown?.invoke()
                }
            },
        )
    }
}

private val SWIPE_DISTANCE = 72.dp

/**
 * Taps on empty space: [onTap] (closing an open folder), [onDoubleTap] (locking) and
 * [onLongPress] (Customise). Apps, the clock and cards handle their own taps, so tapping them
 * doesn't count. [onTap] and [onDoubleTap] can be null when they do nothing.
 */
@Composable
private fun Modifier.homeTaps(onTap: (() -> Unit)?, onDoubleTap: (() -> Unit)?, onLongPress: () -> Unit): Modifier {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    return pointerInput(onTap != null, onDoubleTap != null) {
        detectTapGestures(
            onTap = if (onTap != null) ({ currentOnTap?.invoke() }) else null,
            onDoubleTap = if (onDoubleTap != null) ({ currentOnDoubleTap?.invoke() }) else null,
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongPress()
            },
        )
    }
}

/**
 * The favourites as names, icons and names, or a grid of icons. Hold one and drag it to move
 * it: the others make room as it passes, and the new order is saved when it's let go. A hold
 * released in place opens the app's menu instead.
 */
@Composable
private fun Favorites(
    favorites: List<LauncherApp>,
    display: AppDisplay,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    onReorder: (List<LauncherApp>) -> Unit,
) {
    val icons = display == AppDisplay.ICONS_ONLY
    val columns = if (icons) ICON_COLUMNS else 1
    var order by remember(favorites) { mutableStateOf(favorites) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // One slot's size, gaps included, to tell when the dragged app has reached the next one.
    var slot by remember { mutableStateOf(Size.Zero) }
    val rowGap = with(LocalDensity.current) { ICON_ROW_GAP.toPx() }
    val haptics = LocalHapticFeedback.current

    fun dragBy(step: Offset) {
        offset += step
        val from = order.indexOfFirst { it.key == dragged }
        if (from < 0 || slot.width == 0f || slot.height == 0f) return
        val columnShift = if (columns > 1) (offset.x / slot.width).roundToInt() else 0
        val rowShift = (offset.y / slot.height).roundToInt()
        val to = (from + rowShift * columns + columnShift).coerceIn(0, order.lastIndex)
        if (to == from) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // It now sits in its new slot; move the offset by as much so it stays under the finger.
        offset -= Offset((to % columns - from % columns) * slot.width, (to / columns - from / columns) * slot.height)
    }

    @Composable
    fun Item(app: LauncherApp, modifier: Modifier = Modifier) {
        val lifted = dragged == app.key
        AppItem(
            app = app,
            display = display,
            onClick = {},
            onLongClick = {},
            clickable = false,
            style = MaterialTheme.typography.headlineMedium,
            circleSize = 60.dp,
            modifier = modifier
                .zIndex(if (lifted) 1f else 0f)
                .graphicsLayer {
                    if (lifted) {
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = LIFTED_SCALE
                        scaleY = LIFTED_SCALE
                    }
                }
                .holdToDrag(
                    key = app.key,
                    onTap = { onLaunch(app) },
                    onLift = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragged = app.key
                        offset = Offset.Zero
                    },
                    onDrag = ::dragBy,
                    onDrop = { moved ->
                        dragged = null
                        offset = Offset.Zero
                        if (moved) onReorder(order) else onLongPress(app)
                    },
                ),
        )
    }

    if (icons) {
        Column(verticalArrangement = Arrangement.spacedBy(ICON_ROW_GAP), modifier = Modifier.fillMaxWidth()) {
            order.chunked(columns).forEach { row ->
                // The lifted app's row draws on top, so it passes over the rows below.
                Row(Modifier.fillMaxWidth().zIndex(if (row.any { it.key == dragged }) 1f else 0f)) {
                    row.forEach { app ->
                        key(app.key) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .onSizeChanged { slot = Size(it.width.toFloat(), it.height + rowGap) },
                            ) { Item(app) }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Column {
            order.forEach { app ->
                key(app.key) { Item(app, Modifier.onSizeChanged { slot = Size(it.width.toFloat(), it.height.toFloat()) }) }
            }
        }
    }
}

/** A lifted app grows a little, so it's clear what's being moved. */
private const val LIFTED_SCALE = 1.06f
private val ICON_ROW_GAP = 16.dp

private const val ICON_COLUMNS = 4

/** How far Orbit's apps reach beyond the clock's rings. */
private val ORBIT_REACH = 40.dp

/**
 * The clock inside two rings, like a watch: the ticks fill with today's phone time against the
 * daily limit (amber when it's close, red when it's used up), and the inner ring has one segment
 * per item on today's list. Tapping it opens the alarms.
 */
@Composable
private fun BezelClock(
    limit: LimitState?,
    progress: List<ItemProgress>,
    focusOn: Boolean,
    onClick: () -> Unit,
    onHoldDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val now by rememberCurrentMinute()
    val palette = stillPalette
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // How far through the hold, 0 to 1; 0 while the ring isn't held.
    var held by remember { mutableFloatStateOf(0f) }
    val holdMillis = holdMillisFor(focusOn)
    val currentHoldMillis by rememberUpdatedState(holdMillis)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHoldDone by rememberUpdatedState(onHoldDone)
    val ringColor = when {
        limit?.isReached == true -> palette.danger
        limit?.isWarning == true -> palette.warning
        else -> palette.accent
    }
    val appearance = currentAppearance
    val is24Hour = is24HourTime()
    val timePattern = if (is24Hour) "HH:mm" else "h:mm"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BEZEL_SIZE)
            .clip(CircleShape)
            // A tap opens the alarms; holding starts focus mode (or, held far longer, ends it).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val total = currentHoldMillis
                    var done = false
                    val timer = scope.launch {
                        val start = withFrameMillis { it }
                        while (true) {
                            val elapsed = withFrameMillis { it } - start
                            held = (elapsed.toFloat() / total).coerceIn(0f, 1f)
                            if (elapsed >= total) {
                                done = true
                                held = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnHoldDone()
                                break
                            }
                        }
                    }
                    var lastMillis = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent()
                        // Kept, so the pages don't swipe and home's long-press doesn't fire meanwhile.
                        event.changes.forEach {
                            it.consume()
                            lastMillis = it.uptimeMillis
                        }
                    } while (event.changes.any { it.pressed })
                    timer.cancel()
                    held = 0f
                    if (!done && lastMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) currentOnClick()
                }
            },
    ) {
        TickRing(fraction = limit?.fraction ?: 0f, lit = ringColor, unlit = palette.faint, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val fraction = held
            if (fraction > 0f) {
                val width = HOLD_ARC_WIDTH.toPx()
                drawArc(
                    color = palette.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        TaskRing(progress, done = palette.accent, overdue = palette.warning, pending = palette.faint, modifier = Modifier.size(TASK_RING_SIZE))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern(timePattern, locale)),
                    // The clock is sized to its rings, so STILL's text size leaves it as it is.
                    fontSize = (CLOCK_FONT_SIZE / appearance.textSize.scale).sp,
                    fontWeight = appearance.clockWeight.fontWeight(),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.alignByBaseline(),
                )
                if (!is24Hour && appearance.clockAmPm) {
                    Text(
                        text = now.format(DateTimeFormatter.ofPattern("a", locale)),
                        fontSize = (AM_PM_FONT_SIZE / appearance.textSize.scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 4.dp),
                    )
                }
            }
            val heldMillis = (held * holdMillis).toLong()
            if (heldMillis > HOLD_HINT_AFTER_MILLIS) {
                val secondsLeft = ((holdMillis - heldMillis + 999) / 1000).toInt()
                Text(
                    text = if (focusOn) pluralStringResource(R.plurals.focus_hold_end, secondsLeft, secondsLeft) else stringResource(R.string.focus_hold_start),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                )
            } else {
                limit?.takeIf { it.isOn }?.let {
                    Text(
                        text = if (it.isReached) stringResource(R.string.home_times_up) else stringResource(R.string.limit_left, formatDuration(it.left)),
                        style = MaterialTheme.typography.labelLarge,
                        color = ringColor,
                    )
                }
            }
        }
    }
}

private val BEZEL_SIZE = 236.dp
private const val CLOCK_FONT_SIZE = 52f
private const val AM_PM_FONT_SIZE = 14f
private val TASK_RING_SIZE = 172.dp
private val HOLD_ARC_WIDTH = 4.dp

/** A press shorter than this is a tap, so it doesn't flash "Keep holding". */
private const val HOLD_HINT_AFTER_MILLIS = 300L

/** Focus mode's frame around the whole screen, following its rounded corners. */
private val FRAME_INSET = 4.dp
private val FRAME_WIDTH = 3.dp
private val FRAME_RADIUS = 32.dp

/** "Focus mode · 42m" under the date while focus mode is on. */
@Composable
private fun FocusLine(since: Long) {
    val now by rememberCurrentMinute()
    val minutes = Duration.between(Instant.ofEpochMilli(since), now.toInstant()).toMinutes()
    Text(
        text = if (minutes < 1) stringResource(R.string.focus_mode_just_started) else stringResource(R.string.focus_mode_for, formatDuration(Duration.ofMinutes(minutes))),
        style = MaterialTheme.typography.titleSmall,
        color = stillPalette.accent,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** The date, or that the day's phone time is up. */
@Composable
private fun DateLine(limit: LimitState?) {
    val locale = LocalLocale.current.platformLocale
    val now by rememberCurrentMinute()
    if (limit?.isReached == true) {
        Text(stringResource(R.string.home_limit_reached), style = MaterialTheme.typography.titleSmall, color = stillPalette.danger)
    } else {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** More reminders than this are summarised as "+N more" so favorites keep their space. */
private const val MAX_REMINDER_CARDS = 3

/** "4 of 7 done · 1 overdue" under the date; tapping it opens the Today page. Hidden when nothing is due today. */
@Composable
private fun ProgressLine(progress: List<ItemProgress>, onClick: () -> Unit) {
    if (progress.isEmpty()) return
    val done = progress.count { it == ItemProgress.DONE }
    val overdue = progress.count { it == ItemProgress.OVERDUE }
    val doneText = stringResource(R.string.home_progress, done, progress.size)
    val overdueText = pluralStringResource(R.plurals.home_tasks_overdue, overdue, overdue)
    val warning = stillPalette.warning
    Text(
        text = buildAnnotatedString {
            append(doneText)
            if (overdue > 0) {
                append(" · ")
                withStyle(SpanStyle(color = warning)) { append(overdueText) }
            }
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/** Today's quality, with a mark for each time it was practised; tapping it shows what counted. */
@Composable
private fun KeynoteLine(keynote: HomeKeynote, onClick: () -> Unit) {
    val accent = stillPalette.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(keynote.quality, style = MaterialTheme.typography.titleMedium, color = accent)
        if (keynote.marks > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(minOf(keynote.marks, MAX_MARKS)) {
                    Box(Modifier.size(width = 2.dp, height = 12.dp).background(accent, RoundedCornerShape(1.dp)))
                }
                if (keynote.marks > MAX_MARKS) Text("+${keynote.marks - MAX_MARKS}", style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
    }
}

/** More marks than this show as "+N". */
private const val MAX_MARKS = 12

/** "18 unlocks · fewer than usual by now" under the due count. */
@Composable
private fun UnlockLine(unlocks: UnlockObservation) {
    Text(
        text = unlockText(unlocks),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** "Focusing on Read until 14:35" while the focus timer runs; tapping it opens the Today page. */
@Composable
private fun Focusing(session: FocusSession, onClick: () -> Unit) {
    val until = Instant.ofEpochMilli(session.endMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    Text(
        text = stringResource(R.string.home_focus, session.title, formatTime(until)),
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
