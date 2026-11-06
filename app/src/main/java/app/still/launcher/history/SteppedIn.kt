package app.still.launcher.history

import app.still.launcher.data.history.FocusModePeriod
import app.still.launcher.data.history.InterventionType
import java.time.Duration
import java.time.Instant

/** What STILL put in front of apps over a week or month. Plain counts, no score. */
data class SteppedIn(
    /** Times you turned back, of all the times STILL paused before an app. */
    val turnedBack: Int,
    val paused: Int,
    /** Times an app stayed closed: focus mode, blocks and schedules, task gates, and limits. */
    val keptClosed: Int,
    /** Shorts or Reels left. */
    val shortVideos: Int,
    /** Visits and time reminders that ran out while the app was open. */
    val ranOut: Int,
    /** The app STILL stepped in front of most, if any. */
    val mostOften: String?,
) {
    val isEmpty: Boolean get() = paused == 0 && keptClosed == 0 && shortVideos == 0 && ranOut == 0
}

/** Tallies [interventions] (package and type) and the pause [choices] (package and whether you turned back). */
fun steppedIn(interventions: List<Pair<String, InterventionType>>, choices: List<Pair<String, Boolean>>): SteppedIn {
    val types = interventions.map { it.second }
    // A pause is noted both ways; its choice says more.
    val packages = interventions.filter { it.second != InterventionType.PAUSE }.map { it.first } + choices.map { it.first }
    return SteppedIn(
        turnedBack = choices.count { it.second },
        paused = choices.size,
        keptClosed = types.count { it in KEPT_CLOSED },
        shortVideos = types.count { it == InterventionType.SHORT_VIDEO },
        ranOut = types.count { it == InterventionType.VISIT_UP || it == InterventionType.TIME_UP },
        mostOften = packages.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
    )
}

private val KEPT_CLOSED = setOf(
    InterventionType.FOCUS,
    InterventionType.BLOCKED,
    InterventionType.SCHEDULE,
    InterventionType.GATE,
    InterventionType.DAILY_LIMIT,
    InterventionType.APP_LIMIT,
)

/** Focus mode's time between [from] and [to], sessions cut at the edges, and how many sessions touched it. */
fun focusModeTime(periods: List<FocusModePeriod>, from: Instant, to: Instant): Pair<Duration, Int> {
    val inside = periods.filter { it.endedAt > from && it.startedAt < to }
    val time = inside.fold(Duration.ZERO) { sum, period -> sum + Duration.between(maxOf(period.startedAt, from), minOf(period.endedAt, to)) }
    return time to inside.size
}
