package app.still.launcher

/**
 * STILL's own screens outside the launcher, such as the reader. Coming back to home from one of
 * them isn't coming back from being away, so home doesn't greet it with welcome back.
 */
object OwnScreens {

    /** When one of them was last put away, in epoch milliseconds. */
    @Volatile
    var leftAtMillis = 0L
        private set

    fun markLeft() {
        leftAtMillis = System.currentTimeMillis()
    }
}
