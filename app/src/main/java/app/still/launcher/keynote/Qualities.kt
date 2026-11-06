package app.still.launcher.keynote

import kotlinx.serialization.Serializable

/** The qualities in [chosen], one per day, tallied with the most-chosen first and ties in the order they came. */
fun qualityDays(chosen: List<String>): List<Pair<String, Int>> =
    chosen.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

/** A quality you added yourself, kept with its action so it's on offer every day after. */
@Serializable
data class OwnQuality(val quality: String, val action: String)

/**
 * [own] with [quality] kept and its [action] brought up to date, added at the end when it's new.
 * One of [suggested] isn't kept, and names match whatever their case.
 */
fun withOwnQuality(own: List<OwnQuality>, quality: String, action: String, suggested: Collection<String>): List<OwnQuality> {
    val name = quality.trim()
    if (name.isEmpty() || suggested.any { it.equals(name, ignoreCase = true) }) return own
    val kept = OwnQuality(name, action.trim())
    val at = own.indexOfFirst { it.quality.equals(name, ignoreCase = true) }
    return if (at < 0) own + kept else own.toMutableList().also { it[at] = kept }
}

/** [own] without [quality], whatever its case. */
fun withoutOwnQuality(own: List<OwnQuality>, quality: String): List<OwnQuality> =
    own.filterNot { it.quality.equals(quality.trim(), ignoreCase = true) }
