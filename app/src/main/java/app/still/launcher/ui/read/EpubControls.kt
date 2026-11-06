package app.still.launcher.ui.read

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.ReadFont
import app.still.launcher.data.ReadText
import app.still.launcher.data.ReadTheme
import app.still.launcher.ui.components.formatDuration
import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.math.roundToInt

/** The page colour and ink for each reading theme, matching Readium's own. */
fun ReadTheme.pageColor(): Color = when (this) {
    ReadTheme.LIGHT -> Color(0xFFFFFFFF)
    ReadTheme.SEPIA -> Color(0xFFFAF4E8)
    ReadTheme.DARK -> Color(0xFF000000)
}

private fun ReadTheme.panelColor(): Color = when (this) {
    ReadTheme.LIGHT -> Color(0xFFF1EFE8)
    ReadTheme.SEPIA -> Color(0xFFEDE3CC)
    ReadTheme.DARK -> Color(0xFF1F1D18)
}

private fun ReadTheme.inkColor(): Color = when (this) {
    ReadTheme.LIGHT -> Color(0xFF2C2C2A)
    ReadTheme.SEPIA -> Color(0xFF3B2F1E)
    ReadTheme.DARK -> Color(0xFFE5E3DB)
}

private fun ReadTheme.mutedColor(): Color = when (this) {
    ReadTheme.LIGHT -> Color(0xFF5F5E5A)
    ReadTheme.SEPIA -> Color(0xFF6B5A40)
    ReadTheme.DARK -> Color(0xFF9E9E9E)
}

/**
 * What shows over an EPUB when you tap its middle: the book and the way back at the top, and the
 * text settings, how far through and this sitting's time at the bottom.
 */
@Composable
fun EpubControls(
    title: String,
    chapter: String?,
    progress: Double,
    text: ReadText,
    sittingMillis: () -> Long,
    onText: (ReadText) -> Unit,
    onBack: () -> Unit,
) {
    val ink = text.theme.inkColor()
    val muted = text.theme.mutedColor()
    val panel = text.theme.panelColor()
    var sitting by remember { mutableLongStateOf(sittingMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            sitting = sittingMillis()
            delay(SITTING_TICK_MILLIS)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(panel)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹", color = ink, style = MaterialTheme.typography.titleLarge) }
            Text(title, color = ink, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(panel, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            val smaller = stringResource(R.string.read_text_smaller)
            val larger = stringResource(R.string.read_text_larger)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.read_text), color = ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onText(text.copy(scale = (text.scale - ReadText.SCALE_STEP).coerceAtLeast(ReadText.MIN_SCALE))) },
                    modifier = Modifier.semantics { contentDescription = smaller },
                ) { Text("A−", color = muted) }
                Text(
                    text = stringResource(R.string.read_percent, (text.scale * 100).roundToInt()),
                    color = ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { onText(text.copy(scale = (text.scale + ReadText.SCALE_STEP).coerceAtMost(ReadText.MAX_SCALE))) },
                    modifier = Modifier.semantics { contentDescription = larger },
                ) { Text("A+", color = ink) }
            }
            Segments(
                options = listOf(
                    stringResource(R.string.read_font_serif) to ReadFont.SERIF,
                    stringResource(R.string.read_font_sans) to ReadFont.SANS,
                    stringResource(R.string.read_font_dyslexic) to ReadFont.DYSLEXIC,
                ),
                selected = text.font,
                theme = text.theme,
            ) { onText(text.copy(font = it)) }
            Segments(
                options = listOf(
                    stringResource(R.string.read_theme_light) to ReadTheme.LIGHT,
                    stringResource(R.string.read_theme_sepia) to ReadTheme.SEPIA,
                    stringResource(R.string.read_theme_dark) to ReadTheme.DARK,
                ),
                selected = text.theme,
                theme = text.theme,
            ) { onText(text.copy(theme = it)) }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = listOfNotNull(stringResource(R.string.read_percent, (progress * 100).roundToInt()), chapter?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.read_sitting, formatDuration(Duration.ofMillis(sitting))),
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** A row of choices, one picked. */
@Composable
private fun <T> Segments(options: List<Pair<String, T>>, selected: T, theme: ReadTheme, onSelect: (T) -> Unit) {
    val ink = theme.inkColor()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Text(
                text = label,
                color = if (isSelected) theme.panelColor() else theme.mutedColor(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = isSelected }
                    .background(if (isSelected) ink else Color.Transparent, RoundedCornerShape(14.dp))
                    .border(1.dp, if (isSelected) ink else theme.mutedColor(), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

private const val SITTING_TICK_MILLIS = 30_000L
