package app.still.launcher.ui.productivity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.still.launcher.R

/** The left page's three views. */
enum class LeftTab { TODAY, NOTES, PLANS }

/**
 * "Today  Notes  Plans" as the page heading; the current one is bright, the other dimmed. [compact] is
 * the small version under the Today page's date.
 */
@Composable
fun LeftPageTabs(selected: LeftTab, compact: Boolean = false, onSelect: (LeftTab) -> Unit) {
    val style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp)) {
        Tab(stringResource(R.string.today_title), selected == LeftTab.TODAY, style) { onSelect(LeftTab.TODAY) }
        Tab(stringResource(R.string.notes_title), selected == LeftTab.NOTES, style) { onSelect(LeftTab.NOTES) }
        Tab(stringResource(R.string.plans_title), selected == LeftTab.PLANS, style) { onSelect(LeftTab.PLANS) }
    }
}

@Composable
private fun Tab(text: String, isSelected: Boolean, style: TextStyle, onClick: () -> Unit) {
    Text(
        text = text,
        style = style,
        color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .semantics { selected = isSelected }
            .clickable(role = Role.Tab, onClick = onClick),
    )
}
