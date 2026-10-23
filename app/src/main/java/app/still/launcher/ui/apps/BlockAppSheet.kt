package app.still.launcher.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.AppScreenTime
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.SlideToConfirm
import app.still.launcher.ui.components.formatDateTime
import app.still.launcher.ui.components.formatDuration
import java.time.Duration
import java.time.Instant

/** The block lengths on offer, from an hour to a month. */
private val BLOCK_HOURS = listOf(1L, 2L, 4L, 8L, 12L, 24L, 48L, 72L, 7 * 24L, 14 * 24L, 30 * 24L)

/**
 * Pick how long to block [app], then slide to confirm. The last week's screen time, when
 * available, is shown as the reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockAppSheet(
    app: LauncherApp,
    screenTime: AppScreenTime?,
    onDismiss: () -> Unit,
    onBlock: (Duration) -> Unit,
) {
    var hours by rememberSaveable { mutableLongStateOf(BLOCK_HOURS.first()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.block_title, app.label), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = screenTime?.let { stringResource(R.string.block_explainer, formatDuration(it.lastSevenDays), app.label) }
                    ?: stringResource(R.string.block_explainer_no_data, app.label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.block_for))
                ChipRow {
                    BLOCK_HOURS.forEach { option ->
                        Chip(blockLengthLabel(option), selected = option == hours) { hours = option }
                    }
                }
                Text(
                    text = stringResource(R.string.block_until, formatDateTime(Instant.now().plus(Duration.ofHours(hours)))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SlideToConfirm(
                text = stringResource(R.string.block_slide),
                accessibilityLabel = stringResource(R.string.block_now),
                onConfirm = { onBlock(Duration.ofHours(hours)) },
            )
        }
    }
}

/** "1 hour", "12 hours", then whole days from 48 hours on: "2 days", "30 days". */
@Composable
private fun blockLengthLabel(hours: Long): String =
    if (hours >= 48) pluralStringResource(R.plurals.block_days, (hours / 24).toInt(), hours / 24)
    else pluralStringResource(R.plurals.block_hours, hours.toInt(), hours)
