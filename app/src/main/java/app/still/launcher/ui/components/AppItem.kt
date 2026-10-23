package app.still.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.still.launcher.data.AppDisplay
import app.still.launcher.data.MonoIcon
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.theme.stillPalette
import kotlin.math.roundToInt

/** Loads an app's one-colour icon at a size in pixels; null when its icon can't be read. */
fun interface AppIconLoader {
    suspend fun load(app: LauncherApp, sizePx: Int): MonoIcon?
}

/** How composables below get app icons; the launcher provides it. */
val LocalAppIconLoader = staticCompositionLocalOf { AppIconLoader { _, _ -> null } }

/**
 * An app's one-colour icon: its logo tinted [tint], or a grey version of its icon when the logo
 * fills it. Closed apps (blocked, gated or past the daily limit) are greyed like their names.
 */
@Composable
fun AppIcon(app: LauncherApp, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val loader = LocalAppIconLoader.current
    val sizePx = with(LocalDensity.current) { size.toPx().roundToInt() }
    val icon by produceState<MonoIcon?>(null, app.key, sizePx) { value = loader.load(app, sizePx) }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        when (val current = icon) {
            is MonoIcon.Mask -> Image(current.bitmap, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
            is MonoIcon.Grey -> Image(current.bitmap, contentDescription = null, alpha = GREY_ICON_ALPHA, modifier = Modifier.size(size))
            // Still loading, or an icon that can't be read: leave the space empty rather than guess.
            null -> Unit
        }
    }
}

/**
 * An app on home or in a folder, the way [display] says: by name, by icon and name, or by icon
 * alone in a circle. Names keep [AppLabel]'s tags and greying for closed apps. With [clickable]
 * off, taps are left to the caller.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: LauncherApp,
    display: AppDisplay,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    circleSize: Dp = 52.dp,
    clickable: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val clicks = if (!clickable) Modifier else Modifier.combinedClickable(onClick = onClick, onLongClick = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
    })
    val closed = app.isBlocked || app.isGated || app.isLimited
    val color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    when (display) {
        AppDisplay.TEXT -> AppLabel(app = app, onClick = onClick, onLongClick = onLongClick, modifier = modifier, style = style, clickable = clickable)
        AppDisplay.ICON_AND_NAME -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .then(clicks)
                    .padding(vertical = 8.dp),
            ) {
                AppIcon(app, size = with(LocalDensity.current) { style.fontSize.toDp() }, tint = color)
                Text(app.label, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AppDisplay.ICONS_ONLY -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .border(1.dp, stillPalette.faint, CircleShape)
                    .then(clicks)
                    .semantics { contentDescription = app.label },
            ) {
                AppIcon(app, size = circleSize * 0.5f, tint = color)
            }
        }
    }
}

/** Grey icons are toned down a little so they sit with the tinted ones. */
private const val GREY_ICON_ALPHA = 0.85f
