package app.still.launcher.keynote

/** The qualities in [chosen], one per day, tallied with the most-chosen first and ties in the order they came. */
fun qualityDays(chosen: List<String>): List<Pair<String, Int>> =
    chosen.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }
