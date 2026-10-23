package app.still.launcher.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.BuildConfig
import app.still.launcher.R
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.blocking.SchedulesPage
import app.still.launcher.ui.blocking.SchedulesViewModel
import app.still.launcher.ui.components.AppLabel
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatDateTime
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.editor.SwitchRow
import app.still.launcher.ui.gates.GatesPage
import app.still.launcher.ui.gates.GatesViewModel
import app.still.launcher.ui.notifications.NotificationFilterPage
import app.still.launcher.ui.notifications.NotificationFilterViewModel

/** Settings and the pages it leads to. */
enum class SettingsPage { MAIN, CUSTOMISE, HIDDEN_APPS, BLOCKED_APPS, SCHEDULES, GATES, NOTIFICATION_FILTER, PRECISION }

/**
 * Settings, reached from Insights. Shows each permission STILL uses with its state and a way to
 * turn it on, the hidden and blocked apps, and the app version.
 */
@Composable
fun SettingsScreen(
    page: SettingsPage,
    onNavigate: (SettingsPage) -> Unit,
    onClose: () -> Unit,
    apps: List<LauncherApp>,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    hasUsageAccess: Boolean,
    onAllowUsageAccess: () -> Unit,
    canNotify: () -> Boolean,
    canScheduleExactAlarms: () -> Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onLongPressApp: (LauncherApp) -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    launchDelaySeconds: Int,
    onSetLaunchDelaySeconds: (Int) -> Unit,
    timeUpBlockMinutes: Int,
    onSetTimeUpBlockMinutes: (Int) -> Unit,
    precisionOn: Boolean,
    /** Switched on in Android's settings but not running. */
    precisionStuck: Boolean,
    onTurnOnPrecision: () -> Unit,
    onTurnOffPrecision: () -> Unit,
    onOpenAppInfo: () -> Unit,
    doubleTapToLock: Boolean,
    onSetDoubleTapToLock: (Boolean) -> Unit,
    appListKeyboard: Boolean,
    onSetAppListKeyboard: (Boolean) -> Unit,
    appListFade: Boolean,
    onSetAppListFade: (Boolean) -> Unit,
    onOpenHeldNotifications: () -> Unit,
) {
    BackHandler { if (page == SettingsPage.MAIN) onClose() else onNavigate(SettingsPage.MAIN) }
    val hidden = apps.filter { it.isHidden }
    // One row per package: a block covers the app in every profile.
    val blocked = apps.filter { it.isBlocked }.distinctBy { it.component.packageName }

    when (page) {
        SettingsPage.MAIN -> MainSettings(
            hiddenCount = hidden.size,
            blockedCount = blocked.size,
            launchDelayCount = apps.filter { it.hasLaunchDelay }.distinctBy { it.component.packageName }.size,
            timeReminderCount = apps.filter { it.hasTimeReminder }.distinctBy { it.component.packageName }.size,
            launchDelaySeconds = launchDelaySeconds,
            onSetLaunchDelaySeconds = onSetLaunchDelaySeconds,
            timeUpBlockMinutes = timeUpBlockMinutes,
            onSetTimeUpBlockMinutes = onSetTimeUpBlockMinutes,
            onNavigate = onNavigate,
            onClose = onClose,
            isDefaultLauncher = isDefaultLauncher,
            onSetDefaultLauncher = onSetDefaultLauncher,
            hasUsageAccess = hasUsageAccess,
            onAllowUsageAccess = onAllowUsageAccess,
            canNotify = canNotify,
            canScheduleExactAlarms = canScheduleExactAlarms,
            precisionOn = precisionOn,
            doubleTapToLock = doubleTapToLock,
            onSetDoubleTapToLock = onSetDoubleTapToLock,
            appListKeyboard = appListKeyboard,
            onSetAppListKeyboard = onSetAppListKeyboard,
            appListFade = appListFade,
            onSetAppListFade = onSetAppListFade,
        )
        SettingsPage.CUSTOMISE -> CustomisePage(onBack = { onNavigate(SettingsPage.MAIN) })
        SettingsPage.NOTIFICATION_FILTER -> NotificationFilterPage(
            apps = apps,
            onOpenHeld = onOpenHeldNotifications,
            onBack = { onNavigate(SettingsPage.MAIN) },
            onOpenAppInfo = onOpenAppInfo,
        )
        SettingsPage.PRECISION -> PrecisionPage(
            isOn = precisionOn,
            isStuck = precisionStuck,
            onTurnOn = onTurnOnPrecision,
            onTurnOff = onTurnOffPrecision,
            onOpenAppInfo = onOpenAppInfo,
            onDone = { onNavigate(SettingsPage.MAIN) },
        )
        SettingsPage.HIDDEN_APPS -> EditorScaffold(
            title = stringResource(R.string.settings_hidden_apps),
            onDone = { onNavigate(SettingsPage.MAIN) },
        ) {
            if (hidden.isEmpty()) EmptyText(stringResource(R.string.settings_hidden_empty))
            hidden.forEach { app ->
                // Tap opens the app; long-press offers Unhide with the other app actions.
                AppLabel(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPressApp(app) }, dimmed = true)
            }
        }
        SettingsPage.SCHEDULES -> SchedulesPage(
            onBack = { onNavigate(SettingsPage.MAIN) },
            onOpenEditor = onOpenEditor,
        )
        SettingsPage.GATES -> GatesPage(
            precisionOn = precisionOn,
            onBack = { onNavigate(SettingsPage.MAIN) },
            onOpenEditor = onOpenEditor,
        )
        SettingsPage.BLOCKED_APPS -> EditorScaffold(
            title = stringResource(R.string.settings_blocked_apps),
            onDone = { onNavigate(SettingsPage.MAIN) },
        ) {
            if (blocked.isEmpty()) EmptyText(stringResource(R.string.settings_blocked_empty))
            blocked.forEach { app ->
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    app.blockedUntil?.let { until ->
                        val untilText = stringResource(R.string.block_until, formatDateTime(until))
                        Text(
                            text = app.blockedBySchedule?.let { "$untilText · $it" } ?: untilText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainSettings(
    hiddenCount: Int,
    blockedCount: Int,
    launchDelayCount: Int,
    timeReminderCount: Int,
    launchDelaySeconds: Int,
    onSetLaunchDelaySeconds: (Int) -> Unit,
    timeUpBlockMinutes: Int,
    onSetTimeUpBlockMinutes: (Int) -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onClose: () -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    hasUsageAccess: Boolean,
    onAllowUsageAccess: () -> Unit,
    canNotify: () -> Boolean,
    canScheduleExactAlarms: () -> Boolean,
    precisionOn: Boolean,
    doubleTapToLock: Boolean,
    onSetDoubleTapToLock: (Boolean) -> Unit,
    appListKeyboard: Boolean,
    onSetAppListKeyboard: (Boolean) -> Unit,
    appListFade: Boolean,
    onSetAppListFade: (Boolean) -> Unit,
    schedulesViewModel: SchedulesViewModel = viewModel(factory = SchedulesViewModel.Factory),
    gatesViewModel: GatesViewModel = viewModel(factory = GatesViewModel.Factory),
    filterViewModel: NotificationFilterViewModel = viewModel(factory = NotificationFilterViewModel.Factory),
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(canNotify()) }
    var exactAlarmsOn by remember { mutableStateOf(canScheduleExactAlarms()) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = canNotify()
        exactAlarmsOn = canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = canNotify()
        // Android stops showing the dialog after repeated refusals; the settings page still works.
        if (!granted) context.startActivity(notificationSettingsIntent(context))
    }

    EditorScaffold(title = stringResource(R.string.settings_title), onDone = onClose) {
        Section(stringResource(R.string.settings_section_permissions)) {
            PermissionRow(
                title = stringResource(R.string.settings_home_app),
                detail = stringResource(R.string.settings_home_app_detail),
                isOn = isDefaultLauncher,
                onTurnOn = onSetDefaultLauncher,
            )
            PermissionRow(
                title = stringResource(R.string.settings_notifications),
                detail = stringResource(R.string.settings_notifications_detail),
                isOn = notificationsOn,
                onTurnOn = {
                    val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsRuntimePermission) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(notificationSettingsIntent(context))
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionRow(
                    title = stringResource(R.string.settings_exact_alarms),
                    detail = stringResource(R.string.settings_exact_alarms_detail),
                    isOn = exactAlarmsOn,
                    onTurnOn = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    },
                )
            }
            PermissionRow(
                title = stringResource(R.string.settings_usage_access),
                detail = stringResource(R.string.settings_usage_access_detail),
                isOn = hasUsageAccess,
                onTurnOn = onAllowUsageAccess,
            )
            // Turning it on goes through the explanation first; so does turning it off.
            PermissionRow(
                title = stringResource(R.string.settings_precision),
                detail = stringResource(R.string.settings_precision_detail),
                isOn = precisionOn,
                onTurnOn = { onNavigate(SettingsPage.PRECISION) },
                onClick = { onNavigate(SettingsPage.PRECISION) },
            )
        }

        Section(stringResource(R.string.settings_section_apps)) {
            val scheduleCount = schedulesViewModel.schedules.collectAsStateWithLifecycle().value?.size ?: 0
            NavigationRow(
                title = stringResource(R.string.settings_schedules),
                value = pluralStringResource(R.plurals.settings_schedule_count, scheduleCount, scheduleCount),
            ) { onNavigate(SettingsPage.SCHEDULES) }
            val gateCount = gatesViewModel.inputs.collectAsStateWithLifecycle().value?.gates?.size ?: 0
            NavigationRow(
                title = stringResource(R.string.gates_title),
                value = pluralStringResource(R.plurals.settings_gate_count, gateCount, gateCount),
            ) { onNavigate(SettingsPage.GATES) }
            val filterOn = filterViewModel.settings.collectAsStateWithLifecycle().value.on
            NavigationRow(
                title = stringResource(R.string.held_settings_title),
                value = stringResource(if (filterOn) R.string.settings_on else R.string.schedules_status_off),
            ) { onNavigate(SettingsPage.NOTIFICATION_FILTER) }
            NavigationRow(
                title = stringResource(R.string.settings_hidden_apps),
                value = pluralStringResource(R.plurals.settings_app_count, hiddenCount, hiddenCount),
            ) { onNavigate(SettingsPage.HIDDEN_APPS) }
            NavigationRow(
                title = stringResource(R.string.settings_blocked_apps),
                value = pluralStringResource(R.plurals.settings_app_count, blockedCount, blockedCount),
            ) { onNavigate(SettingsPage.BLOCKED_APPS) }
        }

        Section(stringResource(R.string.settings_section_mindful)) {
            Text(
                text = stringResource(R.string.settings_mindful_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CountedLabel(stringResource(R.string.settings_launch_delay), launchDelayCount)
            ChipRow {
                LAUNCH_DELAY_SECONDS.forEach { seconds ->
                    Chip(stringResource(R.string.settings_seconds, seconds), seconds == launchDelaySeconds) {
                        onSetLaunchDelaySeconds(seconds)
                    }
                }
            }
            CountedLabel(stringResource(R.string.settings_time_reminder), timeReminderCount)
            Text(stringResource(R.string.settings_time_up), style = MaterialTheme.typography.bodyMedium)
            ChipRow {
                Chip(stringResource(R.string.settings_time_up_remind), timeUpBlockMinutes == 0) { onSetTimeUpBlockMinutes(0) }
                TIME_UP_BLOCK_MINUTES.forEach { minutes ->
                    Chip(stringResource(R.string.settings_time_up_block, minutes), minutes == timeUpBlockMinutes) {
                        onSetTimeUpBlockMinutes(minutes)
                    }
                }
            }
        }

        Section(stringResource(R.string.settings_section_home)) {
            NavigationRow(title = stringResource(R.string.customise_title), value = "") { onNavigate(SettingsPage.CUSTOMISE) }
        }

        Section(stringResource(R.string.settings_section_app_list)) {
            SwitchRow(
                title = stringResource(R.string.settings_app_list_keyboard),
                detail = stringResource(R.string.settings_app_list_keyboard_detail),
                checked = appListKeyboard,
                onCheckedChange = onSetAppListKeyboard,
            )
            SwitchRow(
                title = stringResource(R.string.settings_app_list_fade),
                detail = stringResource(
                    if (hasUsageAccess) R.string.settings_app_list_fade_detail else R.string.settings_app_list_fade_needs_usage,
                ),
                checked = appListFade,
                onCheckedChange = onSetAppListFade,
            )
        }

        Section(stringResource(R.string.settings_section_gestures)) {
            SwitchRow(
                title = stringResource(R.string.settings_double_tap_lock),
                detail = stringResource(
                    if (precisionOn) R.string.settings_double_tap_lock_detail else R.string.settings_double_tap_lock_needs_precision,
                ),
                checked = doubleTapToLock,
                onCheckedChange = onSetDoubleTapToLock,
            )
        }

        Section(stringResource(R.string.settings_section_about)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title, Modifier.padding(bottom = 4.dp))
        content()
    }
}

/** A permission's name and purpose, with "On" or a button to turn it on. [onClick] makes the whole row open more. */
@Composable
private fun PermissionRow(title: String, detail: String, isOn: Boolean, onTurnOn: () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isOn) {
            Text(
                text = stringResource(R.string.settings_on),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 12.dp),
            )
        } else {
            TextButton(onClick = onTurnOn) { Text(stringResource(R.string.settings_turn_on)) }
        }
    }
}

@Composable
private fun NavigationRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$value  ›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A setting's name with how many apps use it, e.g. "Launch delay  ·  3 apps". */
@Composable
private fun CountedLabel(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            text = pluralStringResource(R.plurals.settings_app_count, count, count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LAUNCH_DELAY_SECONDS = listOf(5, 10, 15, 30)
private val TIME_UP_BLOCK_MINUTES = listOf(5, 15, 30)

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
