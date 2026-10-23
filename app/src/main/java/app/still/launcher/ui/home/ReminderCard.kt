package app.still.launcher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.productivity.Reminder
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.productivity.CheckCircle
import java.time.LocalDate

/**
 * A reminder that went off, kept on the home screen until it's handled: tick the circle for
 * Done, swipe either way to dismiss, or tap to open the Today page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCard(
    reminder: Reminder,
    today: LocalDate,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
    }

    SwipeToDismissBox(state = state, backgroundContent = {}) {
        Surface(
            onClick = onOpen,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                // Count and minutes habits are logged on the Today page, so they get no tick here.
                if (reminder.canMarkDone) CheckCircle(checked = false, skipped = false, onClick = onDone)
                else Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = whenText(reminder, today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "15:00" today, "Yesterday · 15:00", or "Wed 21 Oct · 15:00". */
@Composable
private fun whenText(reminder: Reminder, today: LocalDate): String {
    val time = formatTime(reminder.at.toLocalTime())
    val date = reminder.occurrence.date
    return when (date) {
        today -> time
        today.minusDays(1) -> "${stringResource(R.string.today_yesterday)} · $time"
        else -> "${formatShortDate(date)} · $time"
    }
}
