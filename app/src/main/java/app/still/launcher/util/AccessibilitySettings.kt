package app.still.launcher.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.os.bundleOf

/**
 * Whether Android's settings have one of STILL's Accessibility services switched on, running or
 * not. After an update Android sometimes leaves a service switched on without starting it again.
 */
fun isAccessibilityServiceSwitchedOn(context: Context, service: Class<*>): Boolean {
    val component = ComponentName(context, service)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}

/** Android's Accessibility settings, with [service]'s row highlighted where Settings supports it. */
fun accessibilitySettingsIntent(context: Context, service: Class<*>): Intent {
    val component = ComponentName(context, service).flattenToString()
    return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, bundleOf(EXTRA_FRAGMENT_ARG_KEY to component))
}

// Undocumented extras that Settings uses to scroll to and highlight one row.
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
