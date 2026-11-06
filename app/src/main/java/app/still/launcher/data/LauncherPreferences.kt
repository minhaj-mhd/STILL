package app.still.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.still.launcher.focus.FocusSession
import app.still.launcher.limit.AppLimitEntry
import app.still.launcher.limit.AppVisit
import app.still.launcher.limit.LimitSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

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
    /** Swipe down on home to open the notifications; needs Precision mode. */
    val swipeDownNotifications: Boolean = true,
    /** What home's corner buttons open. */
    val phoneButton: CornerChoice = CornerChoice(),
    val cameraButton: CornerChoice = CornerChoice(),
    /** Swiping to the app list puts the cursor in search and brings up the keyboard. */
    val appListKeyboard: Boolean = false,
    /** Apps that have taken a lot of today fade in the app list. */
    val appListFade: Boolean = true,
    /** Precision mode was on; if Android turns it off, home offers to turn it back on. */
    val precisionWanted: Boolean = false,
    /** Apps task gates never close, on top of the essential ones. */
    val gateAlwaysOpenPackages: Set<String> = emptySet(),
    /** The notification filter holds back notifications from apps not in [notificationAllowedPackages]. */
    val notificationFilterOn: Boolean = false,
    val notificationAllowedPackages: Set<String> = emptySet(),
    /** Show how many notifications are held back, as a quiet notification of STILL's own. */
    val notificationCountShown: Boolean = true,
    /** The daily phone limit. */
    val limit: LimitSettings = LimitSettings(),
    /** Folders on home, in the order they were made. */
    val folders: List<AppFolder> = emptyList(),
    val folderStyle: FolderStyle = FolderStyle.STRIP,
    val folderOpening: FolderOpening = FolderOpening.SPOTLIGHT,
    val appDisplay: AppDisplay = AppDisplay.TEXT,
    val accent: AccentColor = AccentColor.TEAL,
    val wallpaper: WallpaperChoice = WallpaperChoice.BLACK,
    /** How much the phone's wallpaper is darkened behind home, 0 to [MAX_WALLPAPER_DIM]. */
    val wallpaperDim: Float = DEFAULT_WALLPAPER_DIM,
    /** Each app's own limits, by package. */
    val appLimits: Map<String, AppLimitEntry> = emptyMap(),
    /** Inner practice: a quality for the day on home, with a mark each time it's practised. */
    val dailyQuality: Boolean = true,
    /** Inner practice: today's unlocks on home, against the usual by this hour. */
    val unlocksOnHome: Boolean = true,
    /** Inner practice: apps whose limits ask what they're opened for do ask; off silences every such pause. */
    val askWhyOn: Boolean = true,
    /** The day the morning quality card was put off, so it doesn't ask again that day. */
    val keynotePutOffOn: LocalDate? = null,
    /** When focus mode started, in epoch milliseconds; null while it's off. */
    val focusModeSince: Long? = null,
    /** Short-video rules (by id) switched off; the rest block while the service is on. */
    val shortVideoRulesOff: Set<String> = emptySet(),
    /** STILL Read: minutes to read a day (0 for no goal), and the duration habit reading fills. */
    val readGoalMinutes: Int = 0,
    val readHabitId: Long? = null,
    /** STILL Read: how EPUBs look, and whether PDFs show light on dark. */
    val readText: ReadText = ReadText(),
    val readPdfNight: Boolean = false,
) {
    /** App limits, the app list's fading and the daily quality's summary all need each app's time today. */
    val needsAppUsage: Boolean get() = appListFade || appLimits.isNotEmpty() || dailyQuality

    companion object {
        const val DEFAULT_LAUNCH_DELAY_SECONDS = 15
        const val DEFAULT_WALLPAPER_DIM = 0.4f
        const val MAX_WALLPAPER_DIM = 0.8f
    }
}

