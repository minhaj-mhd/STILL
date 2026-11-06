package app.still.launcher.read

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.roundToInt

/** Words a minute for a reading-time estimate. */
private const val WORDS_PER_MINUTE = 230

/**
 * Letters and marks a minute, for scripts whose words run long, such as Malayalam's: about the
 * same pace as [WORDS_PER_MINUTE] in English.
 */
private const val CHARACTERS_PER_MINUTE = 1_200

/** How many recent reads say when a series usually lands. */
private const val LANDING_SAMPLE = 7

/** How long after a series is due to look, giving the chat time to save. */
private val CHECK_AFTER_DUE: Duration = Duration.ofMinutes(5)

/** How often to look again once a series is late, and for how long. */
private val LATE_RETRY: Duration = Duration.ofMinutes(20)
private val LATE_FOR: Duration = Duration.ofHours(3)

private const val MINUTES_PER_DAY = 24 * 60

/** Reads saved within this of a late one came in the same catch-up, not on the chat's schedule. */
private val CATCH_UP_WINDOW: Duration = Duration.ofHours(1)

private val ISO_DATE = Regex("""^\s*\d{4}[-./]\d{1,2}[-./]\d{1,2}""")
private val DAY_MONTH = Regex("""^\s*(?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*,?\s+)?\d{1,2}(?:st|nd|rd|th)?\s+[a-z]{3,9}\.?(?:,?\s+\d{4})?""", RegexOption.IGNORE_CASE)
private val MONTH_DAY = Regex("""^\s*(?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*,?\s+)?[a-z]{3,9}\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?""", RegexOption.IGNORE_CASE)
private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val SEPARATOR = Regex("""^\s*[-–—:|·•]+\s*""")
private val EXTENSION = Regex("""\.(?:pdf|epub|docx?|txt|md)$""", RegexOption.IGNORE_CASE)

/**
 * A read's headline from its file name: without a leading date, which the list already shows, or
 * a file extension. A name that's only a date stays as it is.
 */
fun headlineOf(name: String): String {
    val plain = name.replace(EXTENSION, "").trim()
    val date = leadingDate(plain) ?: return plain
    val rest = plain.substring(date.range.last + 1).replaceFirst(SEPARATOR, "").trim()
    return rest.ifEmpty { plain }
}

/** Whether [name] is a date and nothing more, such as "2026-11-06". */
fun isOnlyDate(name: String): Boolean {
    val plain = name.replace(EXTENSION, "").trim()
    val date = leadingDate(plain) ?: return false
    return plain.substring(date.range.last + 1).replaceFirst(SEPARATOR, "").isBlank()
}

/**
 * The day a file's name starts with, if it does. A name without a year takes the one that puts
 * the day nearest before [around], when the file was saved.
 */
fun dateInName(name: String, around: LocalDate): LocalDate? {
    val date = leadingDate(name.replace(EXTENSION, "").trim())?.value ?: return null
    val numbers = Regex("""\d+""").findAll(date).map { it.value }.toList()
    if (ISO_DATE.matches(date)) {
        val (year, month, day) = numbers.map(String::toInt)
        return dateOrNull(year, month, day)
    }
    val month = date.lowercase().split(Regex("""[^a-z]+"""))
        .firstNotNullOfOrNull { word -> MONTHS.indexOfFirst { word.length >= 3 && word.startsWith(it) }.takeIf { it >= 0 } }
        ?.plus(1) ?: return null
    val day = numbers.firstOrNull { it.length <= 2 }?.toInt() ?: return null
    numbers.firstOrNull { it.length == 4 }?.let { return dateOrNull(it.toInt(), month, day) }
    val thisYear = dateOrNull(around.year, month, day) ?: return dateOrNull(around.year - 1, month, day)
    return if (thisYear.isAfter(around.plusDays(1))) dateOrNull(around.year - 1, month, day) else thisYear
}

private fun leadingDate(plain: String): MatchResult? = ISO_DATE.find(plain)
    ?: DAY_MONTH.find(plain)?.takeIf { it.value.hasMonth() }
    ?: MONTH_DAY.find(plain)?.takeIf { it.value.hasMonth() }

private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

private fun String.hasMonth(): Boolean {
    val words = lowercase().split(Regex("""[^a-z]+""")).filter { it.length >= 3 }
    return words.any { word -> MONTHS.any { word.startsWith(it) } }
}

/**
 * When a read counts as having come: when it was saved to Drive, unless its name dates it to an
 * earlier day, as when a week of reads goes into the folder at once. Then it keeps that day, at
 * the time it was saved.
 */
fun arrivalOf(name: String, savedAt: Instant, zone: ZoneId): Instant {
    val saved = savedAt.atZone(zone)
    val day = dateInName(name, saved.toLocalDate()) ?: return savedAt
    if (!day.isBefore(saved.toLocalDate())) return savedAt
    return day.atTime(saved.toLocalTime()).atZone(zone).toInstant()
}

/**
 * The times reads were saved on the chat's schedule, from each file's name and when it was saved:
 * not ones saved after their day, nor ones saved alongside those in the same catch-up.
 */
