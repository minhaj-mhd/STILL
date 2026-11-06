package app.still.launcher.shortvideo

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.Toast
import app.still.launcher.BuildConfig
import app.still.launcher.R
import app.still.launcher.StillApplication
import app.still.launcher.data.HistoryRepository
import app.still.launcher.data.history.InterventionType
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

/**
 * The short-video service. Android sends it events from YouTube, Instagram, Snapchat and the
 * browsers only (see res/xml/short_video_service.xml). In the apps it looks for the Shorts or Reels
 * player by view id and goes back when one fills the screen, or while Snapchat's Spotlight tab is
 * the selected one; in a browser it reads the address bar and opens the site's home page instead of
 * a short-video page. It keeps nothing.
 */
class ShortVideoService : AccessibilityService() {

    private val scope = MainScope()
    private lateinit var blocker: ShortVideoBlocker
    private lateinit var history: HistoryRepository

    /** Looking through another app's views waits on that app, so it's kept off STILL's main thread. */
    private val thread = HandlerThread("short-videos").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCheck = Runnable(::check)
    private var checkPending = false

    /** When STILL last left a short video, on the elapsed-time clock; read and written on [thread]. */
    private val exits = ArrayList<Long>()

    /** What debug builds last noted per app, so the trace only shows changes. */
    private val lastNoted = HashMap<String, String>()

    /** The site each browser's address bar last showed, if it's YouTube or Instagram; kept while the bar is hidden. */
    private val siteInBrowser = HashMap<String, ShortVideoRule>()

