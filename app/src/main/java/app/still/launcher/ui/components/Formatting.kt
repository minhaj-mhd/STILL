package app.still.launcher.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import app.still.launcher.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "08:00" or "8:00 AM", following the phone's locale. */
@Composable
fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalLocale.current.platformLocale))

/** "Fri 23 Oct", in the phone's locale order. */
@Composable
fun formatShortDate(date: LocalDate): String = formatDate(date, "EEEdMMM")

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
