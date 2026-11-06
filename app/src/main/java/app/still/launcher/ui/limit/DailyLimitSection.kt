package app.still.launcher.ui.limit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.limit.LimitPlan
import app.still.launcher.limit.LimitState
import app.still.launcher.limit.Uncounted
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.blocking.AppPickerSheet
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.TickRing
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.SwitchRow
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration

private const val STEP_MINUTES = 15
private const val FIRST_LIMIT_MINUTES = 60
private const val MAX_LIMIT_MINUTES = 12 * 60
private val WARN_MINUTES = listOf(0, 5, 15, 30)

/**
 * The daily limit page, opened from the screen time in Insights: today against the limit on the
 * same tick ring as home, and the limit's settings. Raising the limit, or switching that rule
 * off, may only count tomorrow. Allowing more apps while time's up asks first.
 */
@Composable
fun DailyLimitPage(
    apps: List<LauncherApp>,
    hasUsageAccess: Boolean,
    onAllowUsageAccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: DailyLimitViewModel = viewModel(factory = DailyLimitViewModel.Factory),
) {
    BackHandler(onBack = onBack)
    EditorScaffold(title = stringResource(R.string.limit_title), onDone = onBack) {
        DailyLimitSettings(apps, hasUsageAccess, onAllowUsageAccess, viewModel)
    }
}

