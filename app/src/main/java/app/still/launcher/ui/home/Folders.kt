package app.still.launcher.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.still.launcher.data.AppDisplay
import app.still.launcher.data.FolderOpening
import app.still.launcher.data.FolderStyle
import app.still.launcher.ui.HomeFolder
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.AppIcon
import app.still.launcher.ui.components.AppItem
import app.still.launcher.ui.theme.stillPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The folders above the favourites, in the chosen [style]. Tapping one opens it, tapping it
 * again closes it, and long-pressing it offers rename and delete. No counts: the apps show when
 * it opens.
 */
@Composable
fun FolderBar(
    folders: List<HomeFolder>,
    style: FolderStyle,
    openId: String?,
    onToggle: (HomeFolder) -> Unit,
    onLongPress: (HomeFolder) -> Unit,
) {
    when (style) {
        FolderStyle.STRIP -> StripBar(folders, openId, onToggle, onLongPress)
        FolderStyle.TABBED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            folders.forEach { folder -> TabbedFolder(folder, folder.id == openId, { onToggle(folder) }, { onLongPress(folder) }, Modifier.weight(1f)) }
        }
        FolderStyle.PAPER_STACK -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            folders.forEach { folder -> PaperFolder(folder, folder.id == openId, { onToggle(folder) }, { onLongPress(folder) }, Modifier.weight(1f)) }
        }
        FolderStyle.ORBIT_DOTS -> Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
            folders.forEach { folder -> DotsFolder(folder, folder.id == openId, { onToggle(folder) }, { onLongPress(folder) }) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.folderClicks(onClick: () -> Unit, onLongClick: () -> Unit, haptics: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = {
        haptics()
        onLongClick()
    })

@Composable
private fun haptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
}

