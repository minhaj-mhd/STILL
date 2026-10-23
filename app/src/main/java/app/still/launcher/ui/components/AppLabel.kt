package app.still.launcher.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.apps.FADE_FROM_MINUTES
import app.still.launcher.apps.fadeForUse
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration

/**
 * An app shown as text only, the way both the home screen and the app list draw it. With
 * [clickable] off, taps are left to the caller, e.g. home's drag-to-reorder. [highlighted]
 * draws it in the accent colour, e.g. the search result Enter would open. With [fadeWithUse],
 * an app that has taken a lot of today fades and says how long.
 */
@Composable
fun AppLabel(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    dimmed: Boolean = false,
    clickable: Boolean = true,
    highlighted: Boolean = false,
    fadeWithUse: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!clickable) Modifier
                else Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
            )
            .padding(vertical = 10.dp),
    ) {
        // Blocked and gated apps stay listed, greyed out, so you can see what's closed.
        val closed = app.isBlocked || app.isGated || app.isLimited
        val color = when {
            dimmed || closed -> MaterialTheme.colorScheme.onSurfaceVariant
            highlighted -> stillPalette.accent
            else -> MaterialTheme.colorScheme.onBackground
        }
        val strength = if (fadeWithUse) fadeForUse(app.minutesToday) else 1f
        Text(
            text = app.label,
            style = style,
            color = color.copy(alpha = color.alpha * strength),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (fadeWithUse && app.minutesToday > FADE_FROM_MINUTES) Tag(formatDuration(Duration.ofMinutes(app.minutesToday.toLong())))
        if (app.isWorkProfile) Tag(stringResource(R.string.apps_work_label))
        if (app.isBlocked) Tag(stringResource(R.string.apps_blocked_label))
        else if (app.isGated || app.isLimited) Tag(stringResource(R.string.apps_locked_label))
    }
}

/** A small grey word after an app's name, e.g. "work", "blocked" or "locked". */
@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
    )
}
