package app.still.launcher.ui.productivity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.still.launcher.R

/**
 * Asks for what reminders need, one thing at a time: notifications first, then exact alarms.
 * Shows nothing once both are allowed. Rechecked whenever STILL comes back to the front.
 */
@Composable
fun ReminderPermissionBanner(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(viewModel.canNotify()) }
    var canScheduleExact by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    LifecycleResumeEffect(Unit) {
        canNotify = viewModel.canNotify()
        canScheduleExact = viewModel.canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = viewModel.canNotify()
        // Android stops showing the dialog after repeated refusals; the settings page still works.
        if (!granted) context.startActivity(notificationSettingsIntent(context))
    }

    when {
        !canNotify -> Banner(stringResource(R.string.reminder_permission_notifications), modifier) {
            val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsRuntimePermission) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(notificationSettingsIntent(context))
        }
        !canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Banner(stringResource(R.string.reminder_permission_alarms), modifier) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                )
            }
    }
}

@Composable
private fun Banner(text: String, modifier: Modifier, onAllow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onAllow, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.reminder_permission_allow))
            }
        }
    }
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