/** How an EPUB's text looks in STILL Read. [scale] 1 is the book's own size. */
data class ReadText(val scale: Float = DEFAULT_SCALE, val font: ReadFont = ReadFont.SERIF, val theme: ReadTheme = ReadTheme.DARK) {
    companion object {
        const val DEFAULT_SCALE = 1f
        const val MIN_SCALE = 0.7f
        const val MAX_SCALE = 2f
        const val SCALE_STEP = 0.1f
    }
}

enum class ReadFont { SERIF, SANS, DYSLEXIC }

enum class ReadTheme { LIGHT, SEPIA, DARK }

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
                swipeDownNotifications = prefs[SWIPE_DOWN_NOTIFICATIONS] ?: true,
                phoneButton = CornerChoice(prefs[PHONE_BUTTON_ON] ?: true, prefs[PHONE_BUTTON_APP]),
                cameraButton = CornerChoice(prefs[CAMERA_BUTTON_ON] ?: true, prefs[CAMERA_BUTTON_APP]),
                appListKeyboard = prefs[APP_LIST_KEYBOARD] ?: false,
                appListFade = prefs[APP_LIST_FADE] ?: true,
                precisionWanted = prefs[PRECISION_WANTED] ?: false,
                gateAlwaysOpenPackages = prefs[GATE_ALWAYS_OPEN_PACKAGES] ?: emptySet(),
                notificationFilterOn = prefs[NOTIFICATION_FILTER_ON] ?: false,
                notificationAllowedPackages = prefs[NOTIFICATION_ALLOWED_PACKAGES] ?: emptySet(),
                notificationCountShown = prefs[NOTIFICATION_COUNT_SHOWN] ?: true,
                limit = prefs[DAILY_LIMIT]?.let(::decodeLimit) ?: LimitSettings(),
                folders = prefs.folders(),
                folderStyle = enumOr(prefs[FOLDER_STYLE], FolderStyle.STRIP),
                folderOpening = enumOr(prefs[FOLDER_OPENING], FolderOpening.SPOTLIGHT),
                appDisplay = enumOr(prefs[APP_DISPLAY], AppDisplay.TEXT),
                accent = enumOr(prefs[ACCENT], AccentColor.TEAL),
                wallpaper = enumOr(prefs[WALLPAPER], WallpaperChoice.BLACK),
                wallpaperDim = prefs[WALLPAPER_DIM] ?: LauncherSettings.DEFAULT_WALLPAPER_DIM,
                appLimits = prefs[APP_LIMITS]?.let(::decodeAppLimits) ?: emptyMap(),
                dailyQuality = prefs[DAILY_QUALITY] ?: true,
                unlocksOnHome = prefs[UNLOCKS_ON_HOME] ?: true,
                askWhyOn = prefs[ASK_WHY_ON] ?: true,
                keynotePutOffOn = prefs[KEYNOTE_PUT_OFF_ON]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                focusModeSince = prefs[FOCUS_MODE_SINCE],
                shortVideoRulesOff = prefs[SHORT_VIDEO_RULES_OFF] ?: emptySet(),
                readGoalMinutes = prefs[READ_GOAL_MINUTES] ?: 0,
                readHabitId = prefs[READ_HABIT_ID],
                readText = ReadText(
                    scale = prefs[READ_TEXT_SCALE] ?: ReadText.DEFAULT_SCALE,
                    font = enumOr(prefs[READ_FONT], ReadFont.SERIF),
                    theme = enumOr(prefs[READ_THEME], ReadTheme.DARK),
                ),
                readPdfNight = prefs[READ_PDF_NIGHT] ?: false,
            )
        }
        // Other keys, such as visits to limited apps, change often; only pass on real changes.
        .distinctUntilChanged()

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

    /** Makes a folder named [name] holding [firstApp], taking it out of any other folder; returns the folder's id. */
    suspend fun createFolder(name: String, firstApp: String): String {
        val id = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            val folders = prefs.folders() + AppFolder(id, name.trim())
            prefs.setFolders(moveToFolder(folders, firstApp, id))
        }
        return id
    }

    /** Moves [appKey] into the folder [folderId], or out of its folder when that's null. */
    suspend fun moveToFolder(appKey: String, folderId: String?) {
        dataStore.edit { prefs -> prefs.setFolders(moveToFolder(prefs.folders(), appKey, folderId)) }
    }

    suspend fun renameFolder(folderId: String, name: String) {
        dataStore.edit { prefs -> prefs.setFolders(prefs.folders().map { if (it.id == folderId) it.copy(name = name.trim()) else it }) }
    }

    /** The folder goes; its apps stay in the app list. */
    suspend fun deleteFolder(folderId: String) {
        dataStore.edit { prefs -> prefs.setFolders(prefs.folders().filterNot { it.id == folderId }) }
    }

    suspend fun setFolderStyle(style: FolderStyle) {
        dataStore.edit { prefs -> prefs[FOLDER_STYLE] = style.name }
    }

    suspend fun setFolderOpening(opening: FolderOpening) {
        dataStore.edit { prefs -> prefs[FOLDER_OPENING] = opening.name }
    }

    suspend fun setAppDisplay(display: AppDisplay) {
        dataStore.edit { prefs -> prefs[APP_DISPLAY] = display.name }
    }

    suspend fun setAccent(accent: AccentColor) {
        dataStore.edit { prefs -> prefs[ACCENT] = accent.name }
    }

    suspend fun setWallpaper(wallpaper: WallpaperChoice) {
        dataStore.edit { prefs -> prefs[WALLPAPER] = wallpaper.name }
    }

    suspend fun setWallpaperDim(dim: Float) {
        dataStore.edit { prefs -> prefs[WALLPAPER_DIM] = dim.coerceIn(0f, LauncherSettings.MAX_WALLPAPER_DIM) }
    }

    private fun Preferences.folders(): List<AppFolder> = this[FOLDERS]?.let { text ->
        try {
            json.decodeFromString(foldersSerializer, text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    } ?: emptyList()

    private fun MutablePreferences.setFolders(folders: List<AppFolder>) {
        this[FOLDERS] = json.encodeToString(foldersSerializer, folders)
    }

    /** A value saved by a newer version that this one doesn't know falls back to [default]. */
    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    /** Changes the daily limit's settings in one step, so rules that compare old and new see the saved values. */
    suspend fun updateLimit(change: (LimitSettings) -> LimitSettings) {
        dataStore.edit { prefs ->
            val current = prefs[DAILY_LIMIT]?.let(::decodeLimit) ?: LimitSettings()
            prefs[DAILY_LIMIT] = json.encodeToString(LimitSettings.serializer(), change(current))
        }
    }

    /** Changes one app's limits in one step, so the rules compare against what's saved; null removes them. */
    suspend fun updateAppLimit(packageName: String, change: (AppLimitEntry?) -> AppLimitEntry?) {
        dataStore.edit { prefs ->
            val all = prefs[APP_LIMITS]?.let(::decodeAppLimits) ?: emptyMap()
            val updated = change(all[packageName])
            prefs[APP_LIMITS] = json.encodeToString(appLimitsSerializer, if (updated == null) all - packageName else all + (packageName to updated))
        }
    }

    /** Visits to limited apps, kept so the wait between visits survives STILL restarting. */
    suspend fun appVisits(): Map<String, AppVisit> = dataStore.data.first()[APP_VISITS]?.let(::decodeVisits) ?: emptyMap()

    suspend fun setAppVisits(visits: Map<String, AppVisit>) {
        dataStore.edit { prefs -> prefs[APP_VISITS] = json.encodeToString(visitsSerializer, visits) }
    }

    suspend fun setDailyQuality(on: Boolean) {
        dataStore.edit { prefs -> prefs[DAILY_QUALITY] = on }
    }

    suspend fun setUnlocksOnHome(on: Boolean) {
        dataStore.edit { prefs -> prefs[UNLOCKS_ON_HOME] = on }
    }

    suspend fun setAskWhyOn(on: Boolean) {
        dataStore.edit { prefs -> prefs[ASK_WHY_ON] = on }
    }

    /** Starts focus mode at [millis], or ends it with null. */
    suspend fun setReadGoal(minutes: Int) {
        dataStore.edit { prefs -> prefs[READ_GOAL_MINUTES] = minutes.coerceAtLeast(0) }
    }

    suspend fun setReadHabit(habitId: Long?) {
        dataStore.edit { prefs -> if (habitId == null) prefs.remove(READ_HABIT_ID) else prefs[READ_HABIT_ID] = habitId }
    }

    suspend fun setReadText(text: ReadText) {
        dataStore.edit { prefs ->
            prefs[READ_TEXT_SCALE] = text.scale
            prefs[READ_FONT] = text.font.name
            prefs[READ_THEME] = text.theme.name
        }
    }

    suspend fun setReadPdfNight(on: Boolean) {
        dataStore.edit { prefs -> prefs[READ_PDF_NIGHT] = on }
    }

    suspend fun setFocusModeSince(millis: Long?) {
        dataStore.edit { prefs -> if (millis == null) prefs.remove(FOCUS_MODE_SINCE) else prefs[FOCUS_MODE_SINCE] = millis }
    }

    suspend fun setKeynotePutOff(day: LocalDate) {
        dataStore.edit { prefs -> prefs[KEYNOTE_PUT_OFF_ON] = day.toString() }
    }

    /** Limits saved by a different app version that can't be read fall back to none. */
    private fun decodeAppLimits(text: String): Map<String, AppLimitEntry>? = try {
        json.decodeFromString(appLimitsSerializer, text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun decodeVisits(text: String): Map<String, AppVisit>? = try {
        json.decodeFromString(visitsSerializer, text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** The last limit notice shown, e.g. "2026-10-23:WARN", so it isn't shown twice. */
    suspend fun limitNotice(): String? = dataStore.data.first()[LIMIT_NOTICE]

    suspend fun setLimitNotice(notice: String) {
        dataStore.edit { prefs -> prefs[LIMIT_NOTICE] = notice }
    }

    /** Settings saved by a different app version that can't be read fall back to no limit. */
    private fun decodeLimit(text: String): LimitSettings? = try {
        json.decodeFromString(LimitSettings.serializer(), text).let { limit ->
            // The first build with a limit didn't record the day it started; a raise waiting
            // since then was made on that day, the only day it could have been.
            if (limit.startedOn == null && limit.plan.isOn && limit.pending != null) limit.copy(startedOn = limit.pending.from.minusDays(1)) else limit
        }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
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

    /**
     * Puts the favourites in [order]. Favourites it leaves out, such as hidden apps that home
     * doesn't show, keep their place at the end.
     */
    suspend fun setFavoriteOrder(order: List<String>) {
        dataStore.edit { prefs ->
            val current = prefs.favorites()
            val ordered = order.filter { it in current }
            prefs[FAVORITES] = (ordered + (current - ordered.toSet())).joinToString(SEPARATOR)
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

    suspend fun setAppListKeyboard(on: Boolean) {
        dataStore.edit { prefs -> prefs[APP_LIST_KEYBOARD] = on }
    }

    suspend fun setAppListFade(on: Boolean) {
        dataStore.edit { prefs -> prefs[APP_LIST_FADE] = on }
    }

    suspend fun setSwipeDownNotifications(on: Boolean) {
        dataStore.edit { prefs -> prefs[SWIPE_DOWN_NOTIFICATIONS] = on }
    }

    suspend fun setCornerButton(button: CornerButton, choice: CornerChoice) {
        val (onKey, appKey) = when (button) {
            CornerButton.PHONE -> PHONE_BUTTON_ON to PHONE_BUTTON_APP
            CornerButton.CAMERA -> CAMERA_BUTTON_ON to CAMERA_BUTTON_APP
        }
        dataStore.edit { prefs ->
            prefs[onKey] = choice.on
            if (choice.appKey == null) prefs.remove(appKey) else prefs[appKey] = choice.appKey
        }
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

    suspend fun setShortVideoRuleOn(id: String, on: Boolean) {
        dataStore.edit { prefs -> prefs[SHORT_VIDEO_RULES_OFF] = (prefs[SHORT_VIDEO_RULES_OFF] ?: emptySet()).with(id, !on) }
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
        val SWIPE_DOWN_NOTIFICATIONS = booleanPreferencesKey("swipeDownNotifications")
        val PHONE_BUTTON_ON = booleanPreferencesKey("phoneButtonOn")
        val PHONE_BUTTON_APP = stringPreferencesKey("phoneButtonApp")
        val CAMERA_BUTTON_ON = booleanPreferencesKey("cameraButtonOn")
        val CAMERA_BUTTON_APP = stringPreferencesKey("cameraButtonApp")
        val APP_LIST_KEYBOARD = booleanPreferencesKey("appListKeyboard")
        val APP_LIST_FADE = booleanPreferencesKey("appListFade")
        val PRECISION_WANTED = booleanPreferencesKey("precisionWanted")
        val GATE_ALWAYS_OPEN_PACKAGES = stringSetPreferencesKey("gateAlwaysOpenPackages")
        val FOCUS_SESSION = stringPreferencesKey("focusSession")
        val DAILY_LIMIT = stringPreferencesKey("dailyLimit")
        val LIMIT_NOTICE = stringPreferencesKey("limitNotice")
        val FOLDERS = stringPreferencesKey("folders")
        val FOLDER_STYLE = stringPreferencesKey("folderStyle")
        val FOLDER_OPENING = stringPreferencesKey("folderOpening")
        val APP_DISPLAY = stringPreferencesKey("appDisplay")
        val ACCENT = stringPreferencesKey("accent")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        val WALLPAPER_DIM = floatPreferencesKey("wallpaperDim")
        val APP_LIMITS = stringPreferencesKey("appLimits")
        val APP_VISITS = stringPreferencesKey("appVisits")
        val DAILY_QUALITY = booleanPreferencesKey("dailyQuality")
        val UNLOCKS_ON_HOME = booleanPreferencesKey("unlocksOnHome")
        val ASK_WHY_ON = booleanPreferencesKey("askWhyOn")
        val KEYNOTE_PUT_OFF_ON = stringPreferencesKey("keynotePutOffOn")
        val FOCUS_MODE_SINCE = longPreferencesKey("focusModeSince")
        val SHORT_VIDEO_RULES_OFF = stringSetPreferencesKey("shortVideoRulesOff")
        val READ_GOAL_MINUTES = intPreferencesKey("readGoalMinutes")
        val READ_HABIT_ID = longPreferencesKey("readHabitId")
        val READ_TEXT_SCALE = floatPreferencesKey("readTextScale")
        val READ_FONT = stringPreferencesKey("readFont")
        val READ_THEME = stringPreferencesKey("readTheme")
        val READ_PDF_NIGHT = booleanPreferencesKey("readPdfNight")
        val appLimitsSerializer: KSerializer<Map<String, AppLimitEntry>> = MapSerializer(String.serializer(), AppLimitEntry.serializer())
        val visitsSerializer: KSerializer<Map<String, AppVisit>> = MapSerializer(String.serializer(), AppVisit.serializer())
        // Typed explicitly, as for Room's converters.
        val foldersSerializer: KSerializer<List<AppFolder>> = ListSerializer(AppFolder.serializer())
        val NOTIFICATION_FILTER_ON = booleanPreferencesKey("notificationFilterOn")
        val NOTIFICATION_ALLOWED_PACKAGES = stringSetPreferencesKey("notificationAllowedPackages")
        val NOTIFICATION_COUNT_SHOWN = booleanPreferencesKey("notificationCountShown")
        val json = Json { ignoreUnknownKeys = true }
        const val RENAME_PREFIX = "rename:"
        const val SEPARATOR = "\n"
    }
}
