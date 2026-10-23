package app.still.launcher.ui.limit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.limit.AppLimit
import app.still.launcher.limit.AppLimitEntry
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.editor.SwitchRow
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration
import java.time.LocalDate

/**
 * An app's own limits: minutes a day, the longest visit, a wait between visits, and whether
 * opening it asks what it's for. Choices apply at once; loosening one waits for tomorrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitSheet(
    app: LauncherApp,
    entry: AppLimitEntry?,
    day: LocalDate,
    hasUsageAccess: Boolean,
    precisionOn: Boolean,
    askWhyOn: Boolean,
    onChange: (AppLimit) -> Unit,
    onDismiss: () -> Unit,
) {
    val inForce = entry?.limitOn(day) ?: AppLimit()
    val waiting = entry?.waitingOn(day)
    // What's been chosen: a change waiting for tomorrow shows as chosen, with today's limits noted.
    val chosen = waiting?.limit ?: inForce

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Column {
                Text(stringResource(R.string.app_limit_title, app.label), style = MaterialTheme.typography.headlineSmall)
                if (app.minutesToday > 0) Hint(stringResource(R.string.app_limit_used_today, formatDuration(Duration.ofMinutes(app.minutesToday.toLong()))))
            }
            Choices(stringResource(R.string.app_limit_daily), DAILY_CHOICES, chosen.dailyMinutes) { onChange(chosen.copy(dailyMinutes = it)) }
            Choices(stringResource(R.string.app_limit_visit), VISIT_CHOICES, chosen.visitMinutes) { onChange(chosen.copy(visitMinutes = it)) }
            Choices(stringResource(R.string.app_limit_wait), WAIT_CHOICES, chosen.waitMinutes) { onChange(chosen.copy(waitMinutes = it)) }
            SwitchRow(
                title = stringResource(R.string.app_limit_ask),
                detail = stringResource(if (askWhyOn) R.string.app_limit_ask_detail else R.string.app_limit_ask_off),
                checked = chosen.askWhy,
                onCheckedChange = { onChange(chosen.copy(askWhy = it)) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (waiting != null) {
                    Text(
                        text = stringResource(R.string.app_limit_until_tomorrow, appLimitSummary(inForce)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = stillPalette.warning,
                    )
                }
                Hint(stringResource(R.string.app_limit_rule))
                if (chosen.dailyMinutes > 0 && !hasUsageAccess) Hint(stringResource(R.string.app_limit_needs_usage))
                if (!precisionOn) Hint(stringResource(R.string.app_limit_needs_precision))
            }
        }
    }
}

/** "30m a day · 10m a visit · 30m between · asks why", or "No limits". */
@Composable
fun appLimitSummary(limit: AppLimit): String {
    val parts = buildList {
        if (limit.dailyMinutes > 0) add(stringResource(R.string.app_limit_summary_daily, minutes(limit.dailyMinutes)))
        if (limit.visitMinutes > 0) add(stringResource(R.string.app_limit_summary_visit, minutes(limit.visitMinutes)))
        if (limit.waitMinutes > 0) add(stringResource(R.string.app_limit_summary_wait, minutes(limit.waitMinutes)))
        if (limit.askWhy) add(stringResource(R.string.app_limit_summary_ask))
    }
    return if (parts.isEmpty()) stringResource(R.string.app_limit_none) else parts.joinToString(" · ")
}

@Composable
private fun minutes(minutes: Int): String = formatDuration(Duration.ofMinutes(minutes.toLong()))

@Composable
private fun Choices(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        FieldLabel(label)
        ChipRow {
            options.forEach { minutes ->
                Chip(if (minutes == 0) stringResource(R.string.app_limit_off) else minutes(minutes), selected == minutes) { onSelect(minutes) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val DAILY_CHOICES = listOf(0, 15, 30, 45, 60, 90, 120)
private val VISIT_CHOICES = listOf(0, 5, 10, 15, 20, 30)
private val WAIT_CHOICES = listOf(0, 15, 30, 60, 120)
