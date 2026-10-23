package app.still.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.still.launcher.focus.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** User choices about apps. Favorites, hidden apps and renames are keyed by [InstalledApp.key]. */
data class LauncherSettings(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    /** Packages that wait [launchDelaySeconds] before opening from STILL. */
    val launchDelayPackages: Set<String> = emptySet(),
    /** Packages that ask how long you'll use them, then remind you when the time is up. */
    val timeReminderPackages: Set<String> = emptySet(),
    val launchDelaySeconds: Int = DEFAULT_LAUNCH_DELAY_SECONDS,
    /** When a time reminder runs out: 0 only reminds, otherwise the app is blocked this many minutes. */
    val timeUpBlockMinutes: Int = 0,
    /** Double-tap empty space on home to lock the screen; needs Precision mode. */
    val doubleTapToLock: Boolean = true,
    /** Precision mode was on; if Android turns it off, home offers to turn it back on. */
    val precisionWanted: Boolean = false,
    /** Apps task gates never close, on top of the essential ones. */
    val gateAlwaysOpenPackages: Set<String> = emptySet(),
    /** The notification filter holds back notifications from apps not in [notificationAllowedPackages]. */
    val notificationFilterOn: Boolean = false,
    val notificationAllowedPackages: Set<String> = emptySet(),
    /** Show how many notifications are held back, as a quiet notification of STILL's own. */
    val notificationCountShown: Boolean = true,
) {
    companion object {
        const val DEFAULT_LAUNCH_DELAY_SECONDS = 15
    }
}