/** One rounded bar split into folder names; the open one fills with the accent. */
@Composable
private fun StripBar(folders: List<HomeFolder>, openId: String?, onToggle: (HomeFolder) -> Unit, onLongPress: (HomeFolder) -> Unit) {
    val palette = stillPalette
    val shape = RoundedCornerShape(14.dp)
    val haptic = haptic()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, palette.faint, shape),
    ) {
        folders.forEachIndexed { i, folder ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(palette.faint))
            val open = folder.id == openId
            val fill by animateColorAsState(if (open) palette.accent else Color.Transparent, label = "strip")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(fill)
                    .folderClicks({ onToggle(folder) }, { onLongPress(folder) }, haptic)
                    .padding(horizontal = 6.dp, vertical = 11.dp),
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (open) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A folder shape with a tab on top. */
@Composable
private fun TabbedFolder(folder: HomeFolder, open: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val palette = stillPalette
    val color = if (open) palette.accent else palette.faint
    Column(modifier.folderClicks(onClick, onLongClick, haptic())) {
        Box(Modifier.size(width = 30.dp, height = 8.dp).background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(if (open) MaterialTheme.colorScheme.surfaceVariant else palette.faint, RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp))
                .then(if (open) Modifier.border(1.dp, palette.accent, RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)) else Modifier)
                .padding(horizontal = 10.dp),
        ) { Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A few sheets of paper, with the first apps' names peeking out on top. */
@Composable
private fun PaperFolder(folder: HomeFolder, open: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val palette = stillPalette
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.height(64.dp).folderClicks(onClick, onLongClick, haptic())) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(start = 8.dp).offset(x = 4.dp).border(1.dp, palette.faint, shape))
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = 8.dp)
                .height(54.dp)
                .padding(end = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .border(1.dp, if (open) palette.accent else palette.faint, shape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = folder.apps.take(2).joinToString(", ") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small circle ringed by one dot per app, echoing the clock's rings. */
@Composable
private fun DotsFolder(folder: HomeFolder, open: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = stillPalette
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).folderClicks(onClick, onLongClick, haptic())) {
        Canvas(Modifier.size(52.dp)) {
            drawCircle(if (open) palette.accent else palette.faint, radius = 15.dp.toPx())
            val count = folder.apps.size.coerceAtLeast(1)
            repeat(folder.apps.size) { i ->
                val angle = -PI / 2 + i * 2 * PI / count
                drawCircle(palette.accent, radius = 3.dp.toPx(), center = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * 22.dp.toPx())
            }
        }
        Text(folder.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An open folder's apps, in the chosen [opening], each moving into place a moment after the
 * one before. [pointerAt] is where along the bar the open folder sits, 0 to 1, for Drop's
 * pointer. Orbit draws around the clock instead; see [OrbitApps].
 */
@Composable
fun FolderContents(
    folder: HomeFolder,
    opening: FolderOpening,
    display: AppDisplay,
    pointerAt: Float,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
) {
    val palette = stillPalette
    val tint = palette.accent.copy(alpha = 0.16f)
    val iconsOnly = display == AppDisplay.ICONS_ONLY
    @Composable
    fun item(app: LauncherApp, index: Int, style: TextStyle, circle: Dp = 52.dp, modifier: Modifier = Modifier) {
        AppItem(app, display, { onLaunch(app) }, { onLongPress(app) }, Modifier.entrance(index, opening).then(modifier), style = style, circleSize = circle)
    }
    when (opening) {
        FolderOpening.SPOTLIGHT -> if (iconsOnly) {
            Grid(folder.apps, columns = 3, spacing = 16.dp) { app, i -> item(app, i, MaterialTheme.typography.headlineSmall, circle = 60.dp) }
        } else {
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .drawBehind { drawLine(palette.accent, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx()) }
                    .padding(start = 16.dp),
            ) { folder.apps.forEachIndexed { i, app -> item(app, i, MaterialTheme.typography.headlineMedium) } }
        }
        FolderOpening.DROP -> Column(Modifier.fillMaxWidth().entrance(0, FolderOpening.DROP)) {
            Box(Modifier.fillMaxWidth().height(8.dp)) {
                Layout(content = { Box(Modifier.size(14.dp).rotate(45f).background(tint, RoundedCornerShape(2.dp))) }) { measurables, constraints ->
                    val pointer = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, 8.dp.roundToPx()) {
                        pointer.place((constraints.maxWidth * pointerAt).roundToInt() - pointer.width / 2, 1.dp.roundToPx())
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(tint, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Grid(folder.apps, columns = if (iconsOnly) 4 else 2, spacing = 6.dp) { app, i -> item(app, i + 1, MaterialTheme.typography.titleLarge, circle = 48.dp) }
            }
        }
        FolderOpening.BLOOM -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            folder.apps.forEachIndexed { i, app ->
                if (iconsOnly) {
                    item(app, i, MaterialTheme.typography.titleLarge, circle = 56.dp, modifier = Modifier.background(tint, CircleShape))
                } else {
                    Box(
                        Modifier
                            .entrance(i, opening)
                            .background(tint, RoundedCornerShape(50))
                            .border(1.dp, palette.accent, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp),
                    ) { AppItem(app, display, { onLaunch(app) }, { onLongPress(app) }, Modifier.width(IntrinsicSize.Max), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        FolderOpening.CASCADE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            folder.apps.forEachIndexed { i, app ->
                Box(
                    contentAlignment = if (iconsOnly) Alignment.Center else Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .entrance(i, opening)
                        .background(tint, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = if (iconsOnly) 6.dp else 0.dp),
                ) { AppItem(app, display, { onLaunch(app) }, { onLongPress(app) }, style = MaterialTheme.typography.titleLarge, circleSize = 44.dp) }
            }
        }
        FolderOpening.SHELF -> Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
        ) {
            folder.apps.forEachIndexed { i, app -> ShelfApp(app, display, i, opening, tint, onLaunch, onLongPress) }
        }
        FolderOpening.ORBIT -> Unit
    }
}

/** A shelf item: a tinted circle with the icon or first letter, and the name under it unless icons only. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfApp(
    app: LauncherApp,
    display: AppDisplay,
    index: Int,
    opening: FolderOpening,
    tint: Color,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
) {
    val haptic = haptic()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .entrance(index, opening)
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = {
                haptic()
                onLongPress(app)
            }),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).background(tint, CircleShape)) {
            AppIcon(app, size = 28.dp, tint = MaterialTheme.colorScheme.onBackground)
        }
        if (display != AppDisplay.ICONS_ONLY) {
            Spacer(Modifier.height(4.dp))
            Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Orbit: the open folder's apps placed in a circle around the clock, over its dimmed rings.
 * Sits in a box the size of [diameter], centred on the clock.
 */
@Composable
fun OrbitApps(
    folder: HomeFolder,
    display: AppDisplay,
    diameter: Dp,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
) {
    val palette = stillPalette
    val background = MaterialTheme.colorScheme.background
    Layout(
        content = {
            folder.apps.forEachIndexed { i, app ->
                Box(
                    Modifier
                        .entrance(i, FolderOpening.ORBIT)
                        .background(background, RoundedCornerShape(50))
                        .border(1.dp, palette.accent, RoundedCornerShape(50))
                        .padding(horizontal = if (display == AppDisplay.ICONS_ONLY) 0.dp else 12.dp),
                ) {
                    AppItem(
                        app = app,
                        display = display,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        modifier = if (display == AppDisplay.ICONS_ONLY) Modifier else Modifier.width(IntrinsicSize.Max),
                        style = MaterialTheme.typography.titleSmall,
                        circleSize = 46.dp,
                    )
                }
            }
        },
        modifier = Modifier.size(diameter),
    ) { measurables, constraints ->
        val items = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val radius = constraints.maxWidth / 2f * 0.92f
            val cx = constraints.maxWidth / 2f
            val cy = constraints.maxHeight / 2f
            items.forEachIndexed { i, item ->
                val angle = -PI / 2 + i * 2 * PI / items.size
                item.place((cx + radius * cos(angle) - item.width / 2).roundToInt(), (cy + radius * sin(angle) - item.height / 2).roundToInt())
            }
        }
    }
}

/** Apps in rows of [columns], each cell an equal share of the width. */
@Composable
private fun Grid(apps: List<LauncherApp>, columns: Int, spacing: Dp, cell: @Composable (LauncherApp, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
        apps.chunked(columns).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { column, app ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) { cell(app, row * columns + column) }
                }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** How long each opening waits between one app and the next. */
private fun FolderOpening.stepMillis(): Long = when (this) {
    FolderOpening.CASCADE -> 80
    FolderOpening.BLOOM, FolderOpening.ORBIT -> 70
    else -> 55
}

/**
 * Moves an app into place [index] steps after its folder opens, the way [opening] does it:
 * sliding in, rising, popping, being dealt like a card, or arriving from the side.
 */
@Composable
private fun Modifier.entrance(index: Int, opening: FolderOpening): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * opening.stepMillis())
        val bouncy = opening == FolderOpening.BLOOM || opening == FolderOpening.ORBIT
        progress.animateTo(1f, if (bouncy) spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow) else tween(380, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        when (opening) {
            FolderOpening.SPOTLIGHT -> translationX = (1 - p) * -18.dp.toPx()
            FolderOpening.DROP -> {
                translationY = (1 - p) * 10.dp.toPx()
                if (index == 0) {
                    scaleY = 0.6f + 0.4f * p
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
            FolderOpening.BLOOM, FolderOpening.ORBIT -> {
                scaleX = 0.5f + 0.5f * p
                scaleY = 0.5f + 0.5f * p
            }
            FolderOpening.CASCADE -> {
                translationY = (1 - p) * -34.dp.toPx()
                rotationZ = (1 - p) * -4f
            }
            FolderOpening.SHELF -> translationX = (1 - p) * 40.dp.toPx()
        }
    }
}
