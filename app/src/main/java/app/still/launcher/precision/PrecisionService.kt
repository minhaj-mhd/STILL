package app.still.launcher.precision

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import app.still.launcher.AppContainer
import app.still.launcher.StillApplication
import app.still.launcher.blocking.activeBlock
import app.still.launcher.blocking.scheduledBlocks
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.blocking.BlockingSchedule
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Precision mode's Accessibility service. It only listens for windows changing, to learn which
 * app is in front, and can't read what's on screen (see res/xml/precision_service.xml).
 */
class PrecisionService : AccessibilityService() {

    private val scope = MainScope()
    private lateinit var container: AppContainer
    private val precision: PrecisionMode get() = container.precisionMode

    /** The latest blocks, schedules and app settings, kept here so each window change is checked at once. */
    private var rules: Rules? = null

    /** Whether a window's class is an activity, by "package/class". */
    private val activities = HashMap<String, Boolean>()

    private class Rules(val blocks: Map<String, Instant>, val schedules: List<BlockingSchedule>, val settings: LauncherSettings)

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = (application as StillApplication).container
        precision.attach(this)
        scope.launch {
            combine(
                container.blockingRepository.blocks,
                container.blockingRepository.schedules,
                container.preferences.settings,
                // Gates and the limit are read straight from their monitors; this only looks again when they change.
                container.taskGates.inputs,
                container.dailyLimit.state,
            ) { blocks, schedules, settings, _, _ ->
                Rules(blocks, schedules, settings)
            }.collect { latest ->
                rules = latest
                recheck()
            }
        }
        // Schedules start, blocks end and tasks fall due with the clock alone, so look at the app
        // in front now and then.
        scope.launch {
            while (true) {
                delay(RECHECK_MILLIS)
                container.taskGates.setToday(LocalDate.now())
                container.dailyLimit.refresh()
                recheck()
            }
        }
        // Remembered so home can say so if Android turns the service off, e.g. after an update.
        container.applicationScope.launch { container.preferences.setPrecisionWanted(true) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        // Keyboards, dialogs and the notification shade change windows too, but no app came to the front.
        if (isActivity(packageName, className)) check(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        scope.cancel()
        if (::container.isInitialized) precision.detach(this)
    }

    private fun check(packageName: String) {
        val now = Instant.now()
        container.appLimits.onForeground(packageName, now.toEpochMilli())
        val kind = precision.gate.onForeground(packageName, now.toEpochMilli(), ruleFor(packageName, now))
        if (kind == null) {
            container.appLimits.onAllowed(packageName, now.toEpochMilli())
            return
        }
        precision.intervene(Intervention(packageName, kind))
    }

    private fun recheck() {
        precision.gate.foreground?.let(::check)
    }

    private fun ruleFor(packageName: String, now: Instant): ForegroundRule {
        val rules = rules ?: return ForegroundRule.NONE
        if (container.essentialApps.isEssential(packageName)) return ForegroundRule.NONE
        val zone = ZoneId.systemDefault()
        val scheduled = scheduledBlocks(rules.schedules, LocalDateTime.ofInstant(now, zone))
        val alwaysOpen = rules.settings.gateAlwaysOpenPackages
        val gates = container.taskGates.closedGates(now) { it == packageName && container.essentialApps.canGate(it, alwaysOpen) }
        return ForegroundRule(
            blocked = activeBlock(packageName, rules.blocks, scheduled, now, zone) != null,
            gated = gates.isNotEmpty(),
            limited = container.dailyLimit.keepsClosed(packageName, now.toEpochMilli()),
            appClosed = container.appLimits.status(packageName, now.toEpochMilli())?.closed != null,
            asksFirst = packageName in rules.settings.launchDelayPackages || packageName in rules.settings.timeReminderPackages ||
                container.appLimits.pauseFor(packageName, now.toEpochMilli()) != null,
        )
    }

    private fun isActivity(packageName: String, className: String): Boolean = activities.getOrPut("$packageName/$className") {
        val component = ComponentName(packageName, className)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private companion object {
        const val RECHECK_MILLIS = 30_000L
    }
}
