package app.still.launcher.productivity

/** How one item on today's list stands, as the task ring on home shows it. */
enum class ItemProgress { DONE, OVERDUE, PENDING }

/**
 * Today's dated tasks and the habits due today, as the home ring shows them: done first, then
 * overdue, then still to do, so the ring fills clockwise like progress. Skipped items, undated
 * tasks and habits that aren't due today are left out.
 */
val TodayAgenda.progress: List<ItemProgress>
    get() {
        val tasks = (overdue + today).filterNot { it.isSkipped }.map { entry ->
            when {
                entry.isDone -> ItemProgress.DONE
                entry.isOverdue -> ItemProgress.OVERDUE
                else -> ItemProgress.PENDING
            }
        }
        val dueHabits = habits.filter { !it.isSkipped && (it.isDone || it.isPending) }.map { entry ->
            if (entry.isDone) ItemProgress.DONE else ItemProgress.PENDING
        }
        return (tasks + dueHabits).sortedBy { it.ordinal }
    }
