package app.still.launcher.blocking

/**
 * One usage event, reduced to what screen-time counting needs. [className] is the activity, which
 * tells a browser's tab from the browser (see [TabOwners]).
 */
data class AppUsageEvent(val packageName: String, val timeMillis: Long, val kind: Kind, val className: String? = null) {
    enum class Kind {
        /** An activity of the app came to the front. */
        FOREGROUND,

        /** An activity of the app left the front. */
        BACKGROUND,

        /** The screen went off or the device shut down; nothing is in front any more. */
        SCREEN_OFF,

        /** The phone was unlocked. Nothing changes in front; only history counts these. */
        UNLOCK,
    }
}

/**
 * The app in front after these events, or null when the screen went off (or nothing came to
 * the front) after the last one. Leaving an app only clears it if nothing else has come to the
 * front since, which is how Android orders them when switching apps.
 */
fun foregroundAppAfter(events: List<AppUsageEvent>): String? {
    var current: String? = null
    for (event in events.sortedBy { it.timeMillis }) {
        when (event.kind) {
            AppUsageEvent.Kind.FOREGROUND -> current = event.packageName
            AppUsageEvent.Kind.BACKGROUND -> if (current == event.packageName) current = null
            AppUsageEvent.Kind.SCREEN_OFF -> current = null
            AppUsageEvent.Kind.UNLOCK -> Unit
        }
    }
    return current
}

/**
 * Milliseconds each app spent in front between [fromMillis] and [toMillis].
 *
 * An app counts from coming to the front until it leaves, the screen goes off, or [toMillis].
 * An app whose first event in the window is leaving the front was already open when the
 * window started, so it counts from [fromMillis]. Several apps can be in front at once
 * (split screen); each counts on its own.
 */
fun foregroundTimeByApp(events: List<AppUsageEvent>, fromMillis: Long, toMillis: Long): Map<String, Long> {
    val totals = mutableMapOf<String, Long>()
    for (span in foregroundSpans(events, fromMillis, toMillis)) totals[span.packageName] = (totals[span.packageName] ?: 0L) + span.millis
    return totals
}

/** A stretch of time one app was in front, within the window it was read for. */
data class ForegroundSpan(val packageName: String, val startMillis: Long, val endMillis: Long) {
    val millis: Long get() = endMillis - startMillis
}

/** The stretches behind [foregroundTimeByApp], in the order they ended, cut to the window. */
fun foregroundSpans(events: List<AppUsageEvent>, fromMillis: Long, toMillis: Long): List<ForegroundSpan> {
    val spans = mutableListOf<ForegroundSpan>()
    val inFront = mutableMapOf<String, Long>()
    val seen = mutableSetOf<String>()

    fun add(packageName: String, start: Long, end: Long) {
        val from = start.coerceAtLeast(fromMillis)
        val to = end.coerceAtMost(toMillis)
        if (to > from) spans += ForegroundSpan(packageName, from, to)
    }

    for (event in events.sortedBy { it.timeMillis }) {
        if (event.timeMillis > toMillis) break
        when (event.kind) {
            AppUsageEvent.Kind.FOREGROUND -> {
                seen += event.packageName
                inFront.putIfAbsent(event.packageName, event.timeMillis)
            }
            AppUsageEvent.Kind.BACKGROUND -> {
                val start = inFront.remove(event.packageName)
                    ?: fromMillis.takeIf { event.packageName !in seen } // open before the window began
                if (start != null) add(event.packageName, start, event.timeMillis)
                seen += event.packageName
            }
            AppUsageEvent.Kind.SCREEN_OFF -> {
                inFront.forEach { (packageName, start) -> add(packageName, start, event.timeMillis) }
                inFront.clear()
            }
            AppUsageEvent.Kind.UNLOCK -> Unit
        }
    }
    inFront.forEach { (packageName, start) -> add(packageName, start, toMillis) }
    return spans
}

/**
 * How many times each app was opened between [fromMillis] and [toMillis]: came to the front after
 * another app, or after the screen was off. Moving between one app's own screens isn't opening it.
 */
fun opensByApp(events: List<AppUsageEvent>, fromMillis: Long, toMillis: Long): Map<String, Int> {
    val opens = mutableMapOf<String, Int>()
    var last: String? = null
    for (event in events.sortedBy { it.timeMillis }) {
        if (event.timeMillis > toMillis) break
        when (event.kind) {
            AppUsageEvent.Kind.FOREGROUND -> {
                if (event.packageName != last && event.timeMillis >= fromMillis) opens[event.packageName] = (opens[event.packageName] ?: 0) + 1
                last = event.packageName
            }
            AppUsageEvent.Kind.SCREEN_OFF -> last = null
            AppUsageEvent.Kind.BACKGROUND, AppUsageEvent.Kind.UNLOCK -> Unit
        }
    }
    return opens
}
