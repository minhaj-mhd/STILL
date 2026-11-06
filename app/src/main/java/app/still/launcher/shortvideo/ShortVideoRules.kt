package app.still.launcher.shortvideo

/**
 * One app's short-video player, spotted by the ids of views only that full-screen player has (or
 * by the app's own short-video tab being the selected one), and the same videos on the web,
 * spotted by their address. These come from the apps' and sites' current versions and can change
 * when they update.
 */
data class ShortVideoRule(
    /** Saved in settings to say the rule is on. */
    val id: String,
    /** The app and builds of it under other names, e.g. patched YouTube. */
    val packages: Set<String>,
    /** The package its view ids are named in, which patched builds keep. */
    val resourcePackage: String,
    /** View ids, without the "package:id/" part, that only the player shows. */
    val playerViewIds: List<String>,
    /** The site showing the same videos; its subdomains count too. */
    val webHosts: Set<String>,
    /** First parts of a web address's path that are short videos, e.g. "shorts" in youtube.com/shorts/…. */
    val webPaths: Set<String>,
    /** Where a browser goes instead. */
    val webHome: String,
    /**
     * Starts of the ids the site gives its short-video player's elements. Chrome passes a page's
     * own element ids on, so the player can be spotted while the address bar is hidden.
     */
    val webPlayerIds: List<String>,
    /**
     * View ids of the app's own tab for short videos, for an app that hides its player's ids:
     * while that tab is the selected one, short videos are showing.
     */
    val tabViewIds: List<String> = emptyList(),
)

val YOUTUBE_SHORTS = ShortVideoRule(
    id = "youtube_shorts",
    packages = setOf("com.google.android.youtube", "app.revanced.android.youtube", "app.rvx.android.youtube"),
    resourcePackage = "com.google.android.youtube",
    playerViewIds = listOf("reel_player_page_container", "reel_watch_player"),
    webHosts = setOf("youtube.com"),
    webPaths = setOf("shorts"),
    webHome = "https://m.youtube.com/",
    // Not yet seen on a phone; debug builds note the page's ids so these can be checked.
    webPlayerIds = listOf("shorts-player", "player-shorts-container"),
)

val INSTAGRAM_REELS = ShortVideoRule(
    id = "instagram_reels",
    packages = setOf("com.instagram.android"),
    resourcePackage = "com.instagram.android",
    playerViewIds = listOf("clips_viewer_view_pager", "clips_viewer_container"),
    webHosts = setOf("instagram.com"),
    webPaths = setOf("reels", "reel"),
    webHome = "https://www.instagram.com/",
    // Each reel's overlay is "clipsoverlay" followed by its numbers.
    webPlayerIds = listOf("clipsoverlay"),
)

val SNAPCHAT_SPOTLIGHT = ShortVideoRule(
    id = "snapchat_spotlight",
    packages = setOf("com.snapchat.android"),
    resourcePackage = "com.snapchat.android",
    // Snapchat hides its player's view ids, but its bottom bar keeps them: Spotlight is showing
    // while its tab is the selected one.
    playerViewIds = emptyList(),
    webHosts = setOf("snapchat.com"),
    webPaths = setOf("spotlight"),
    webHome = "https://www.snapchat.com/",
    webPlayerIds = emptyList(),
    tabViewIds = listOf("ngs_spotlight_icon_container"),
)

val SHORT_VIDEO_RULES = listOf(YOUTUBE_SHORTS, INSTAGRAM_REELS, SNAPCHAT_SPOTLIGHT)

fun shortVideoRuleFor(packageName: String): ShortVideoRule? = SHORT_VIDEO_RULES.firstOrNull { packageName in it.packages }

/** A view the user can see: its full id ("package:id/name") and how much of the screen's height it fills. */
data class SeenView(val id: String, val heightShare: Float)

/**
 * Whether [views] include [rule]'s player filling most of the screen. A small one is a preview in
 * a feed, which is left alone.
 */
fun showsShortVideo(rule: ShortVideoRule, views: Collection<SeenView>): Boolean = views.any {
    it.id.substringAfter(":id/", missingDelimiterValue = "") in rule.playerViewIds && it.heightShare >= MIN_PLAYER_SHARE
}

/** Whether the selected tabs, by full id ("package:id/name"), include [rule]'s short-video tab. */
fun onShortVideoTab(rule: ShortVideoRule, selectedTabIds: Collection<String>): Boolean =
    selectedTabIds.any { it.substringAfter(":id/", missingDelimiterValue = "") in rule.tabViewIds }

