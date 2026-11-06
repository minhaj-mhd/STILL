package app.still.launcher.blocking

/** STILL opened [packageName] at [timeMillis]. */
data class AppOpening(val packageName: String, val timeMillis: Long)

/**
 * Which app a browser's tab counts as.
 *
 * Some apps show their pages in a browser's window inside their own task: a link opened within
 * an app (a Custom Tab), and apps that are web apps underneath, like Chrome Remote Desktop.
 * Android names the browser as the app in front then, so an allowed web app would close with the
 * browser, and its time would count as the browser's. Here a tab counts as the app that opened
 * it: the app in front just before it, or the app STILL just opened. Coming back to a tab from
 * Recents shows nothing of its app, so each browser's last owner is kept for that.
 *
 * [canOpenTabs] leaves out STILL and Android's own screens, Recents among them.
 */
class TabOwners(private val canOpenTabs: (String) -> Boolean) {

    /** The app each browser's tabs count as, by the browser's package. */
    private val owners = HashMap<String, String>()

    /** The app a tab coming to the front now would belong to. */
    private var opener: String? = null

    /** The apps STILL opened lately, oldest first, to read usage events with afterwards. */
    private val openings = ArrayDeque<AppOpening>()

    /** The app [packageName]'s activity [className] counts as, as it comes to the front. */
    @Synchronized
    fun onFront(packageName: String, className: String?): String {
        if (!isBrowserTab(className)) {
            opener = packageName.takeIf(canOpenTabs)
            return packageName
        }
        opener?.let { owners[packageName] = it }
        return ownerOf(packageName, className)
    }

    /** The app [packageName]'s activity [className] counts as now. */
    @Synchronized
    fun ownerOf(packageName: String, className: String?): String =
        if (isBrowserTab(className)) owners[packageName] ?: packageName else packageName

    /** STILL is opening [packageName] at [nowMillis], so a tab coming to the front next is its. */
    @Synchronized
    fun opening(packageName: String, nowMillis: Long) {
        opener = packageName
        openings.addLast(AppOpening(packageName, nowMillis))
        while (openings.size > MAX_OPENINGS || openings.first().timeMillis < nowMillis - KEEP_OPENINGS_MILLIS) openings.removeFirst()
    }

    /**
     * [events], oldest first, with each browser tab's events given to the app the tab counts as.
     * Reading starts from the owners known now and follows the events, and STILL's openings, from there.
     */
    fun attribute(events: List<AppUsageEvent>): List<AppUsageEvent> {
        val reader = TabOwners(canOpenTabs)
        val opened: List<AppOpening>
        synchronized(this) {
            reader.owners += owners
            opened = openings.toList()
        }
        var next = 0
        return events.map { event ->
            while (next < opened.size && opened[next].timeMillis <= event.timeMillis) reader.opener = opened[next++].packageName
            when (event.kind) {
                AppUsageEvent.Kind.FOREGROUND -> event.copy(packageName = reader.onFront(event.packageName, event.className))
                AppUsageEvent.Kind.BACKGROUND -> event.copy(packageName = reader.ownerOf(event.packageName, event.className))
                AppUsageEvent.Kind.SCREEN_OFF, AppUsageEvent.Kind.UNLOCK -> event
            }
        }
    }

    companion object {
        /** Screen time reads a week back at most. */
        private const val KEEP_OPENINGS_MILLIS = 8 * 24 * 60 * 60_000L
        private const val MAX_OPENINGS = 2_000

        /**
         * A browser's window for another app's pages: Chrome's (and the browsers built from it)
         * for Custom Tabs, web apps like Chrome Remote Desktop and installed web apps, and Firefox's.
         * Also each browser's doorway, which shows nothing and hands a link or web app on: it opens
         * in the task of whatever app sent it, so it counts as that app too. The browser's own
         * window comes after it when the link opens in the browser. Full names, so another app's
         * activity that happens to share a short name isn't one.
         */
        private val BROWSER_TABS = Regex(
            """org\.chromium\.chrome\.browser\.(customtabs\.(Translucent)?CustomTabActivity|webapps\.(Webapp|WebApk|SameTaskWebApk)Activity\d*""" +
                """|document\.ChromeLauncherActivity)""" +
                """|org\.mozilla\.fenix\.(customtabs\.ExternalAppBrowserActivity|IntentReceiverActivity)""",
        )

        fun isBrowserTab(className: String?): Boolean = className != null && BROWSER_TABS.matches(className)
    }
}
