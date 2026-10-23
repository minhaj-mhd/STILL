package app.still.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User choices about apps, keyed by [InstalledApp.key]. */
data class LauncherSettings(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
)

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
            )
        }

    /** Favorites keep the order they were added in; re-adding moves an app to the end. */
    suspend fun setFavorite(appKey: String, favorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs.favorites() - appKey
            prefs[FAVORITES] = (if (favorite) current + appKey else current).joinToString(SEPARATOR)
        }
    }

    suspend fun setHidden(appKey: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[HIDDEN] ?: emptySet()
            prefs[HIDDEN] = if (hidden) current + appKey else current - appKey
        }
    }

    /** A null or blank [label] restores the app's own name. */
    suspend fun setRename(appKey: String, label: String?) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey(RENAME_PREFIX + appKey)
            if (label.isNullOrBlank()) prefs.remove(key) else prefs[key] = label.trim()
        }
    }

    private fun Preferences.favorites(): List<String> =
        this[FAVORITES]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden")
        const val RENAME_PREFIX = "rename:"
        const val SEPARATOR = "\n"
    }
}
