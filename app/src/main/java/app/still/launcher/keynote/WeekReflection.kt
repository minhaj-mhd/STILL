package app.still.launcher.keynote

import kotlin.math.roundToInt

/**
 * The last seven days in plain facts for Insights: what happened when STILL paused, unlocks a
 * day against the week before, the qualities chosen, and what got finished. No score.
 */
data class WeekReflection(
    /** Times you turned back, of all the times STILL paused before an app. */
    val turnedBack: Int = 0,
    val paused: Int = 0,
    /** Unlocks a day this week and the week before; null without enough days to go on. */
    val unlocksPerDay: Int? = null,
    val unlocksBeforePerDay: Int? = null,
    /** Qualities chosen, with how many days each, most days first. */
    val qualities: List<Pair<String, Int>> = emptyList(),
    /** Tasks and habits finished. */
    val finished: Int = 0,
) {
    val isEmpty: Boolean get() = paused == 0 && unlocksPerDay == null && qualities.isEmpty() && finished == 0
}

/** How many of [choices] (turned back or not) were turning back, and how many there were. */
fun turnedBackOf(choices: List<Boolean>): Pair<Int, Int> = choices.count { it } to choices.size

/** The qualities in [chosen], one per day, tallied with the most-chosen first and ties in the order they came. */
fun qualityDays(chosen: List<String>): List<Pair<String, Int>> =
    chosen.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

/** The average of [days]' counts, rounded; null with fewer than [MIN_BASELINE_DAYS] days that have any. */
fun perDay(days: List<Int>): Int? {
    val counted = days.filter { it > 0 }
    if (counted.size < MIN_BASELINE_DAYS) return null
    return (counted.sum().toDouble() / counted.size).roundToInt()
}