class LauncherPreferences(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<LauncherSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            LauncherSettings(
                favorites = prefs.favorites(),
                hidden = prefs[HIDDEN] ?: emptySet(),
                renames = prefs.asMap().entries
                    .filter { it.key.name.startsWith(RENAME_PREFIX) }
                    .associate { it.key.name.removePrefix(RENAME_PREFIX) to it.value as String },
                launchDelayPackages = prefs[LAUNCH_DELAY_PACKAGES] ?: emptySet(),
                timeReminderPackages = prefs[TIME_REMINDER_PACKAGES] ?: emptySet(),
                launchDelaySeconds = prefs[LAUNCH_DELAY_SECONDS] ?: LauncherSettings.DEFAULT_LAUNCH_DELAY_SECONDS,
                timeUpBlockMinutes = prefs[TIME_UP_BLOCK_MINUTES] ?: 0,
                doubleTapToLock = prefs[DOUBLE_TAP_TO_LOCK] ?: true,
                precisionWanted = prefs[PRECISION_WANTED] ?: false,
                gateAlwaysOpenPackages = prefs[GATE_ALWAYS_OPEN_PACKAGES] ?: emptySet(),
                notificationFilterOn = prefs[NOTIFICATION_FILTER_ON] ?: false,
                notificationAllowedPackages = prefs[NOTIFICATION_ALLOWED_PACKAGES] ?: emptySet(),
                notificationCountShown = prefs[NOTIFICATION_COUNT_SHOWN] ?: true,
            )
        }

    /** The focus timer that's running, if any; kept apart from [settings] as it changes on its own. */
    val focusSession: Flow<FocusSession?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[FOCUS_SESSION]?.let(::decodeFocusSession) }

    suspend fun currentFocusSession(): FocusSession? = focusSession.first()

    suspend fun setFocusSession(session: FocusSession?) {
        dataStore.edit { prefs ->
            if (session == null) prefs.remove(FOCUS_SESSION) else prefs[FOCUS_SESSION] = json.encodeToString(FocusSession.serializer(), session)
        }
    }

    /** A session saved by a different app version that can't be read is treated as none. */
    private fun decodeFocusSession(text: String): FocusSession? = try {
        json.decodeFromString(FocusSession.serializer(), text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** The current settings, read once; for code outside the UI such as alarm receivers. */
    suspend fun current(): LauncherSettings = settings.first()

    /** Favorites keep the order they were added in; re-adding moves an app to the end. */
    suspend fun setFavorite(appKey: String, favorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs.favorites() - appKey
            prefs[FAVORITES] = (if (favorite) current + appKey else current).joinToString(SEPARATOR)
        }
    }

    suspend fun setHidden(appKey: String, hidden: Boolean) {
        dataStore.edit { prefs -> prefs[HIDDEN] = (prefs[HIDDEN] ?: emptySet()).with(appKey, hidden) }
    }

    /** A null or blank [label] restores the app's own name. */
    suspend fun setRename(appKey: String, label: String?) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey(RENAME_PREFIX + appKey)
            if (label.isNullOrBlank()) prefs.remove(key) else prefs[key] = label.trim()
        }
    }

    suspend fun setLaunchDelay(packageName: String, on: Boolean) {
        dataStore.edit { prefs -> prefs[LAUNCH_DELAY_PACKAGES] = (prefs[LAUNCH_DELAY_PACKAGES] ?: emptySet()).with(packageName, on) }
    }

    suspend fun setTimeReminder(packageName: String, on: Boolean) {
        dataStore.edit { prefs -> prefs[TIME_REMINDER_PACKAGES] = (prefs[TIME_REMINDER_PACKAGES] ?: emptySet()).with(packageName, on) }
    }

    suspend fun setLaunchDelaySeconds(seconds: Int) {
        dataStore.edit { prefs -> prefs[LAUNCH_DELAY_SECONDS] = seconds }
    }

    suspend fun setTimeUpBlockMinutes(minutes: Int) {
        dataStore.edit { prefs -> prefs[TIME_UP_BLOCK_MINUTES] = minutes }
    }

    suspend fun setDoubleTapToLock(on: Boolean) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_TO_LOCK] = on }
    }

    suspend fun setPrecisionWanted(wanted: Boolean) {
        dataStore.edit { prefs -> prefs[PRECISION_WANTED] = wanted }
    }

    /** Turning the filter on for the first time lets [firstAllowed] through to begin with. */
    suspend fun setNotificationFilterOn(on: Boolean, firstAllowed: Set<String> = emptySet()) {
        dataStore.edit { prefs ->
            if (on && prefs[NOTIFICATION_ALLOWED_PACKAGES] == null) prefs[NOTIFICATION_ALLOWED_PACKAGES] = firstAllowed
            prefs[NOTIFICATION_FILTER_ON] = on
        }
    }

    suspend fun setNotificationAllowed(packages: Set<String>) {
        dataStore.edit { prefs -> prefs[NOTIFICATION_ALLOWED_PACKAGES] = packages }
    }

    suspend fun setNotificationCountShown(shown: Boolean) {
        dataStore.edit { prefs -> prefs[NOTIFICATION_COUNT_SHOWN] = shown }
    }

    suspend fun setGateAlwaysOpen(packages: Set<String>) {
        dataStore.edit { prefs -> prefs[GATE_ALWAYS_OPEN_PACKAGES] = packages }
    }

    private fun Preferences.favorites(): List<String> =
        this[FAVORITES]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    private fun Set<String>.with(value: String, included: Boolean): Set<String> = if (included) this + value else this - value

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val LAUNCH_DELAY_PACKAGES = stringSetPreferencesKey("launchDelayPackages")
        val TIME_REMINDER_PACKAGES = stringSetPreferencesKey("timeReminderPackages")
        val LAUNCH_DELAY_SECONDS = intPreferencesKey("launchDelaySeconds")
        val TIME_UP_BLOCK_MINUTES = intPreferencesKey("timeUpBlockMinutes")
        val DOUBLE_TAP_TO_LOCK = booleanPreferencesKey("doubleTapToLock")
        val PRECISION_WANTED = booleanPreferencesKey("precisionWanted")
        val GATE_ALWAYS_OPEN_PACKAGES = stringSetPreferencesKey("gateAlwaysOpenPackages")
        val FOCUS_SESSION = stringPreferencesKey("focusSession")
        val NOTIFICATION_FILTER_ON = booleanPreferencesKey("notificationFilterOn")
        val NOTIFICATION_ALLOWED_PACKAGES = stringSetPreferencesKey("notificationAllowedPackages")
        val NOTIFICATION_COUNT_SHOWN = booleanPreferencesKey("notificationCountShown")
        val json = Json { ignoreUnknownKeys = true }
        const val RENAME_PREFIX = "rename:"
        const val SEPARATOR = "\n"
    }
}
