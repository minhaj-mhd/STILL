package app.still.launcher.apps

import java.text.Normalizer

/** Where a letter's apps start in the alphabetical list, for the fast scroller. */
data class ListSection(val letter: String, val index: Int)

/**
 * The app list's sections, in list order: one per first letter, ignoring case and accents, so
 * "Écran" sits under E. Names starting with a digit or a symbol share "#". [labels] are the
 * list's labels, already sorted.
 */
fun listSections(labels: List<String>): List<ListSection> {
    val seen = mutableSetOf<String>()
    return labels.mapIndexedNotNull { index, label ->
        val letter = sectionLetter(label)
        if (seen.add(letter)) ListSection(letter, index) else null
    }
}

fun sectionLetter(label: String): String {
    val first = label.normalizedForSearch().firstOrNull() ?: return OTHER_SECTION
    return if (first.isLetter()) first.uppercaseChar().toString() else OTHER_SECTION
}

/** Apps installed in the last [RECENT_DAYS] days, newest first, at most [RECENT_MAX]. */
fun <T> recentlyInstalled(apps: List<T>, nowMillis: Long, installedAt: (T) -> Long): List<T> =
    apps
        .filter { nowMillis - installedAt(it) < RECENT_DAYS * MILLIS_PER_DAY }
        .sortedByDescending(installedAt)
        .take(RECENT_MAX)

/**
 * How strongly an app's name shows after [minutes] of use today: fully up to
 * [FADE_FROM_MINUTES], then fading to [FADE_FLOOR] at [FADE_FULL_MINUTES] and staying there.
 */
fun fadeForUse(minutes: Int): Float = when {
    minutes <= FADE_FROM_MINUTES -> 1f
    minutes >= FADE_FULL_MINUTES -> FADE_FLOOR
    else -> 1f - (1f - FADE_FLOOR) * (minutes - FADE_FROM_MINUTES) / (FADE_FULL_MINUTES - FADE_FROM_MINUTES)
}

private val combiningMarks = Regex("\\p{Mn}+")

/** Lower case without accents, for matching names the way people type them. */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")

const val OTHER_SECTION = "#"
const val RECENT_DAYS = 3
const val RECENT_MAX = 5
const val FADE_FROM_MINUTES = 10
const val FADE_FULL_MINUTES = 120
const val FADE_FLOOR = 0.3f
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
