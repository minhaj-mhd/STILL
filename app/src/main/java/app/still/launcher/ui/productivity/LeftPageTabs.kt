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
import androidx.compose.ui.unit.dp
import app.still.launcher.R

/** The left page's two views. */
enum class LeftTab { TODAY, NOTES }

/** "Today  Notes" as the page heading; the current one is bright, the other dimmed. */
@Composable
fun LeftPageTabs(selected: LeftTab, onSelect: (LeftTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Tab(stringResource(R.string.today_title), selected == LeftTab.TODAY) { onSelect(LeftTab.TODAY) }
        Tab(stringResource(R.string.notes_title), selected == LeftTab.NOTES) { onSelect(LeftTab.NOTES) }
    }
}

@Composable
private fun Tab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .semantics { selected = isSelected }
            .clickable(role = Role.Tab, onClick = onClick),
    )
}