fun scheduledSaves(saved: List<Pair<String, Instant>>, zone: ZoneId): List<Instant> {
    val (late, onTime) = saved.partition { (name, at) -> arrivalOf(name, at, zone) != at }
    return onTime.map { it.second }.filter { at -> late.none { Duration.between(it.second, at).abs() < CATCH_UP_WINDOW } }
}

/** A read's title: its [headline], or its first heading when the headline is only a date. */
fun readTitle(headline: String, firstHeading: String?): String =
    if (isOnlyDate(headline)) firstHeading?.takeIf { it.isNotBlank() } ?: headline else headline

/** When a series' reads usually land, in minutes after midnight: the median of its latest few. */
fun usualLandingMinute(arrivals: List<Instant>, zone: ZoneId): Int? {
    val minutes = arrivals.sortedDescending().take(LANDING_SAMPLE)
        .map { it.atZone(zone).let { time -> time.hour * 60 + time.minute } }
        .sorted()
    if (minutes.isEmpty()) return null
    return minutes[(minutes.size - 1) / 2]
}

/**
 * When to look for a series' next read: a few minutes after it's due; again every so often while
 * it's late today; otherwise tomorrow. Null when it isn't known when the series lands.
 */
fun nextSeriesCheck(now: ZonedDateTime, landsAtMinute: Int?, arrivedToday: Boolean): ZonedDateTime? {
    landsAtMinute ?: return null
    val minute = landsAtMinute.coerceIn(0, MINUTES_PER_DAY - 1).toLong()
    val dueToday = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minute).plus(CHECK_AFTER_DUE)
    val dueTomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(minute).plus(CHECK_AFTER_DUE)
    return when {
        arrivedToday -> dueTomorrow
        now.isBefore(dueToday) -> dueToday
        now.isBefore(dueToday.plus(LATE_FOR)) -> now.plus(LATE_RETRY)
        else -> dueTomorrow
    }
}

/**
 * About how long [text] takes to read, at least a minute: by its words, or by its characters when
 * its words are long ones, which take longer each.
 */
fun minutesToRead(text: String): Int {
    val byWords = wordsIn(text).toDouble() / WORDS_PER_MINUTE
    val byCharacters = text.count { !it.isWhitespace() }.toDouble() / CHARACTERS_PER_MINUTE
    return max(byWords, byCharacters).roundToInt().coerceAtLeast(1)
}

/** Readable text from a page of HTML: no markup, styles or scripts, spaces tidied. */
fun textOfHtml(html: String): String {
    val body = html
        .replace(HEAD, " ")
        .replace(Regex("""(?is)<(script|style)\b.*?</\1>"""), " ")
        .replace(Regex("""(?s)<[^>]*>"""), " ")
    return decodeEntities(body).replace(Regex("""\s+"""), " ").trim()
}

/** A page's text without its headings: what a read says, for its opening words. */
fun proseOfHtml(html: String): String = textOfHtml(html.replace(HEADING, " "))

/** The first heading on a page, as text. */
fun firstHeadingOf(html: String): String? =
    HEADING.find(html.replace(HEAD, " "))?.groupValues?.get(2)?.let(::textOfHtml)?.takeIf { it.isNotBlank() }

private val HEAD = Regex("""(?is)<head\b.*?</head>""")
private val HEADING = Regex("""(?is)<h([1-6])\b[^>]*>(.*?)</h\1\s*>""")

private fun decodeEntities(text: String): String = text
    .replace(Regex("""&#(\d+);""")) { match -> match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value }
    .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match -> match.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: match.value }
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

/** How many words [text] has. */
fun wordsIn(text: String): Int = text.split(Regex("""\s+""")).count { it.any(Char::isLetterOrDigit) }

/**
 * A read's opening words to show before it's opened, without the headline it may start with,
 * cut at a word near [maxChars].
 */
fun excerptOf(text: String, headline: String, maxChars: Int = 220): String? {
    var body = text.trim()
    if (headline.isNotBlank() && body.startsWith(headline, ignoreCase = true)) body = body.substring(headline.length).trimStart()
    body = body.trimStart('-', '–', '—', ':', '·', ' ')
    if (body.isBlank()) return null
    if (body.length <= maxChars) return body
    val cut = body.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
    return body.substring(0, cut).trimEnd(',', ';', ':', '.', ' ') + "…"
}

/** One day in a series' week. */
enum class WeekDay {
    /** A read came and was finished. */
    READ,

    /** A read came and isn't finished. */
    WAITING,

    /** Today, before anything has come. */
    TODAY,

    /** Nothing came. */
    NONE,
}

/** The seven days to [today], oldest first, from each day's reads (finished or not). */
fun weekOf(readsByDate: Map<LocalDate, List<Boolean>>, today: LocalDate): List<Pair<LocalDate, WeekDay>> =
    (6L downTo 0L).map { back ->
        val date = today.minusDays(back)
        val finished = readsByDate[date].orEmpty()
        val day = when {
            finished.any { it } -> WeekDay.READ
            finished.isNotEmpty() -> WeekDay.WAITING
            date == today -> WeekDay.TODAY
            else -> WeekDay.NONE
        }
        date to day
    }

/** The first day whose reads stay on the phone when [keepDays] are kept. */
fun keepFrom(today: LocalDate, keepDays: Int): LocalDate = today.minusDays((keepDays - 1).coerceAtLeast(0).toLong())
