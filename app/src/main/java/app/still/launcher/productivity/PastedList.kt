package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * A plan pasted in or shared from another app, usually an AI chat. The format is in
 * docs/today-and-lists.md and in [pastePrompt]; change all three together. Habits and repeats are still
 * read, so the plan editor can say what it left out.
 *
 *   Exam week                                ← a line without a dash before the items: the plan's name
 *   Day 1                                    ← a day heading: the tasks under it are on that day
 *   - Physics: chapter 3 @7:00 !!            ← a task: its title, then tags (@day2 also names a day)
 *     - Read the notes                       ← indented: a subtask
 *     > Sections 3.1 to 3.4                  ← ">": notes for the task above
 *   - Flashcards @habit @every day @15min    ← a habit, which a plan leaves out
 *
 * Parsing never fails: anything it can't read is kept as text and listed in [PastedList.problems].
 */

/** A day an item names, before the list's first day is picked. */
sealed interface PastedDay {
    data object Today : PastedDay
    data object Tomorrow : PastedDay

    /** Day [number] of the list, counting the start day as day 1. */
    data class ListDay(val number: Int) : PastedDay

    /** The first [weekday] on or after the start day. */
    data class Weekday(val weekday: DayOfWeek) : PastedDay

    data class OnDate(val date: LocalDate) : PastedDay
}

/** How an item repeats, before it has a start day. */
sealed interface PastedRepeat {
    data class Days(val interval: Int) : PastedRepeat

    /** On [days] of every [interval]th week; no days means the start day's weekday. */
    data class Weeks(val days: Set<DayOfWeek>, val interval: Int) : PastedRepeat

    /** On the start day's date every [interval]th month. */
    data class Months(val interval: Int) : PastedRepeat

    data object Years : PastedRepeat
}

/** What a habit tracks per day: minutes, a count or an amount in [unit]. */
data class PastedTarget(val measure: HabitMeasure, val target: Double, val unit: String? = null)

data class PastedItem(
    val title: String,
    /** Where it starts in the pasted text, counting from 1. */
    val line: Int,
    val isHabit: Boolean = false,
    val notes: List<String> = emptyList(),
    val subtasks: List<String> = emptyList(),
    val priority: Priority = Priority.NONE,
    val day: PastedDay? = null,
    /** Asked for no date with @nodate. */
    val noDate: Boolean = false,
    val times: List<LocalTime> = emptyList(),
    val repeat: PastedRepeat? = null,
    val until: PastedDay? = null,
    val target: PastedTarget? = null,
    val quota: Quota? = null,
) {
    /** A one-time task on a numbered day, which can be part of a day-by-day plan. */
    val planDay: Int? get() = (day as? PastedDay.ListDay)?.number?.takeIf { !isHabit && repeat == null }
}

/** Something in the pasted text that was left out or kept as plain text. */
data class PasteProblem(val line: Int, val kind: Kind, val text: String = "") {
    enum class Kind {
        /** [text] starts with @ but isn't a tag; it stays in the title. */
        UNKNOWN_TAG,

        /** "@every [text]" couldn't be read; the item doesn't repeat. */
        UNKNOWN_REPEAT,

        /** "@until [text]" couldn't be read; the item repeats with no end. */
        UNKNOWN_UNTIL,

        /** [text] (@3/week and the like) only works on habits and was left out. */
        HABIT_ONLY,

        /** Times on a habit counted per week or month, which has no days to put them on. */
        TIMES_WITHOUT_DAYS,

        /** More than [MAX_PASTED_ITEMS] items; the rest were left out. */
        TOO_MANY,
    }
}

data class PastedList(val name: String?, val items: List<PastedItem>, val problems: List<PasteProblem>) {
    val subtaskCount: Int get() = items.sumOf { it.subtasks.size }
}

const val MAX_PASTED_ITEMS = 100
const val MAX_PASTED_SUBTASKS = 50

/** The most text the list sheet and Share accept; far more than any list needs. */
const val MAX_PASTED_CHARS = 20_000

