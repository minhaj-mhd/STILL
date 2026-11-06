package app.still.launcher.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Small grey heading above a group of choices. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A row of choice chips that scrolls sideways when it doesn't fit. */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        content()
    }
}

/** A choice; [fontFamily] shows its label in a font, as when choosing one. */
@Composable
fun Chip(text: String, selected: Boolean, fontFamily: FontFamily? = null, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, fontFamily = fontFamily) })
}
