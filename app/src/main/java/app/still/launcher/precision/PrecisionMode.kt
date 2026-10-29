package app.still.launcher.precision

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import app.still.launcher.MainActivity
import app.still.launcher.util.accessibilitySettingsIntent
import app.still.launcher.util.isAccessibilityServiceSwitchedOn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Precision mode: STILL's optional Accessibility service. It only notices which app comes to the
 * front, so blocks, schedules, launch delays and time reminders apply however an app is opened
 * (Recents, a notification, a link), and it can lock the screen. The service and the rest of
 * STILL run in one process and meet here.
 */
class PrecisionMode(context: Context) {
    private val appContext = context.applicationContext

    val gate = ForegroundGate()

    /** Requests for STILL's screen to step in front of an app; the launcher UI collects them. */
    val interventions = Channel<Intervention>(Channel.CONFLATED)

    private val _running = MutableStateFlow(false)

    /** Whether the service is connected. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    private var service: PrecisionService? = null

    val isRunning: Boolean get() = service != null

    internal fun attach(service: PrecisionService) {
        this.service = service
        _running.value = true
    }

    internal fun detach(service: PrecisionService) {
        if (this.service !== service) return
        this.service = null
        _running.value = false
    }

    /**
     * Whether Android's settings have the service switched on, running or not. After an update
     * Android sometimes leaves it switched on without starting it again.
     */
    fun isSwitchedOn(): Boolean = isAccessibilityServiceSwitchedOn(appContext, PrecisionService::class.java)

    /** The app in front, or null when Precision mode is off and STILL can't tell. */
    fun appInFront(): String? = if (isRunning) gate.foreground else null

    /** Brings STILL to the front to step in for an app. Does nothing when Precision mode is off. */
    fun intervene(intervention: Intervention) {
        val service = service ?: return
        interventions.trySend(intervention)
        // Android lets an app with a bound accessibility service open its screens from the background.
        service.startActivity(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Locks the screen like the power button does, so fingerprint and face unlock still work. */
    fun lockScreen(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true

    /** Pulls down the notifications, as a swipe from the top of the screen does. */
    fun openNotifications(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true

    /** Turns the service off until the user turns it on again in Android's settings. */
    fun turnOff() {
        service?.disableSelf()
    }

    /** Android's Accessibility settings, with STILL's row highlighted where Settings supports it. */
    fun settingsIntent(): Intent = accessibilitySettingsIntent(appContext, PrecisionService::class.java)

    /** STILL's App info page, where a sideloaded install can allow restricted settings. */
    fun appInfoIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${appContext.packageName}".toUri())
}