fun parsePastedList(text: String): PastedList {
    val lines = fencedBlock(text.take(MAX_PASTED_CHARS).lines())
    // A list with no bullets at all is one item per line.
    val hasBullets = lines.any { BULLET.matches(it.content) || CHECKBOX.matches(it.content) }
    val problems = mutableListOf<PasteProblem>()
    val items = mutableListOf<ItemBuilder>()
    var name: String? = null
    var sectionDay: PastedDay? = null
    var topIndent: Int? = null

    for (line in lines) {
        val content = line.content
        if (content.isBlank()) continue
        val note = NOTE.matchEntire(content)
        if (note != null) {
            items.lastOrNull()?.notes?.add(cleanText(note.groupValues[1]))
            continue
        }

        val bulletText = BULLET.matchEntire(content)?.groupValues?.get(1)
            ?: CHECKBOX.matchEntire(content)?.groupValues?.get(1)
            ?: content.takeIf { !hasBullets }
        if (bulletText == null) {
            // A heading: the list's name, or a day the items under it are on.
            val heading = cleanText(content.trimStart('#'))
            val day = headingDay(heading)
            when {
                day != null -> sectionDay = day
                name == null && items.isEmpty() -> name = heading.trimEnd(':').trim().takeIf { it.any(Char::isLetterOrDigit) }
            }
            continue
        }
        val itemText = CHECKBOX.matchEntire(bulletText)?.groupValues?.get(1) ?: bulletText
        val current = items.lastOrNull()
        val top = topIndent
        if (current != null && top != null && line.indent > top) {
            val subtask = readLine(itemText, line.number, problems = null).titleWithTarget()
            if (subtask.isEmpty()) continue
            // Habits have no subtasks; their steps go in the notes instead.
            if (current.isHabit) current.notes.add("– $subtask")
            else if (current.subtasks.size < MAX_PASTED_SUBTASKS) current.subtasks.add(subtask)
            continue
        }
        if (top == null || line.indent < top) topIndent = line.indent
        if (items.size == MAX_PASTED_ITEMS) {
            problems.add(PasteProblem(line.number, PasteProblem.Kind.TOO_MANY))
            break
        }
        val read = readLine(itemText, line.number, problems)
        if (cleanTitle(read.title.toString()).isEmpty()) continue
        items.add(read.toBuilder(sectionDay, problems))
    }
    return PastedList(name, items.map { it.build() }, problems.distinct())
}

/**
 * The start day, tasks that don't name a day ([datelessOnStart]: on the start day, otherwise
 * without a date), and [today] turn a pasted item into what's saved; [planTasks] then puts tasks
 * on their plan days. [createdAt] keeps the pasted order, since tasks and subtasks list oldest first.
 */
fun PastedItem.resolve(
    start: LocalDate,
    today: LocalDate,
    datelessOnStart: Boolean,
    createdAt: Instant,
): ResolvedItem {
    val date = day?.let { dateOf(it, start, today) } ?: when {
        isHabit || repeat != null || times.isNotEmpty() -> start
        noDate || !datelessOnStart -> null
        else -> start
    }
    val notesText = notes.joinToString("\n")
    fun schedule(on: LocalDate, recurrence: Recurrence) = Schedule(
        start = on,
        recurrence = recurrence,
        until = until?.let { dateOf(it, start, today) }?.takeIf { recurrence != Recurrence.Once && it >= on },
        times = times.distinct().sorted(),
    )
    if (isHabit) {
        val on = date ?: start
        val recurrence = repeat?.toRecurrence(on)
        val habit = Habit(
            title = title,
            notes = notesText,
            priority = priority,
            measure = target?.measure ?: HabitMeasure.CHECK,
            target = target?.target ?: 1.0,
            unit = target?.unit,
            // Counted per week or month with no days given: logged on any day.
            schedule = if (recurrence == null && quota != null) null else schedule(on, recurrence ?: Recurrence.Daily()),
            quota = quota,
            createdAt = createdAt,
        )
        return ResolvedItem.NewHabit(habit)
    }
    val task = Task(
        title = title,
        notes = notesText,
        priority = priority,
        schedule = date?.let { schedule(it, repeat?.toRecurrence(it) ?: Recurrence.Once) },
        createdAt = createdAt,
    )
    val subtaskRows = subtasks.mapIndexed { index, subtask ->
        Task(title = subtask, createdAt = createdAt.plusMillis(index + 1L))
    }
    return ResolvedItem.NewTask(task, subtaskRows)
}

/** A pasted item as it will be saved. */
sealed interface ResolvedItem {
    data class NewTask(val task: Task, val subtasks: List<Task>) : ResolvedItem
    data class NewHabit(val habit: Habit) : ResolvedItem
}

