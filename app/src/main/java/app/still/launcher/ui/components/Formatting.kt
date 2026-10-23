package app.still.launcher.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import java.time.LocalDate
import java.time.LocalTime
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

@Composable
private fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = LocalLocale.current.platformLocale
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}
