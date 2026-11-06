package app.still.launcher.read

/**
 * Counts reading time from what you do in a book. Opening it and each page turn, scroll or tap
 * keep the clock going for [idleMillis]; a book left open without any of that stops counting.
 */
class ReadingClock(private val idleMillis: Long = IDLE_MILLIS) {

    /** A stretch of reading: when it began and how long it counted. */
    data class Sitting(val startedAtMillis: Long, val millis: Long)

    private var startedAt: Long? = null
    private var lastActivity: Long? = null
    private var counted = 0L

    /** Something happened in the book at [now]: the time since the last thing counts, up to the idle limit. */
    fun activity(now: Long) {
        val last = lastActivity
        if (last == null) {
            startedAt = now
        } else {
            counted += (now - last).coerceIn(0, idleMillis)
        }
        lastActivity = now
    }

    /** The sitting so far, counting up to [now]. */
    fun millisSoFar(now: Long): Long = counted + (lastActivity?.let { (now - it).coerceIn(0, idleMillis) } ?: 0L)

    /** Ends the sitting at [now] and starts afresh; null when nothing was read. */
    fun stop(now: Long): Sitting? {
        val start = startedAt ?: return null
        val sitting = Sitting(start, millisSoFar(now))
        startedAt = null
        lastActivity = null
        counted = 0L
        return sitting.takeIf { it.millis > 0 }
    }

    companion object {
        /** Long enough for a dense page, short enough that a book left open doesn't count. */
        const val IDLE_MILLIS = 3 * 60_000L
    }
}
