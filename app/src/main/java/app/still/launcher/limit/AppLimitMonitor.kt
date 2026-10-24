package app.still.launcher.limit

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.PracticeRepository
import app.still.launcher.focus.FocusSession
import app.still.launcher.focus.FocusTimer
import app.still.launcher.precision.PrecisionMode
import app.still.launcher.reminders.AppSession
import app.still.launcher.reminders.AppTimeReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/** What the pause before an app says: why it's asking, and the facts it mentions. */
data class Pause(
    val context: PauseContext,
    /** Times the app was opened in the last hour, and the first of them. */
    val opensLastHour: Int,
    val firstOpenMillis: Long?,
    /** The focus timer that's running, if any. */
    val focus: FocusSession?,
)

/**
 * Applies each app's own limits. It follows visits to limited apps: Precision mode reports each
 * app coming to the front, and without it a visit runs from STILL opening the app until STILL is
 * back on screen. It says which apps are closed (done for today, or waiting between visits),
 * times each visit with [AppTimeReminders], and decides when opening an app asks what it's for.
 */
class AppLimitMonitor(
    context: Context,
    private val preferences: LauncherPreferences,
    private val dailyLimit: DailyLimitMonitor,
    private val reminders: AppTimeReminders,
    private val precision: PrecisionMode,
    private val practice: PracticeRepository,
    private val focusTimer: FocusTimer,
    scope: CoroutineScope,
) {
    private val packageManager = context.applicationContext.packageManager
    private val settings: StateFlow<LauncherSettings> = preferences.settings.stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    private val _visits = MutableStateFlow<Map<String, AppVisit>>(emptyMap())

    /** The latest visit to each limited app. */
    val visits: StateFlow<Map<String, AppVisit>> = _visits.asStateFlow()

    /** When each app was last asked what it's for. */
    private val lastAsked = ConcurrentHashMap<String, Long>()

    /** When each limited app was opened in the last hour. */
    private val opens = HashMap<String, MutableList<Long>>()

    /** The app Precision mode last reported in front. */
    private var foreground: String? = null

    private val graceMillis: Long get() = precision.gate.graceMillis

    init {
        scope.launch {
            val saved = preferences.appVisits()
            // Anything seen since STILL started is newer than what was saved.
            _visits.update { seen -> saved + seen }
            practice.lastAsked().forEach { (packageName, at) -> lastAsked.merge(packageName, at, ::maxOf) }
            _visits.drop(1).collect { visits ->
                val since = System.currentTimeMillis() - KEEP_VISITS_MILLIS
                preferences.setAppVisits(visits.filterValues { (it.leftMillis ?: it.startMillis) >= since })
            }
        }
    }

    /** The limits in force for [packageName] at [nowMillis]; null when it has none. */
    fun limitFor(packageName: String, nowMillis: Long = System.currentTimeMillis()): AppLimit? {
        val current = settings.value
        val entry = current.appLimits[packageName] ?: return null
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        return entry.limitOn(limitDay(now, current.limit.dayStartHour)).takeIf { it.isOn }
    }

    /** Where [packageName] stands against its limits at [nowMillis]; null when it has none. */
    fun status(packageName: String, nowMillis: Long = System.currentTimeMillis()): AppLimitStatus? {
        val limit = limitFor(packageName, nowMillis) ?: return null
        val used = Duration.ofMillis(dailyLimit.appUsage.value[packageName] ?: 0L)
        return AppLimitStatus(limit, used, opensAgainAt(limit, _visits.value[packageName], nowMillis, graceMillis))
    }

    /** Precision mode saw [packageName] in front at [nowMillis]: the app before it has left. */
    @Synchronized
    fun onForeground(packageName: String, nowMillis: Long) {
        if (packageName == foreground) return
        foreground?.let { leave(it, nowMillis) }
        foreground = packageName
        // Back within the grace: the visit carries on.
        val visit = _visits.value[packageName]
        if (visit?.leftMillis != null && visit.isOngoing(nowMillis, graceMillis)) put(packageName, visit.copy(leftMillis = null))
    }

    /** Precision mode let [packageName] through: a visit starts, unless one is going on or its limits keep it closed. */
    fun onAllowed(packageName: String, nowMillis: Long) {
        val limit = limitFor(packageName, nowMillis) ?: return
        if (_visits.value[packageName]?.isOngoing(nowMillis, graceMillis) == true) return
        if (status(packageName, nowMillis)?.closed != null) return
        startVisit(packageName, labelOf(packageName), limit, nowMillis)
    }

    /** STILL opened [packageName] after its checks: a visit starts, or carries on if it's within the grace. */
    fun onOpened(packageName: String, label: String, nowMillis: Long) {
        val limit = limitFor(packageName, nowMillis) ?: return
        val visit = _visits.value[packageName]
        if (visit != null && visit.isOngoing(nowMillis, graceMillis)) {
            if (visit.leftMillis != null) put(packageName, visit.copy(leftMillis = null))
            return
        }
        startVisit(packageName, label, limit, nowMillis)
    }

    /** STILL is on screen, so no other app is: visits still open end now. */
    fun onLauncherShown(nowMillis: Long) {
        _visits.value.forEach { (packageName, visit) -> if (visit.leftMillis == null) put(packageName, visit.copy(leftMillis = nowMillis)) }
    }

    /** A visit's time ran out: coming back is a new visit, after any wait, and STILL steps in if the app is still open. */
    suspend fun onVisitTimeUp(session: AppSession) {
        val now = System.currentTimeMillis()
        val visit = _visits.value[session.packageName]
        if (visit != null && visit.startMillis == session.startMillis) {
            put(session.packageName, visit.copy(leftMillis = visit.leftMillis ?: now, timeUp = true))
        }
        dailyLimit.refresh()
        reminders.onVisitTimeUp(session, doneToday = status(session.packageName, now)?.closed == AppClosed.DONE_TODAY)
    }

    /** The pause to show before opening [packageName] at [nowMillis], or null to open without asking. */
    fun pauseFor(packageName: String, nowMillis: Long = System.currentTimeMillis()): Pause? {
        if (!settings.value.askWhyOn) return null
        val limit = limitFor(packageName, nowMillis) ?: return null
        val recent = synchronized(opens) { opens[packageName].orEmpty().filter { it >= nowMillis - HOUR_MILLIS } }
        val focus = focusTimer.session.value
        val context = pauseContext(limit, focus != null, recent.size, lastAsked[packageName], nowMillis) ?: return null
        return Pause(context, recent.size, recent.minOrNull(), focus)
    }

    /** The pause before [packageName] was shown at [nowMillis]. */
    fun onAsked(packageName: String, nowMillis: Long) {
        lastAsked[packageName] = nowMillis
    }

    private fun startVisit(packageName: String, label: String, limit: AppLimit, nowMillis: Long) {
        put(packageName, AppVisit(nowMillis))
        synchronized(opens) {
            val times = opens.getOrPut(packageName) { mutableListOf() }
            times.removeAll { it < nowMillis - HOUR_MILLIS }
            times += nowMillis
        }
        val used = Duration.ofMillis(dailyLimit.appUsage.value[packageName] ?: 0L)
        visitLength(limit, used)?.let { minutes -> reminders.startVisit(packageName, label, minutes, nowMillis) }
    }

    private fun leave(packageName: String, nowMillis: Long) {
        val visit = _visits.value[packageName] ?: return
        if (visit.leftMillis == null) put(packageName, visit.copy(leftMillis = nowMillis))
    }

    private fun put(packageName: String, visit: AppVisit) {
        _visits.update { it + (packageName to visit) }
    }

    private fun labelOf(packageName: String): String = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        info.loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private companion object {
        /** Visits older than this can't affect a wait any more. */
        const val KEEP_VISITS_MILLIS = 24 * 60 * 60 * 1000L
    }
}
