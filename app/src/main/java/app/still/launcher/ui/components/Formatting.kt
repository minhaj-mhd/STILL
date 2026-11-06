package app.still.launcher.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import app.still.launcher.R
import app.still.launcher.data.TimeFormat
import app.still.launcher.ui.theme.currentAppearance
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "18:04" or "6:04 pm": 12- or 24-hour as chosen in Customise, written the locale's way. */
@Composable
fun formatTime(time: LocalTime): String = time.format(timeFormatter(is24HourTime(), LocalLocale.current.platformLocale))

/** Whether STILL shows 24-hour time here. */
@Composable
fun is24HourTime(): Boolean = uses24Hour(LocalContext.current, currentAppearance.timeFormat)

/** Whether [format] means 24-hour time; [TimeFormat.PHONE] asks Android. */
fun uses24Hour(context: Context, format: TimeFormat): Boolean = when (format) {
    TimeFormat.PHONE -> DateFormat.is24HourFormat(context)
    TimeFormat.H24 -> true
    TimeFormat.H12 -> false
}

/** Hours and minutes, 12- or 24-hour, in [locale]'s order and marks. */
fun timeFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)

/** STILL's time format for text outside Compose, such as notifications; kept in step with settings. */
object TimeFormats {
    @Volatile
    var choice: TimeFormat = TimeFormat.PHONE

    /** [at]'s time of day in the phone's zone. */
    fun format(context: Context, at: Instant): String =
        at.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter(uses24Hour(context, choice), Locale.getDefault()))
}

/** "Fri 23 Oct", in the phone's locale order. */
@Composable
fun formatShortDate(date: LocalDate): String = formatDate(date, "EEEdMMM")

/** "Friday 23", the Today page's heading. */
@Composable
fun formatWeekdayAndDay(date: LocalDate): String = formatDate(date, "EEEEd")

/** "Fri 23", for days within the week. */
@Composable
fun formatWeekdayShortDay(date: LocalDate): String = formatDate(date, "EEEd")

/** "October", or "October 2025" outside this year. */
@Composable
fun formatMonth(date: LocalDate): String = formatDate(date, if (date.year == LocalDate.now().year) "MMMM" else "MMMMyyyy")

/** "Fri 23 Oct 2026". */
@Composable
fun formatLongDate(date: LocalDate): String = formatDate(date, "EEEdMMMyyyy")

/** "Fri 23 Oct, 18:30". */
@Composable
fun formatDateTime(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    return stringResource(R.string.date_time, formatShortDate(local.toLocalDate()), formatTime(local.toLocalTime()))
}

/** Screen time as "2h 5m", "45m" or "<1m". */
@Composable
fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.duration_hours, hours)
        duration.toMinutes() > 0 -> stringResource(R.string.duration_minutes, duration.toMinutes())
        else -> stringResource(R.string.duration_less_than_minute)
    }
}

@Composable
private fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = LocalLocale.current.platformLocale
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}