@Composable
private fun DailyLimitSettings(
    apps: List<LauncherApp>,
    hasUsageAccess: Boolean,
    onAllowUsageAccess: () -> Unit,
    viewModel: DailyLimitViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var pickingApps by rememberSaveable { mutableStateOf(false) }
    var confirmingApps by rememberSaveable { mutableStateOf(false) }
    val palette = stillPalette
    // One row per package; essential apps always open, so they aren't offered.
    val candidates = remember(apps) { apps.filterNot { viewModel.alwaysOpen(it.component.packageName) }.distinctBy { it.component.packageName } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasUsageAccess) {
            Text(stringResource(R.string.limit_needs_access), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onAllowUsageAccess) { Text(stringResource(R.string.screen_time_allow)) }
            return@Column
        }

        Summary(state, settings.plan.isOn || settings.pending?.plan?.isOn == true)
        state?.takeIf { it.isOn }?.let { UncountedTime(it.uncounted) }

        val plan = settings.pending?.plan ?: settings.plan
        Stepper(
            minutes = plan.weekdayMinutes,
            label = if (plan.weekendMinutes != null) stringResource(R.string.limit_weekdays) else null,
        ) { minutes -> viewModel.setLimit(minutes, plan.weekendMinutes?.takeIf { minutes > 0 }) }
        if (plan.isOn) {
            ChipRow {
                Chip(stringResource(R.string.limit_same_every_day), plan.weekendMinutes == null) {
                    viewModel.setLimit(plan.weekdayMinutes, null)
                }
                Chip(stringResource(R.string.limit_different_weekends), plan.weekendMinutes != null) {
                    viewModel.setLimit(plan.weekdayMinutes, plan.weekendMinutes ?: plan.weekdayMinutes)
                }
            }
            plan.weekendMinutes?.let { weekend ->
                Stepper(minutes = weekend, label = stringResource(R.string.limit_weekends), allowOff = false) { minutes ->
                    viewModel.setLimit(plan.weekdayMinutes, minutes)
                }
            }
        }
        settings.pending?.takeIf { it.plan != settings.planOn(state?.day ?: it.from) }?.let { pending ->
            Text(
                text = stringResource(R.string.limit_from_tomorrow, planText(pending.plan)),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.warning,
            )
        }

        if (plan.isOn) {
            FieldLabel(stringResource(R.string.limit_warn_before))
            ChipRow {
                WARN_MINUTES.forEach { minutes ->
                    val label = if (minutes == 0) stringResource(R.string.limit_warn_off) else stringResource(R.string.time_minutes, minutes)
                    Chip(label, settings.warnMinutes == minutes) { viewModel.setWarnMinutes(minutes) }
                }
            }

            FieldLabel(stringResource(R.string.limit_day_starts))
            ChipRow {
                Chip(stringResource(R.string.limit_midnight_option), settings.dayStartHour == 0) { viewModel.setDayStartHour(0) }
                Chip(stringResource(R.string.limit_four_am), settings.dayStartHour == 4) { viewModel.setDayStartHour(4) }
            }

            FieldLabel(stringResource(R.string.limit_allowed))
            val allowed = candidates.filter { it.component.packageName in settings.allowed }
            if (allowed.isNotEmpty()) Text(allowed.joinToString(", ") { it.label }, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.limit_always_open),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { if (state?.isReached == true) confirmingApps = true else pickingApps = true }) {
                Text(stringResource(R.string.schedules_choose_apps))
            }

            SwitchRow(
                title = stringResource(R.string.limit_counts_allowed),
                detail = stringResource(R.string.limit_counts_allowed_detail),
                checked = settings.countsAllowed,
                onCheckedChange = viewModel::setCountsAllowed,
            )
            SwitchRow(
                title = stringResource(R.string.limit_raise_tomorrow),
                detail = stringResource(
                    if (!settings.raiseStartsTomorrow && settings.raiseRuleOffFrom != null) R.string.limit_raise_tomorrow_off_soon
                    else R.string.limit_raise_tomorrow_detail,
                ),
                checked = settings.raiseStartsTomorrow,
                onCheckedChange = viewModel::setRaiseRule,
            )
        }
    }

    if (pickingApps) {
        AppPickerSheet(
            apps = candidates,
            usage = emptyMap(),
            selected = settings.allowed,
            onChange = viewModel::setAllowed,
            onDismiss = { pickingApps = false },
        )
    }
    if (confirmingApps) {
        AlertDialog(
            onDismissRequest = { confirmingApps = false },
            title = { Text(stringResource(R.string.limit_reached_title)) },
            text = { Text(stringResource(R.string.limit_allow_more_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingApps = false
                    pickingApps = true
                }) { Text(stringResource(R.string.schedules_choose_apps)) }
            },
            dismissButton = { TextButton(onClick = { confirmingApps = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The ring with time used, and what's left or when it resets. */
@Composable
private fun Summary(state: LimitState?, isOn: Boolean) {
    val palette = stillPalette
    val color = when {
        state?.isReached == true -> palette.danger
        state?.isWarning == true -> palette.warning
        else -> palette.accent
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
            TickRing(fraction = state?.fraction ?: 0f, lit = color, unlit = palette.faint, modifier = Modifier.size(112.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(state?.used ?: Duration.ZERO), style = MaterialTheme.typography.titleMedium)
                state?.takeIf { it.isOn }?.let {
                    Text(
                        text = stringResource(R.string.limit_of, formatDuration(it.limit)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column {
            val headline = when {
                !isOn || state == null -> stringResource(R.string.limit_none)
                state.isReached -> stringResource(R.string.limit_reached_title)
                else -> stringResource(R.string.limit_left, formatDuration(state.left))
            }
            Text(headline, style = MaterialTheme.typography.titleMedium, color = if (state?.isOn == true) color else MaterialTheme.colorScheme.onBackground)
            state?.takeIf { it.isOn }?.let {
                Text(
                    text = if (it.isReached) stringResource(R.string.limit_only_allowed_until, resetText(it))
                    else stringResource(R.string.limit_resets_at, resetText(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Time today that didn't use up the limit, by why, so the limit and the phone's own screen time
 * can be told apart. Nothing under a minute is shown.
 */
@Composable
private fun UncountedTime(uncounted: Map<Uncounted, Duration>) {
    val shown = uncounted.filterValues { it.toMinutes() > 0 }
    if (shown.isEmpty()) return
    val total = shown.values.fold(Duration.ZERO, Duration::plus)
    val parts = Uncounted.entries.mapNotNull { why ->
        shown[why]?.let { time ->
            val label = when (why) {
                Uncounted.ALLOWED -> R.string.limit_uncounted_allowed
                Uncounted.ALWAYS_OPEN -> R.string.limit_uncounted_always_open
                Uncounted.STILL -> R.string.limit_uncounted_still
            }
            stringResource(label, formatDuration(time))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.limit_uncounted, formatDuration(total)), style = MaterialTheme.typography.bodyMedium)
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** − 3h + in steps of a quarter of an hour; below the smallest step it switches the limit off when [allowOff]. */
@Composable
private fun Stepper(minutes: Int, label: String?, allowOff: Boolean = true, onChange: (Int) -> Unit) {
    val lowest = if (allowOff) 0 else STEP_MINUTES * 2
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 80.dp)) }
        RoundButton("−") {
            val next = minutes - STEP_MINUTES
            onChange(if (next < STEP_MINUTES * 2) lowest else next)
        }
        Text(
            text = if (minutes == 0) stringResource(R.string.limit_off) else formatDuration(Duration.ofMinutes(minutes.toLong())),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 84.dp),
        )
        RoundButton("+") {
            onChange(if (minutes == 0) FIRST_LIMIT_MINUTES else (minutes + STEP_MINUTES).coerceAtMost(MAX_LIMIT_MINUTES))
        }
    }
}

@Composable
private fun RoundButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .border(1.dp, stillPalette.faint, CircleShape)
            .clickable(onClick = onClick),
    ) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

/** "3h", or "3h · 4h at weekends", or "no limit". */
@Composable
private fun planText(plan: LimitPlan): String = when {
    !plan.isOn -> stringResource(R.string.limit_none)
    plan.weekendMinutes == null -> formatDuration(Duration.ofMinutes(plan.weekdayMinutes.toLong()))
    else -> stringResource(
        R.string.limit_plan_weekends,
        formatDuration(Duration.ofMinutes(plan.weekdayMinutes.toLong())),
        formatDuration(Duration.ofMinutes(plan.weekendMinutes.toLong())),
    )
}