fun dateOf(day: PastedDay, start: LocalDate, today: LocalDate): LocalDate = when (day) {
    PastedDay.Today -> today
    PastedDay.Tomorrow -> today.plusDays(1)
    is PastedDay.ListDay -> start.plusDays(day.number - 1L)
    is PastedDay.Weekday -> start.with(TemporalAdjusters.nextOrSame(day.weekday))
    is PastedDay.OnDate -> day.date
}

fun PastedRepeat.toRecurrence(start: LocalDate): Recurrence = when (this) {
    is PastedRepeat.Days -> Recurrence.Daily(interval)
    is PastedRepeat.Weeks -> Recurrence.Weekly(days.ifEmpty { setOf(start.dayOfWeek) }, interval)
    is PastedRepeat.Months -> Recurrence.MonthlyOnDay(start.dayOfMonth, interval)
    PastedRepeat.Years -> Recurrence.Yearly
}

/**
 * What "Copy prompt for AI" copies: the plan format, an example and [today], so an AI can write a
 * plan STILL reads. The person adds what they want after it.
 */
fun pastePrompt(today: LocalDate): String = """
I plan my days in a phone app called STILL. Make me the day-by-day plan I describe at the end, in the format below, and reply with only the plan inside one code block.

Format:
- First line: a short name for the plan, with no dash.
- Each day starts with a line of its own: "Day 1", "Day 2" and so on. Day 1 is the day I start.
- Under each day, one task per line, starting with "- ". Write the title first, then any tags.
- Subtasks: indented two spaces under their task, starting with "- ". Plain text, no tags.
- Notes for a task: a line under it starting with "> ".
- Each task is done once, on its day: no repeating tasks or habits.

Tags (all optional):
- Time: @7:00 or @19:30 (24-hour).
- Priority: ! (low), !! (medium), !!! (high).

Example:
```
Exam week
Day 1
- Physics: chapter 3 @7:00 !!
  - Read the notes
  - Solve 10 problems
  > Sections 3.1 to 3.4
Day 2
- Chemistry: organic reactions @7:00
Day 3
- Maths past paper @9:00 !!!
```

Today is ${today.format(PROMPT_DATE)}.

What I want:
""".trimStart()

private val PROMPT_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

private data class TextLine(val number: Int, val indent: Int, val content: String)

/** The lines inside the first ``` block if there is one, as AI replies wrap lists in one; otherwise all of them. */
private fun fencedBlock(lines: List<String>): List<TextLine> {
    val all = lines.mapIndexed { index, raw ->
        val indent = raw.takeWhile { it.isWhitespace() }.sumOf { if (it == '\t') 4 else 1 }
        TextLine(index + 1, indent, raw.trim())
    }
    val open = all.indexOfFirst { it.content.startsWith("```") }
    if (open < 0) return all
    val close = all.drop(open + 1).indexOfFirst { it.content.startsWith("```") }
    val inside = if (close < 0) all.drop(open + 1) else all.subList(open + 1, open + 1 + close)
    return inside.takeIf { block -> block.any { it.content.isNotBlank() } } ?: all.filterNot { it.content.startsWith("```") }
}

private val BULLET = Regex("""^(?:[-*+•‣◦–—]|\d{1,3}[.)])\s+(.*)$""")
private val CHECKBOX = Regex("""^\[[ xX✓✔]?]\s*(.*)$""")
private val NOTE = Regex("""^>\s?(.*)$""")

/** Markdown emphasis and code marks, which AI replies often add. */
private fun cleanText(text: String): String = text.replace("**", "").replace("__", "").replace("~~", "").replace("`", "").trim()

private val DAY_HEADING = Regex("""^day\s*(\d{1,3})\b.*""", RegexOption.IGNORE_CASE)

private fun headingDay(heading: String): PastedDay? {
    DAY_HEADING.matchEntire(heading)?.let { match ->
        return match.groupValues[1].toInt().takeIf { it in 1..MAX_LIST_DAY }?.let { PastedDay.ListDay(it) }
    }
    // Whole weekday names only: "Sat" or "Sun" can start a heading that isn't a day ("SAT prep").
    val first = heading.lowercase().split(Regex("""[\s:,.(–—]+""")).firstOrNull().orEmpty()
    return when (first) {
        "today" -> PastedDay.Today
        "tomorrow" -> PastedDay.Tomorrow
        else -> DayOfWeek.entries.firstOrNull { it.name.lowercase() == first.removeSuffix("s") }?.let { PastedDay.Weekday(it) }
            ?: isoDate(first)?.let { PastedDay.OnDate(it) }
    }
}

