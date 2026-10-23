package app.still.launcher.ui.blocking

import androidx.annotation.StringRes
import app.still.launcher.R

/** Ready-made schedules to start from, as Minimalist offers them. Every day; apps are chosen by you. */
enum class ScheduleTemplate(@StringRes val nameRes: Int, val startMinute: Int, val endMinute: Int) {
    MINDFUL_MORNING(R.string.schedules_template_morning, 6 * 60, 9 * 60),
    NO_SCREEN_EVENING(R.string.schedules_template_evening, 21 * 60, 7 * 60),
}
