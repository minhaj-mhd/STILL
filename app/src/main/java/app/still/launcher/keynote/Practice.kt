package app.still.launcher.keynote

/** What a practice mark was for. */
enum class MomentKind {
    /** Turned back at a pause or a launch delay instead of opening the app. */
    TURNED_BACK,

    /** Finished the Today item the quality is practised with. */
    PRACTISED,

    /** Finished a focus session. */
    FOCUSED,

    /** An app with a daily budget stayed within it; counted from the evening. */
    WITHIN_LIMIT,
}

/**
 * One practice mark: what it was for, when (null for one about the whole day), and its subject,
 * an app's package or an item's title.
 */
data class Moment(val kind: MomentKind, val atMillis: Long?, val subject: String, val minutes: Int = 0)

/** Something done today, from the Today page's records. */
data class Completion(val title: String, val atMillis: Long, val minutes: Int = 0)

/**
 * Today's practice marks in time order, whole-day ones last. Turning back from the same app again
 * within [MERGE_MILLIS] of the last try counts once, so a burst of tries isn't a burst of marks.
 * [turnedBack] pairs a package with when, [withinLimits] a package with its daily minutes.
 */
fun practiceMoments(
    turnedBack: List<Pair<String, Long>>,
    practised: Completion?,
    focused: List<Completion>,
    withinLimits: List<Pair<String, Int>>,
): List<Moment> {
    val lastTry = mutableMapOf<String, Long>()
    val backs = turnedBack.sortedBy { it.second }.mapNotNull { (packageName, at) ->
        val previous = lastTry.put(packageName, at)
        if (previous != null && at - previous < MERGE_MILLIS) null else Moment(MomentKind.TURNED_BACK, at, packageName)
    }
    val timed = backs +
        listOfNotNull(practised?.let { Moment(MomentKind.PRACTISED, it.atMillis, it.title) }) +
        focused.map { Moment(MomentKind.FOCUSED, it.atMillis, it.title, it.minutes) }
    return timed.sortedBy { it.atMillis } + withinLimits.map { (packageName, minutes) -> Moment(MomentKind.WITHIN_LIMIT, null, packageName, minutes) }
}

/** How today's unlocks compare with the usual by the same time. */
enum class UnlockTrend { FEWER, ABOUT, MORE }

/** Today's unlocks so far, and how that compares; no [trend] without enough earlier days. */
data class UnlockObservation(val count: Int, val trend: UnlockTrend?)

/** Each earlier day's unlocks by [offsetMillis] into the day, from [days]' unlock times as offsets from each day's start. */
fun unlocksByNow(days: List<List<Long>>, offsetMillis: Long): List<Int> = days.map { times -> times.count { it <= offsetMillis } }

/**
 * Today's [count] against [pastByNow], the counts on earlier days by the same time of day: fewer
 * or more when clearly off the usual (their median), about the same otherwise. No trend with
 * fewer than [MIN_BASELINE_DAYS] days to go on.
 */
fun unlockObservation(count: Int, pastByNow: List<Int>): UnlockObservation {
    if (pastByNow.size < MIN_BASELINE_DAYS) return UnlockObservation(count, null)
    val sorted = pastByNow.sorted()
    val middle = sorted.size / 2
    val usual = if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    val margin = maxOf(MIN_MARGIN, usual * MARGIN_SHARE)
    val trend = when {
        count < usual - margin -> UnlockTrend.FEWER
        count > usual + margin -> UnlockTrend.MORE
        else -> UnlockTrend.ABOUT
    }
    return UnlockObservation(count, trend)
}

/** How many earlier days the usual is read from. */
const val BASELINE_DAYS = 7
const val MIN_BASELINE_DAYS = 3
const val MERGE_MILLIS = 5 * 60 * 1000L

/** From this hour, apps still within their daily budget each count as a mark. */
const val EVENING_HOUR = 21

/** The morning card asks for the day's quality from this hour. */
const val MORNING_FROM_HOUR = 4

private const val MIN_MARGIN = 2.0
private const val MARGIN_SHARE = 0.15