private const val MAX_LIST_DAY = 366

private val WEEKDAY_NAMES: Map<String, DayOfWeek> = DayOfWeek.entries.flatMap { day ->
    val full = day.name.lowercase()
    val short = full.take(3)
    val extra = when (day) {
        DayOfWeek.TUESDAY -> listOf("tues")
        DayOfWeek.WEDNESDAY -> listOf("weds")
        DayOfWeek.THURSDAY -> listOf("thur", "thurs")
        else -> emptyList()
    }
    (listOf(full, short, "${full}s") + extra).map { it to day }
}.toMap()

private fun isoDate(text: String): LocalDate? =
    if (ISO_DATE.matches(text)) try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null } else null

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val TIME = Regex("""^(\d{1,2})(?:[:.](\d{2}))?(am|pm)?$""")
private val DURATION = Regex("""^(\d+(?:[.,]\d+)?)(min|mins|minute|minutes|m|h|hr|hrs|hour|hours)$""")
private val COUNT = Regex("""^(\d+)(x|times)$""")
private val QUOTA = Regex("""^(\d+)x?/(week|wk|w|month|mo)$""")
private val AMOUNT = Regex("""^(\d+(?:[.,]\d+)?)([a-z]+)$""")
private val PRIORITY = Regex("""^!{1,3}$""")
private val PRIORITY_WORD = Regex("""^!(low|medium|med|high)$""", RegexOption.IGNORE_CASE)

/** One item's line, split into its title and tags. */
private class ReadLine(val number: Int) {
    val title = StringBuilder()
    var isHabit = false
    var priority = Priority.NONE
    var day: PastedDay? = null
    var noDate = false
    val times = mutableListOf<LocalTime>()
    var repeat: PastedRepeat? = null
    var until: PastedDay? = null
    var target: PastedTarget? = null
    var targetText: String? = null
    var quota: Quota? = null

    fun addTitle(word: String) {
        if (title.isNotEmpty()) title.append(' ')
        title.append(word)
    }

    /** The title, with a duration or amount kept in brackets, as on a task or subtask it isn't tracked. */
    fun titleWithTarget(): String = listOfNotNull(cleanTitle(title.toString()).ifEmpty { null }, targetText?.let { "($it)" }).joinToString(" ")

    var quotaText: String? = null

    fun toBuilder(sectionDay: PastedDay?, problems: MutableList<PasteProblem>): ItemBuilder {
        quotaText?.takeIf { !isHabit }?.let { problems.add(PasteProblem(number, PasteProblem.Kind.HABIT_ONLY, it)) }
        if (isHabit && quota != null && repeat == null && times.isNotEmpty()) problems.add(PasteProblem(number, PasteProblem.Kind.TIMES_WITHOUT_DAYS))
        return ItemBuilder(
            title = if (isHabit) cleanTitle(title.toString()) else titleWithTarget(),
            line = number,
            isHabit = isHabit,
            priority = priority,
            day = day ?: sectionDay.takeIf { !noDate },
            noDate = noDate,
            times = if (isHabit && quota != null && repeat == null) emptyList() else times.toList(),
            repeat = repeat,
            until = until,
            target = target.takeIf { isHabit },
            quota = quota.takeIf { isHabit },
        )
    }
}

private class ItemBuilder(
    val title: String,
    val line: Int,
    val isHabit: Boolean,
    val priority: Priority,
    val day: PastedDay?,
    val noDate: Boolean,
    val times: List<LocalTime>,
    val repeat: PastedRepeat?,
    val until: PastedDay?,
    val target: PastedTarget?,
    val quota: Quota?,
) {
    val notes = mutableListOf<String>()
    val subtasks = mutableListOf<String>()

    fun build() = PastedItem(
        title = title,
        line = line,
        isHabit = isHabit,
        notes = notes.filter { it.isNotBlank() },
        subtasks = subtasks.toList(),
        priority = priority,
        day = day,
        noDate = noDate,
        times = times,
        repeat = repeat,
        until = until,
        target = target,
        quota = quota,
    )
}

/** Trailing separators an AI leaves between a title and its tags, e.g. "Physics –". */
private fun cleanTitle(title: String): String = title.trim().trimEnd(':', '-', '–', '—', ',', ';', '|').trim()

/**
 * Reads "Title words @tag args !! more words". Words that aren't tags or a tag's arguments join
 * the title, so "@" in the middle of a title (an unknown tag) is kept as written. [problems] is
 * null for subtasks, whose tags are dropped quietly.
 */
