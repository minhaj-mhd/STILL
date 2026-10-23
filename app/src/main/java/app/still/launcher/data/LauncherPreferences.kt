package app.still.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
            )
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
        const val RENAME_PREFIX = "rename:"
        const val SEPARATOR = "\n"
    }
}
