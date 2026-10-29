package app.still.launcher.shortvideo

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import app.still.launcher.R
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.util.accessibilitySettingsIntent
import app.still.launcher.util.isAccessibilityServiceSwitchedOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Short-video blocking: an optional Accessibility service of its own, apart from Precision mode so
 * that one never reads the screen. Android only sends it events from YouTube, Instagram and the
 * browsers, and it leaves Shorts and Reels as soon as they open. The service and the rest of STILL
 * meet here.
 */
class ShortVideoBlocker(context: Context, private val preferences: LauncherPreferences, scope: CoroutineScope) {
    private val appContext = context.applicationContext

    private val _running = MutableStateFlow(false)

    /** Whether the service is connected. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    private var service: ShortVideoService? = null

    /** Rules switched off in Settings, by id. */
    val rulesOff: StateFlow<Set<String>> = preferences.settings
        .map { it.shortVideoRulesOff }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun blocks(rule: ShortVideoRule): Boolean = rule.id !in rulesOff.value

    suspend fun setBlocks(rule: ShortVideoRule, on: Boolean) = preferences.setShortVideoRuleOn(rule.id, on)

    /** Whether Shorts and Reels pages are left in Chrome and Firefox too. */
    fun blocksInBrowsers(): Boolean = BROWSERS_ID !in rulesOff.value

    suspend fun setBlocksInBrowsers(on: Boolean) = preferences.setShortVideoRuleOn(BROWSERS_ID, on)

    internal fun attach(service: ShortVideoService) {
        this.service = service
        _running.value = true
    }

    internal fun detach(service: ShortVideoService) {
        if (this.service !== service) return
        this.service = null
        _running.value = false
    }

    /** Whether Android's settings have the service switched on, running or not. */
    fun isSwitchedOn(): Boolean = isAccessibilityServiceSwitchedOn(appContext, ShortVideoService::class.java)

    /** Turns the service off until the user turns it on again in Android's settings. */
    fun turnOff() {
        service?.disableSelf()
    }

    fun settingsIntent(): Intent = accessibilitySettingsIntent(appContext, ShortVideoService::class.java)
}

/** What a rule blocks, as Settings names it. */
@get:StringRes
val ShortVideoRule.nameRes: Int
    get() = when (this) {
        YOUTUBE_SHORTS -> R.string.short_videos_youtube
        INSTAGRAM_REELS -> R.string.short_videos_instagram
        else -> error("No name for $id")
    }
