package app.still.launcher.ui.components

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The round "+" in the corner of the left page's lists. */
@Composable
fun AddButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.background,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(text = "+", style = MaterialTheme.typography.headlineSmall)
    }
}
