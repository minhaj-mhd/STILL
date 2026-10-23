package app.still.launcher.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.still.launcher.MainActivity
import app.still.launcher.R
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.notifications.HeldNotification
import app.still.launcher.data.notifications.HeldNotificationDao
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The notification filter. With Notification access, STILL sees each notification as it's
 * posted; ones from apps the user hasn't allowed are taken out of the shade and kept here for a
 * week, to read when they choose. Calls, alarms and ongoing notifications always come through.
 * Android may still sound or buzz for a held-back notification before STILL removes it.
 */
class NotificationFilter(
    context: Context,
    private val dao: HeldNotificationDao,
    private val preferences: LauncherPreferences,
    private val essentialApps: EssentialApps,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val notifications = NotificationManagerCompat.from(appContext)
    private val settings: StateFlow<LauncherSettings> = preferences.settings.stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    @Volatile
    private var service: NotificationListenerService? = null
    private val _connected = MutableStateFlow(false)

    /** Whether Android has connected the listener, i.e. Notification access is on and working. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Held-back notifications, newest first. */
    val held: Flow<List<HeldNotification>> = dao.observeAll()

    init {
        // The count in the shade follows the list, whoever changes it.
        scope.launch {
            combine(dao.observeCount(), settings) { count, current ->
                if (current.notificationFilterOn && current.notificationCountShown) count else 0
            }.distinctUntilChanged().collect(::showCount)
        }
        // Switching the filter on clears the shade of what it would have held back.
        scope.launch {
            settings.map { it.notificationFilterOn }.distinctUntilChanged().collect { on -> if (on) sweep() }
        }
    }

    fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName(appContext.getString(R.string.held_channel_name))
            .setDescription(appContext.getString(R.string.held_channel_description))
            .build()
        notifications.createNotificationChannel(channel)
    }

    fun hasAccess(): Boolean = appContext.packageName in NotificationManagerCompat.getEnabledListenerPackages(appContext)

    /** Android's page for STILL's Notification access, or the list of apps on older versions. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(appContext, NotificationFilterService::class.java).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** Asks Android to connect the listener again, e.g. after an update, when access is on. */
    fun rebindIfAllowed() {
        if (hasAccess()) NotificationListenerService.requestRebind(ComponentName(appContext, NotificationFilterService::class.java))
    }

    /** The first time it's switched on, the default messages app is let through to begin with. */
    suspend fun setOn(on: Boolean) {
        preferences.setNotificationFilterOn(on, firstAllowed = setOfNotNull(Telephony.Sms.getDefaultSmsPackage(appContext)))
    }

    suspend fun clearAll() = dao.deleteAll()

    suspend fun clear(packageName: String) = dao.deleteForPackage(packageName)

    internal fun attach(service: NotificationListenerService) {
        this.service = service
        _connected.value = true
        scope.launch { sweep() }
    }

    internal fun detach(service: NotificationListenerService) {
        if (this.service !== service) return
        this.service = null
        _connected.value = false
    }

    internal fun onPosted(service: NotificationListenerService, sbn: StatusBarNotification) {
        val current = settings.value
        if (!current.notificationFilterOn) return
        val notification = sbn.notification
        val posted = PostedNotification(
            packageName = sbn.packageName,
            isOngoing = sbn.isOngoing || !sbn.isClearable,
            category = notification.category,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
        when (decide(posted, current.notificationAllowedPackages, essentialApps::notificationsAlwaysThrough)) {
            FilterDecision.LET_THROUGH -> Unit
            FilterDecision.DISMISS -> service.cancelNotification(sbn.key)
            FilterDecision.HOLD -> {
                service.cancelNotification(sbn.key)
                val held = sbn.toHeld() ?: return
                scope.launch {
                    dao.upsert(held)
                    dao.deleteOlderThan(Instant.now().minus(KEEP_FOR))
                }
            }
        }
    }

    /** Runs what's in the shade through the filter. */
    private fun sweep() {
        val service = service ?: return
        val active = try {
            service.activeNotifications.orEmpty()
        } catch (e: SecurityException) {
            // The listener was disconnected in between; it sweeps again when it reconnects.
            return
        }
        active.forEach { onPosted(service, it) }
    }

    /** A notification with neither title nor text has nothing worth keeping. */
    private fun StatusBarNotification.toHeld(): HeldNotification? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return HeldNotification(key = key, packageName = packageName, title = title, text = text, postedAt = Instant.ofEpochMilli(postTime))
    }

    private fun showCount(count: Int) {
        if (count == 0) {
            notifications.cancel(COUNT_NOTIFICATION_ID)
            return
        }
        val text = if (count > MAX_SHOWN_COUNT) appContext.getString(R.string.held_count_many, MAX_SHOWN_COUNT)
        else appContext.resources.getQuantityString(R.plurals.held_count, count, count)
        val open = Intent(appContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_HELD_NOTIFICATIONS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(PendingIntent.getActivity(appContext, REQUEST_OPEN_HELD, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifications.notify(COUNT_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "held_count"
        const val COUNT_NOTIFICATION_ID = 0x4E1D
        const val REQUEST_OPEN_HELD = 2
        const val MAX_SHOWN_COUNT = 99
        val KEEP_FOR: Duration = Duration.ofDays(7)
    }
}

/** STILL's notification listener; [NotificationFilter] does the work. Only Android can bind it. */
class NotificationFilterService : NotificationListenerService() {

    private val filter: NotificationFilter get() = (application as StillApplication).container.notificationFilter

    override fun onListenerConnected() {
        super.onListenerConnected()
        filter.attach(this)
    }

    override fun onListenerDisconnected() {
        filter.detach(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        filter.onPosted(this, sbn)
    }
}