private fun readLine(text: String, number: Int, problems: MutableList<PasteProblem>?): ReadLine {
    val read = ReadLine(number)
    val words = cleanText(text).split(Regex("""\s+""")).filter { it.isNotEmpty() }
    var index = 0
    var untilNext = false
    while (index < words.size) {
        val word = words[index]
        val lower = word.lowercase().trimEnd(',', ';')
        index++
        when {
            PRIORITY.matches(lower) -> read.priority = Priority.entries[lower.length]
            PRIORITY_WORD.matches(lower) -> read.priority = when (lower.drop(1)) {
                "low" -> Priority.LOW
                "high" -> Priority.HIGH
                else -> Priority.MEDIUM
            }
            !lower.startsWith("@") || lower.length == 1 -> read.addTitle(word)
            else -> {
                val tag = lower.drop(1)
                // "@day 3" and "@until day 7": a day number can follow as its own word.
                fun takeDayNumber(): Int? = words.getOrNull(index)?.toIntOrNull()?.takeIf { it in 1..MAX_LIST_DAY }?.also { index++ }
                val dayTag: PastedDay? = when {
                    tag == "day" -> takeDayNumber()?.let { PastedDay.ListDay(it) }
                    else -> dayWord(tag)
                }
                when {
                    dayTag != null -> if (untilNext) {
                        read.until = dayTag
                        untilNext = false
                    } else {
                        read.day = dayTag
                    }
                    tag == "habit" -> read.isHabit = true
                    tag == "nodate" || tag == "anytime" -> read.noDate = true
                    tag == "every" -> {
                        val args = mutableListOf<String>()
                        while (index < words.size && isRepeatWord(words[index])) args.add(words[index++])
                        val repeat = repeatOf(args)
                        if (repeat != null) read.repeat = repeat
                        else problems?.add(PasteProblem(number, PasteProblem.Kind.UNKNOWN_REPEAT, args.joinToString(" ")))
                    }
                    tag in REPEAT_SHORTCUTS -> read.repeat = REPEAT_SHORTCUTS.getValue(tag)
                    tag == "until" || tag == "till" -> {
                        val next = words.getOrNull(index)?.lowercase()?.trimEnd(',', ';')
                        when {
                            next == null || next.startsWith("@") -> untilNext = true
                            next == "day" -> {
                                index++
                                takeDayNumber()?.let { read.until = PastedDay.ListDay(it) }
                                    ?: problems?.add(PasteProblem(number, PasteProblem.Kind.UNKNOWN_UNTIL, next))
                            }
                            else -> {
                                index++
                                dayWord(next)?.let { read.until = it }
                                    ?: problems?.add(PasteProblem(number, PasteProblem.Kind.UNKNOWN_UNTIL, next))
                            }
                        }
                    }
                    else -> {
                        val time = timeOf(tag)
                        val measured = targetOf(tag)
                        val quota = quotaOf(tag)
                        when {
                            time != null -> read.times.add(time)
                            measured != null -> {
                                read.target = measured
                                read.targetText = word.drop(1)
                            }
                            quota != null -> {
                                read.quota = quota
                                read.quotaText = word
                            }
                            else -> {
                                problems?.add(PasteProblem(number, PasteProblem.Kind.UNKNOWN_TAG, word))
                                read.addTitle(word)
                            }
                        }
                    }
                }
            }
        }
    }
    return read
}

/** today, tomorrow, day3, mon, monday or 2026-11-02, without the @. */
private fun dayWord(word: String): PastedDay? = when {
    word == "today" -> PastedDay.Today
    word == "tomorrow" || word == "tmrw" -> PastedDay.Tomorrow
    word.startsWith("day") && word.drop(3).toIntOrNull() != null ->
        word.drop(3).toInt().takeIf { it in 1..MAX_LIST_DAY }?.let { PastedDay.ListDay(it) }
    word.removePrefix("@") in WEEKDAY_NAMES -> PastedDay.Weekday(WEEKDAY_NAMES.getValue(word.removePrefix("@")))
    else -> isoDate(word.removePrefix("@"))?.let { PastedDay.OnDate(it) }
}