    /** When each browser's page was last looked through, on the elapsed-time clock. */
    private val pageLookedAt = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as StillApplication).container
        blocker = container.shortVideos
        history = container.historyRepository
        blocker.attach(this)
        // Only hear from the apps whose switches are on.
        scope.launch {
            blocker.rulesOff.collect { off ->
                serviceInfo = serviceInfo.apply { packageNames = watchedPackages(off, packageName).toTypedArray() }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val app = shortVideoRuleFor(packageName)
        when {
            app != null -> {
                if (!blocker.blocks(app)) return
                // Playing a video changes text and progress many times a second; only views coming and going matter.
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !changesViews(event.contentChangeTypes)) return
            }
            // In a browser the address changing is a text change, so every event counts.
            browserFor(packageName) != null -> if (!blocker.blocksInBrowsers()) return
            else -> return
        }
        handler.post {
            // Events come in bursts; look once a moment after the first, however many follow.
            if (!checkPending) {
                checkPending = true
                handler.postDelayed(pendingCheck, SETTLE_MILLIS)
            }
        }
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
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        if (::blocker.isInitialized) blocker.detach(this)
    }

    private fun check() {
        checkPending = false
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        shortVideoRuleFor(packageName)?.let { rule ->
            if (blocker.blocks(rule)) checkApp(root, packageName, rule)
            return
        }
        browserFor(packageName)?.let { browser -> if (blocker.blocksInBrowsers()) checkBrowser(root, browser) }
    }

    private fun checkApp(root: AccessibilityNodeInfo, packageName: String, rule: ShortVideoRule) {
        if (BuildConfig.DEBUG) {
            // An app whose player isn't known yet has all its view ids noted, to find it. Ids with
            // more than letters, digits and _ in them can carry names people chose, e.g. Snapchat's
            // chat-list filters, so an app spotted by its tab only has the plain ones noted.
            val like = when {
                rule.tabViewIds.isNotEmpty() -> PLAIN_ID
                rule.playerViewIds.isEmpty() -> ANY_ID
                else -> PLAYER_LIKE
            }
            note(
                packageName,
                visibleIds(root, like).map { it.substringAfter(":id/") }.sorted().take(if (like == PLAYER_LIKE) MAX_PROBE_IDS else MAX_NOTED_IDS).toString() +
                    probeShares(root, packageName, rule) + probeTabs(root, rule),
            )
        }
        val showing = showsShortVideo(rule, seenPlayerViews(root, packageName, rule)) ||
            onShortVideoTab(rule, selectedTabs(root, packageName, rule))
        if (!showing) return
        leave(packageName, rule) { performGlobalAction(GLOBAL_ACTION_BACK) }
    }

    /** The full ids of [rule]'s short-video tabs that are on screen and selected. */
    private fun selectedTabs(root: AccessibilityNodeInfo, packageName: String, rule: ShortVideoRule): List<String> =
        listOf(packageName, rule.resourcePackage).distinct().flatMap { pkg ->
            rule.tabViewIds.map { "$pkg:id/$it" }.filter { id ->
                root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser && isSelectedWithin(it, 0) }
            }
        }

    /** A tab marks itself selected on its own view or on one inside it, such as its icon. */
    private fun isSelectedWithin(node: AccessibilityNodeInfo, depth: Int): Boolean =
        node.isSelected || (depth < MAX_TAB_DEPTH && (0 until node.childCount).any { i -> node.getChild(i)?.let { isSelectedWithin(it, depth + 1) } == true })

    /**
     * Debug builds: how each of the app's tabs is built, to find what sets the open one apart when
     * Android isn't told it's selected: its views' kinds, labels, flags and sizes.
     */
    private fun probeTabs(root: AccessibilityNodeInfo, rule: ShortVideoRule): String {
        if (rule.tabViewIds.isEmpty()) return ""
        val tabs = ArrayList<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_LOG_DEPTH || !node.isVisibleToUser) return
            val id = node.viewIdResourceName?.substringAfter(":id/")
            if (id != null && TAB_LIKE.containsMatchIn(id)) {
                tabs += "${id.removePrefix("ngs_").removeSuffix("_icon_container")}{${describeTab(node, 0)}}"
                return
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return " tabs=$tabs"
    }

    private fun describeTab(node: AccessibilityNodeInfo, depth: Int): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        val density = resources.displayMetrics.density
        val parts = buildList {
            add(node.className?.toString()?.substringAfterLast('.') ?: "?")
            if (depth > 0) node.viewIdResourceName?.substringAfter(":id/")?.let { add("id=$it") }
            node.contentDescription?.let { add("desc=$it") }
            node.text?.let { add("text=$it") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription?.let { add("state=$it") }
            if (node.isSelected) add("selected")
            if (node.isCheckable) add(if (node.isChecked) "checked" else "uncheckable")
            if (node.isFocused) add("focused")
            if (!node.isEnabled) add("disabled")
            add("${(bounds.width() / density).toInt()}x${(bounds.height() / density).toInt()}dp")
        }
        val kids = if (depth >= MAX_TAB_DEPTH) emptyList() else (0 until node.childCount).mapNotNull { i ->
            node.getChild(i)?.takeIf { it.isVisibleToUser }?.let { describeTab(it, depth + 1) }
        }
        return parts.joinToString(" ") + if (kids.isEmpty()) "" else kids.joinToString(", ", " [", "]")
    }

    private fun checkBrowser(root: AccessibilityNodeInfo, browser: BrowserRule) {
        val packageName = browser.packageName
        val address = findFirst(root, packageName, browser.addressViewIds)
        // Being typed in: wait until it's gone to.
        if (address?.isFocused == true) return
        val place = address?.text?.toString()?.let(::webPlaceOf)
        if (address != null) {
            place?.let(::shortVideoRuleForSite)?.let { siteInBrowser[packageName] = it } ?: siteInBrowser.remove(packageName)
        }
        if (BuildConfig.DEBUG) noteBrowser(root, browser, address, place)
        // The address, while it's shown.
        if (address != null) {
            place?.let(::shortVideoRuleForPlace)?.takeIf(blocker::blocks)?.let { rule ->
                leave(packageName, rule) { openInstead(address, browser, rule.webHome) || performGlobalAction(GLOBAL_ACTION_BACK) }
                return
            }
        }
        // Otherwise the page itself: the sites move to Shorts and Reels without loading a new page,
        // and the browser hides its address bar as you scroll. Looked through at most every second.
        val site = siteInBrowser[packageName]?.takeIf(blocker::blocks) ?: return
        val now = SystemClock.elapsedRealtime()
        val wait = PAGE_LOOK_MILLIS - (now - (pageLookedAt[packageName] ?: 0L))
        if (wait > 0) {
            checkPending = true
            handler.postDelayed(pendingCheck, wait)
            return
        }
        pageLookedAt[packageName] = now
        if (showsShortVideoPage(site, seenPageElements(root, site))) leave(packageName, site) { performGlobalAction(GLOBAL_ACTION_BACK) }
    }

    /** The page's visible elements whose own ids start like [site]'s player's, with how much of the screen they fill. */
    private fun seenPageElements(root: AccessibilityNodeInfo, site: ShortVideoRule): List<SeenView> {
        val screenHeight = resources.displayMetrics.heightPixels.takeIf { it > 0 } ?: return emptyList()
        val bounds = Rect()
        val seen = ArrayList<SeenView>()
        var looked = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_PAGE_DEPTH || ++looked > MAX_PAGE_NODES || !node.isVisibleToUser) return
            node.viewIdResourceName?.takeIf { id -> site.webPlayerIds.any { id.startsWith(it) } }?.let { id ->
                node.getBoundsInScreen(bounds)
                seen += SeenView(id, bounds.height().toFloat() / screenHeight)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return seen
    }

    /**
     * Leaves a short video in [packageName] with [leavePage], or goes home when that keeps landing
     * on another one. Insights counts each run of exits once.
     */
    private fun leave(packageName: String, rule: ShortVideoRule, leavePage: () -> Boolean) {
        val now = SystemClock.elapsedRealtime()
        exits.removeAll { now - it >= RETRY_WINDOW_MILLIS }
        val exit = shortVideoExit(exits, now)
        val first = exits.isEmpty()
        exits += now
        val left = if (exit == ShortVideoExit.HOME) performGlobalAction(GLOBAL_ACTION_HOME) else leavePage()
        if (BuildConfig.DEBUG) trace("left ${rule.id}: $exit, done=$left")
        if (first) {
            mainHandler.post { Toast.makeText(this, R.string.short_videos_left, Toast.LENGTH_SHORT).show() }
            scope.launch { history.recordIntervention(packageName, InterventionType.SHORT_VIDEO) }
        }
        // Leaving can land on another short video without anything else changing; look again.
        checkPending = true
        handler.postDelayed(pendingCheck, AFTER_EXIT_MILLIS)
    }

    /**
     * Puts [url] in the browser's address bar and goes there, so the tab itself leaves the page:
     * back alone could leave it open for next time. Needs Android 11 to press the keyboard's Go.
     */
    private fun openInstead(address: AccessibilityNodeInfo, browser: BrowserRule, url: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val field = if (address.isEditable) {
            address.takeIf { it.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        } else {
            // The address is only shown here; tapping it opens the field it's typed into.
            if (!address.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false
            SystemClock.sleep(EDIT_OPEN_MILLIS)
            rootInActiveWindow?.let { findFirst(it, browser.packageName, browser.editViewIds) }
        } ?: return false
        val text = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, url) }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text) &&
            field.performAction(AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** The rule's player views on screen, under the app's own package and the one its ids are named in. */
    private fun seenPlayerViews(root: AccessibilityNodeInfo, packageName: String, rule: ShortVideoRule, ids: List<String> = rule.playerViewIds): List<SeenView> {
        val screenHeight = resources.displayMetrics.heightPixels.takeIf { it > 0 } ?: return emptyList()
        val bounds = Rect()
        return listOf(packageName, rule.resourcePackage).distinct().flatMap { pkg ->
            ids.flatMap { id -> root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }
        }.filter { it.isVisibleToUser }.map { node ->
            node.getBoundsInScreen(bounds)
            SeenView(node.viewIdResourceName.orEmpty(), bounds.height().toFloat() / screenHeight)
        }
    }

    private fun findFirst(root: AccessibilityNodeInfo, packageName: String, ids: List<String>): AccessibilityNodeInfo? =
        ids.firstNotNullOfOrNull { id -> root.findAccessibilityNodeInfosByViewId("$packageName:id/$id").firstOrNull() }

    private fun changesViews(types: Int): Boolean =
        types == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED || types and VIEW_CHANGES != 0

    // Debug builds note what they see, to keep the ids up to date when the apps change. Only view
    // ids, and in browsers only the site and first part of the path on YouTube and Instagram.

    private fun noteBrowser(root: AccessibilityNodeInfo, browser: BrowserRule, address: AccessibilityNodeInfo?, place: WebPlace?) {
        val site = siteInBrowser[browser.packageName]
        val shown = when {
            address == null -> "no address bar"
            place != null && isShortVideoSite(place) -> "${place.host}/${place.firstPath}"
            else -> "elsewhere"
        }
        val detail = when {
            site != null -> " ${webProbe(root, site)}"
            address != null -> " shown=${address.isVisibleToUser}"
            else -> " ids=${visibleIds(root, BROWSER_LIKE).sorted()}"
        }
        note(browser.packageName, "$shown$detail")
    }

    /**
     * With the address bar hidden on YouTube or Instagram: the page's own element ids that look
     * like a player, and what the page's root carries (names of extras, and whether its title says
     * Shorts or Reels), to find another way to tell a short video. No text is kept.
     */
    private fun webProbe(root: AccessibilityNodeInfo, site: ShortVideoRule): String {
        val ids = visibleIds(root, WEB_LIKE).sorted().take(MAX_PROBE_IDS)
        val players = seenPageElements(root, site).joinToString(" ", prefix = "players=[", postfix = "]") { "${it.id.take(PROBE_ID_CHARS)}=${"%.2f".format(it.heightShare)}" }
        val page = findByClass(root, WEB_VIEW_CLASS, 0) ?: return "page ids=$ids $players, no web view"
        val extras = page.extras
        val keys = extras.keySet().sorted()
        val places = keys.mapNotNull { key ->
            @Suppress("DEPRECATION")
            val value = extras.get(key) as? CharSequence ?: return@mapNotNull null
            webPlaceOf(value.toString())?.takeIf(::isShortVideoSite)?.let { "$key=${it.host}/${it.firstPath}" }
        }
        val title = listOfNotNull(page.text, page.contentDescription).joinToString(" ")
        return "page ids=$ids $players extras=${keys.size} places=$places titleSaysShort=${SHORT_WORDS.containsMatchIn(title)}"
    }

    private fun findByClass(node: AccessibilityNodeInfo, className: String, depth: Int): AccessibilityNodeInfo? {
        if (node.className == className) return node
        if (depth > MAX_LOG_DEPTH) return null
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findByClass(child, className, depth + 1)?.let { return it } }
        }
        return null
    }

    /** How much of the screen the views that might also be a player fill, to grow the rules. */
    private fun probeShares(root: AccessibilityNodeInfo, packageName: String, rule: ShortVideoRule): String =
        seenPlayerViews(root, packageName, rule, PROBE_IDS).joinToString("") { " ${it.id.substringAfter(":id/")}=${"%.2f".format(it.heightShare)}" }

    private fun visibleIds(root: AccessibilityNodeInfo, like: Regex): Set<String> {
        val ids = HashSet<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_LOG_DEPTH || !node.isVisibleToUser) return
            node.viewIdResourceName?.takeIf { like.containsMatchIn(it) }?.let(ids::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return ids
    }

    private fun note(packageName: String, seen: String) {
        if (lastNoted.put(packageName, seen) != seen) trace("$packageName: $seen")
    }

    /** Logs [line], and keeps it in a small file too, as the system log only reaches back minutes. */
    private fun trace(line: String) {
        Log.d(TAG, line)
        val file = File(filesDir, TRACE_FILE)
        if (file.length() > MAX_TRACE_BYTES) file.delete()
        file.appendText("${LocalTime.now().withNano(0)} $line\n")
    }

    private companion object {
        const val TAG = "StillShortVideos"
        const val SETTLE_MILLIS = 250L

        /** Long enough for leaving to take the app somewhere else before looking again. */
        const val AFTER_EXIT_MILLIS = 800L

        /** How long a browser gets to open its address field after its address bar is tapped. */
        const val EDIT_OPEN_MILLIS = 250L

        const val VIEW_CHANGES = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED
        const val MAX_LOG_DEPTH = 40
        val PLAYER_LIKE = Regex("reel|clips|short", RegexOption.IGNORE_CASE)
        val ANY_ID = Regex(".")
        val TAB_LIKE = Regex("_icon_container$")
        val PLAIN_ID = Regex(":id/\\w+$")
        const val MAX_TAB_DEPTH = 4
        val BROWSER_LIKE = Regex("url|address|toolbar|omnibox|location", RegexOption.IGNORE_CASE)
        val WEB_LIKE = Regex("short|reel|clips|player|video", RegexOption.IGNORE_CASE)
        val SHORT_WORDS = Regex("\\b(shorts|reels?)\\b", RegexOption.IGNORE_CASE)
        val PROBE_IDS = listOf("clips_items_view", "clips_container", "reel_recycler", "favorite")
        const val WEB_VIEW_CLASS = "android.webkit.WebView"
        const val MAX_PROBE_IDS = 40
        const val MAX_NOTED_IDS = 150
        const val PROBE_ID_CHARS = 24

        /** How often a browser's page is looked through for a player while the address bar is hidden. */
        const val PAGE_LOOK_MILLIS = 1_000L
        const val MAX_PAGE_DEPTH = 60
        const val MAX_PAGE_NODES = 4_000
        const val TRACE_FILE = "short-videos-trace.txt"
        const val MAX_TRACE_BYTES = 64 * 1024L
    }
}
