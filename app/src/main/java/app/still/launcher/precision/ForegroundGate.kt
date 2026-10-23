package app.still.launcher.precision

/** What STILL knows about an app as it comes to the front. */
data class ForegroundRule(
    /** A block or a schedule covers it right now. */
    val blocked: Boolean = false,
    /** A task gate keeps it closed until tasks or habits are done. */
    val gated: Boolean = false,
    /** The day's phone limit is used up and it isn't an allowed app. */
    val limited: Boolean = false,
    /** It has a launch delay or a time reminder, so it opens only through STILL's questions. */
    val asksFirst: Boolean = false,
) {
    companion object {
        val NONE = ForegroundRule()
    }
}

/** How STILL steps in front of an app. */
enum class InterventionKind {
    /** Show the blocked screen. */
    BLOCKED,

    /** Show what's left to do before a task gate opens. */
    GATED,

    /** The day's phone limit is used up: only allowed apps open. */
    LIMIT,

    /** Go through the launch delay and "How long?", as if the app were opened from STILL. */
    ASK,

    /** The planned time ran out: offer to leave or to take more time. */
    TIME_UP,
}

/** A request for STILL to step in front of [packageName]. */
data class Intervention(val packageName: String, val kind: InterventionKind, val plannedMinutes: Int = 0)

/**
 * Decides when Precision mode steps in as apps come to the front.
 *
 * A blocked, gated or limited app is covered on every visit. An app with a launch delay or a time
 * reminder needs a pass, which STILL hands out when it opens the app after asking. A pass never
 * runs out while you stay in the app, and it lasts [graceMillis] after you leave, so a quick
 * look at another app doesn't ask again; a time reminder's pass also lasts until the planned
 * time is up. STILL steps in at most once per visit, and a visit ends when another app comes to
 * the front.
 */
class ForegroundGate(val graceMillis: Long = DEFAULT_GRACE_MILLIS) {

    /** Until when each app may come to the front without asking. */
    private val passes = mutableMapOf<String, Long>()

    /** The app in front, as last reported. */
    @get:Synchronized
    var foreground: String? = null
        private set

    /** The app in front arrived with a valid pass. */
    private var inUse = false

    /** STILL already stepped in during this visit. */
    private var intervened = false

    /** Lets [packageName] come to the front without asking until [untilMillis]. Passes only grow. */
    @Synchronized
    fun allow(packageName: String, untilMillis: Long) {
        passes[packageName] = maxOf(passes[packageName] ?: 0L, untilMillis)
    }

    /**
     * [packageName] came to the front at [nowMillis], or is still there when STILL looks again.
     * Returns how STILL should step in, or null to leave it alone.
     */
    @Synchronized
    fun onForeground(packageName: String, nowMillis: Long, rule: ForegroundRule): InterventionKind? {
        if (packageName != foreground) {
            val previous = foreground
            if (previous != null && inUse) allow(previous, nowMillis + graceMillis)
            foreground = packageName
            inUse = passes[packageName]?.let { it >= nowMillis } ?: false
            intervened = false
        }
        if (intervened) return null
        val kind = when {
            rule.blocked -> InterventionKind.BLOCKED
            rule.limited -> InterventionKind.LIMIT
            rule.gated -> InterventionKind.GATED
            rule.asksFirst && !inUse -> InterventionKind.ASK
            else -> return null
        }
        intervened = true
        if (kind != InterventionKind.ASK) {
            // The pass is spent: once the block ends or the gate opens, opening the app asks again.
            passes.remove(packageName)
            inUse = false
        }
        return kind
    }

    /**
     * The planned time in [packageName] ran out, so coming back asks again. Returns true when the
     * app is in front, so STILL should step in now; that counts as this visit's intervention.
     */
    @Synchronized
    fun onTimeUp(packageName: String): Boolean {
        passes.remove(packageName)
        if (foreground != packageName) return false
        inUse = false
        intervened = true
        return true
    }

    companion object {
        const val DEFAULT_GRACE_MILLIS = 60_000L
    }
}