/** 7:00, 19:30, 7pm, 7:30am; a bare number isn't a time. */
private fun timeOf(word: String): LocalTime? {
    val match = TIME.matchEntire(word) ?: return null
    val (hourText, minuteText, half) = match.destructured
    if (minuteText.isEmpty() && half.isEmpty()) return null
    var hour = hourText.toInt()
    val minute = minuteText.ifEmpty { "0" }.toInt()
    if (minute > 59) return null
    if (half.isNotEmpty()) {
        if (hour !in 1..12) return null
        hour = hour % 12 + if (half == "pm") 12 else 0
    }
    return if (hour in 0..23) LocalTime.of(hour, minute) else null
}

private fun targetOf(word: String): PastedTarget? {
    DURATION.matchEntire(word)?.let { match ->
        val amount = match.groupValues[1].replace(',', '.').toDouble()
        val minutes = if (match.groupValues[2].startsWith("h")) amount * 60 else amount
        return PastedTarget(HabitMeasure.DURATION, minutes).takeIf { minutes > 0 }
    }
    COUNT.matchEntire(word)?.let { match ->
        return PastedTarget(HabitMeasure.COUNT, match.groupValues[1].toDouble()).takeIf { it.target > 0 }
    }
    AMOUNT.matchEntire(word)?.let { match ->
        val amount = match.groupValues[1].replace(',', '.').toDouble()
        return PastedTarget(HabitMeasure.MEASUREMENT, amount, match.groupValues[2]).takeIf { amount > 0 }
    }
    return null
}

private fun quotaOf(word: String): Quota? {
    val match = QUOTA.matchEntire(word) ?: return null
    val count = match.groupValues[1].toInt()
    val period = if (match.groupValues[2].startsWith("m")) QuotaPeriod.MONTH else QuotaPeriod.WEEK
    val most = if (period == QuotaPeriod.WEEK) 7 else 31
    return if (count in 1..most) Quota(count, period) else null
}

private val REPEAT_SHORTCUTS = mapOf(
    "daily" to PastedRepeat.Days(1),
    "weekly" to PastedRepeat.Weeks(emptySet(), 1),
    "weekdays" to PastedRepeat.Weeks(DayOfWeek.entries.take(5).toSet(), 1),
    "weekends" to PastedRepeat.Weeks(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1),
    "monthly" to PastedRepeat.Months(1),
    "yearly" to PastedRepeat.Years,
)

private val REPEAT_UNITS = setOf(
    "day", "days", "week", "weeks", "month", "months", "year", "years", "weekday", "weekdays", "weekend", "weekends",
    "other", "on", "and", "&",
)

/** A word "@every" can take: a number, a unit, a weekday, or a list of weekdays like "mon,wed,fri". */
private fun isRepeatWord(word: String): Boolean {
    val parts = word.lowercase().split(',', '/').filter { it.isNotEmpty() }
    return parts.isNotEmpty() && parts.all { it.toIntOrNull() != null || it in REPEAT_UNITS || it in WEEKDAY_NAMES }
}

/** "day", "2 days", "other week", "mon wed fri", "2 weeks on tue", "weekday", "month", "year". */
private fun repeatOf(args: List<String>): PastedRepeat? {
    val words = args.flatMap { it.lowercase().split(',', '/') }.filter { it.isNotEmpty() && it != "on" && it != "and" && it != "&" }
    if (words.isEmpty()) return null
    var interval = 1
    var rest = words
    val number = if (rest.first() == "other") 2 else rest.first().toIntOrNull()
    if (number != null) {
        if (number !in 1..MAX_INTERVAL) return null
        interval = number
        rest = rest.drop(1)
    }
    val days = rest.mapNotNull { WEEKDAY_NAMES[it] }.toSet()
    val units = rest.filter { it !in WEEKDAY_NAMES }
    return when {
        units.isEmpty() && days.isNotEmpty() -> PastedRepeat.Weeks(days, interval)
        units.size != 1 -> null
        units[0].startsWith("weekday") -> PastedRepeat.Weeks(DayOfWeek.entries.take(5).toSet(), interval)
        units[0].startsWith("weekend") -> PastedRepeat.Weeks(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), interval)
        units[0].startsWith("day") && days.isEmpty() -> PastedRepeat.Days(interval)
        units[0].startsWith("week") -> PastedRepeat.Weeks(days, interval)
        units[0].startsWith("month") && days.isEmpty() -> PastedRepeat.Months(interval)
        units[0].startsWith("year") && days.isEmpty() && interval == 1 -> PastedRepeat.Years
        else -> null
    }
}

private const val MAX_INTERVAL = 365