/** A web address as far as the rules care: its host and the first part of its path, both lower case. */
data class WebPlace(val host: String, val firstPath: String)

/** Reads [address] as a browser's address bar shows it, with or without "https://". Null if there's no host. */
fun webPlaceOf(address: String): WebPlace? {
    val rest = address.trim().substringAfter("://")
    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase()
    if (host.isEmpty() || ' ' in host || '.' !in host) return null
    val path = rest.drop(rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: rest.length)
    val firstPath = path.removePrefix("/").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
    return WebPlace(host, if (path.startsWith("/")) firstPath else "")
}

/** The rule for the site [place] is on, whatever page. */
fun shortVideoRuleForSite(place: WebPlace): ShortVideoRule? = SHORT_VIDEO_RULES.firstOrNull { rule ->
    rule.webHosts.any { place.host == it || place.host.endsWith(".$it") }
}

/** The rule whose short videos [place] shows, if any. */
fun shortVideoRuleForPlace(place: WebPlace): ShortVideoRule? = shortVideoRuleForSite(place)?.takeIf { place.firstPath in it.webPaths }

/** The rule whose short videos the web address [address] shows, if any. */
fun shortVideoRuleForAddress(address: String): ShortVideoRule? = webPlaceOf(address)?.let(::shortVideoRuleForPlace)

/** Whether [place] is on a site the rules cover, whatever page; debug logs name only those. */
fun isShortVideoSite(place: WebPlace): Boolean = shortVideoRuleForSite(place) != null

/**
 * Whether a page's elements, by their own ids, include [rule]'s web player filling most of the
 * screen. Ids that only start the same way don't count when they're small, e.g. in a feed.
 */
fun showsShortVideoPage(rule: ShortVideoRule, elements: Collection<SeenView>): Boolean = elements.any { element ->
    rule.webPlayerIds.any { element.id.startsWith(it) } && element.heightShare >= MIN_PLAYER_SHARE
}

/** A browser whose address bar STILL reads to spot short-video pages. */
data class BrowserRule(
    val packageName: String,
    /** Ids of the views showing the address, without the "package:id/" part. */
    val addressViewIds: List<String>,
    /** Ids of the field an address is typed into once the bar is tapped; in Chrome it's the same view. */
    val editViewIds: List<String>,
)

val CHROME = BrowserRule(
    packageName = "com.android.chrome",
    addressViewIds = listOf("url_bar"),
    editViewIds = listOf("url_bar"),
)

val FIREFOX = BrowserRule(
    packageName = "org.mozilla.firefox",
    addressViewIds = listOf("mozac_browser_toolbar_url_view"),
    editViewIds = listOf("mozac_browser_toolbar_edit_url_view"),
)

val BROWSERS = listOf(CHROME, FIREFOX)

/** Saved in settings like a rule's id, for whether browsers are watched at all. */
const val BROWSERS_ID = "browsers"

fun browserFor(packageName: String): BrowserRule? = BROWSERS.firstOrNull { it.packageName == packageName }

/**
 * The apps the service needs to hear from, given the switches that are off. Never empty: Android
 * reads an empty list as every app, so with everything off it's only [ownPackage].
 */
fun watchedPackages(rulesOff: Set<String>, ownPackage: String): Set<String> {
    val on = SHORT_VIDEO_RULES.filter { it.id !in rulesOff }
    val browsers = if (on.isNotEmpty() && BROWSERS_ID !in rulesOff) BROWSERS.map { it.packageName } else emptyList()
    return (on.flatMap { it.packages } + browsers).toSet().ifEmpty { setOf(ownPackage) }
}

/** How STILL leaves a short video: leave the page (back in an app), or home when that keeps landing on another. */
enum class ShortVideoExit { LEAVE, HOME }

/**
 * Leaving is enough, unless it's already been tried [LEAVES_BEFORE_HOME] times in the last
 * [RETRY_WINDOW_MILLIS] (some feeds open straight into the next one): then home.
 */
fun shortVideoExit(recentExitsMillis: List<Long>, nowMillis: Long): ShortVideoExit =
    if (recentExitsMillis.count { nowMillis - it < RETRY_WINDOW_MILLIS } >= LEAVES_BEFORE_HOME) ShortVideoExit.HOME else ShortVideoExit.LEAVE

/** The player fills at least this much of the screen's height when it's the short video itself. */
const val MIN_PLAYER_SHARE = 0.5f

const val LEAVES_BEFORE_HOME = 2
const val RETRY_WINDOW_MILLIS = 4_000L
