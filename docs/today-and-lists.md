# Today page and plans

Built 2026-10-26. The rules below are what the code does; change them here and in the files named together.

## Today page as a timeline

`ui/productivity/TodayPage.kt`, with the order in `productivity/Timeline.kt`.

- **Header**: a ring of today's progress, the same count as home's task ring (`TodayAgenda.progress`: done in the accent, overdue in amber, "3/8" inside), then the day ("Monday 26") with small Today, Notes and Plans tabs under it. The Notes and Plans pages keep large tabs.
- **The line**: a thin vertical line with each item's time in the margin beside it.
  - Late tasks (overdue from earlier days) come first, marked "late" and shown in amber until done.
  - Then the tasks and habits due today at a time, in time order. Done items keep their place, struck through. At the same time, tasks come before habits and higher priority goes first.
  - A "now" mark in the accent sits before the first item after the current minute, or last. It moves every minute.
- **Any time**: tasks due today with no time, then habits with no time, lined up with the line's rows.
- **No date**: tasks without a date.
- With nothing late or timed there's no line, and the rows start at the page's edge.
- Habits have a dashed circle, tasks a solid one. Subtasks stay under their unfinished task, ticked off right there.
- Tapping a row still ticks it or logs progress; long-press still opens the menu. The Today page has no other way to edit.

## Plans

Built 2026-10-29, at the user's request: the List kind became plans, with their own page. Tasks and habits are added one at a time; a plan is the one thing added in bulk, written in the app or imported. Plans hold tasks only, no habits.

The left page has three tabs: **Today**, **Notes** and **Plans** (`ui/productivity/LeftPageTabs.kt`).

### A plan

`data/productivity/Plan.kt` (database version 10, an auto-migration from 9). A plan has:

- A name and a colour (teal, violet, coral or amber). Its tasks' circles on Today are in that colour.
- **Days**: day 1, 2, 3… Each task has `planId` and `planDay`. Days without tasks between others are rest days.
- **Starts**: day 1 is the first plan day on or after this date.
- **Plan days fall on**: every day, weekdays, or picked weekdays (`weekdays`, a bit per day, Monday first). Day 2 is the next such date after day 1, and so on.
- **If a day is missed**: **Move the rest along** (the default) or **Keep the dates** (`movesAlong`).
- **Pauses**: when it was paused and resumed (`pauses`, JSON).

`PlanCalendar` (`productivity/Plans.kt`) lists the dates days can fall on: from the start, on the plan's weekdays, outside its pauses.

### Where days fall

`planProgress` works out each day, in order; `withPlanDates` gives Today, task gates, home and reminders the tasks on those dates. Nothing is written back: the dates are worked out each time from the plan and when tasks were done.

When the plan moves along:

- **Not finished by its date**: it stays due today instead of going overdue, even on a day the plan doesn't fall on. Every later day moves to the plan dates after it.
- **Finished late**: the later days move by as many plan days as it was late.
- **Finished early**: it catches up days the plan had slipped by, but nothing moves earlier than first planned.
- **When a day counts as finished**: once all its tasks are done. Until then, the tasks still to do carry on together, and the next day waits.
- **Gaps stay**: day 1, 3 and 5, with day 1 missed once, become day 1 tomorrow, then 3 and 5 two plan days apart.

When it keeps its dates, every day stays on its date and goes overdue on Today if it isn't done.

A paused plan's unfinished tasks are off Today, gates and reminders. Pausing adds a pause from today; resuming ends it today, so the days after it carry on from where they were rather than counting as behind. A pause started and ended on the same day leaves nothing.

On the Today page a plan task shows "Exam week · day 2 of 14" (or "Day 2 of 14" for a plan with no name). Ticking a moved task saves the stored copy, not the moved one (`ProductivityRepository.setTaskDone`).

### Plans tab

`ui/productivity/PlansPage.kt`: plans going now (and ones still to start), then paused, then finished. Each row has the plan's colour, name, "Day 4 of 14", a bar of the days done, how it's going (on track, N days behind, N days overdue, paused since, starts, finished) and when it ends. **+** starts a new plan.

### A plan's page

`ui/productivity/PlanPage.kt`, opened from its row:

- Its dates ("Mon 26 Oct – Mon 9 Nov · 14 days · was Sun 8 Nov" once it has moved) and what a missed day does.
- **Insights**: a ring of days done, tasks done, days done on time, and how it's going.
- **A strip of its days**: on time in the plan's colour, late in amber, missed in red (plans that keep dates), today outlined.
- Each day with its date and "2/3" done. Tap a day to show its tasks; tap a task to tick it.
- **Pause** or **Resume**, **Edit**, and **Delete**, which asks first and deletes its tasks too.

### Writing or importing a plan

`ui/productivity/PlanEditor.kt`. Nothing is saved until **Create** (or **Done** when editing); then the plan's page opens. A new plan is written here or pasted:

- **Write it here**: name, start (Today, Tomorrow or a picked date), the weekdays, what a missed day does, colour, then each day's tasks with an optional time. **Add a task** under a day; **Add a day** at the end.
- **Paste a plan**: a box with **Paste** and **Copy prompt for AI**, then **Read it**, which fills in the plan to check and change before creating it. **Copy prompt for AI** copies `pastePrompt()`: the format, an example and today's date. Paste it into ChatGPT, Claude or Gemini, add what you want after it, then paste the reply back.
- **Check these** lists what couldn't be read, habits left out, and repeating tasks kept on their first day only.

Editing a saved plan changes the same things. Tasks keep what's been done to them; taking one out deletes it with its subtasks. In the task editor a plan task shows its plan day instead of the date, repeat and keep-overdue choices; its time can still change.

The + sheet on Today has a **Plan** chip beside Task and Habit that opens a new plan.

Text shared from another app with **Import as plan** in Android's Share menu opens the plan editor with the text read in, over the Plans tab (`ShareListActivity` hands it to `MainActivity`). In focus mode a share does nothing, since the left page is closed then.

Not built yet:

- "Skip today" for plan tasks. Not doing a plan day already moves it along, but it stays due today, so a task gate waiting on it keeps its apps closed until it's done.
- Habits in plans, which the user chose to leave out.

### The format

Parsed by `productivity/PastedList.kt`; the prompt the app copies says the same. Parsing never fails: what it can't read stays as text and is listed under **Check these**.

```
Exam week                                ← first line without a dash: the plan's name
Day 1                                    ← a day heading: the tasks under it are on that day
- Physics: chapter 3 @7:00 !!            ← a task: title first, then tags
  - Read the notes                       ← indented: a subtask
  > Sections 3.1 to 3.4                  ← ">" under a task: its notes
Day 2
- Chemistry: organic reactions @7:00
```

A task with no day goes on day 1. A task naming a weekday, `@today`, `@tomorrow` or a date goes on the first plan day on or after it. The parser still reads the tags below for habits and repeats, so it can say what a plan leaves out.

| Tag | Means |
|---|---|
| `@day1`, `@day 2`… | Day N of the plan, like a "Day N" heading |
| `@today`, `@tomorrow` | Those days, whatever the start |
| `@mon` … `@sun`, `@monday` … | The first such day on or after the start |
| `@2026-11-02` | That date |
| `@7:00`, `@19:30`, `@7pm`, `@7.30` | A time; an item can have several |
| `@every day`, `@every 2 days`, `@every other day` | Repeats daily (in a plan: once, on its day) |
| `@every mon wed fri`, `@every 2 weeks on tue`, `@every week`, `@every weekday` | Repeats weekly (no days given: the start day's weekday) |
| `@every month`, `@every year` | Monthly on the start date, yearly |
| `@daily`, `@weekly`, `@weekdays`, `@weekends`, `@monthly`, `@yearly` | Short forms |
| `@until day14`, `@until @day7`, `@until 2026-12-15` | Last day it repeats |
| `!`, `!!`, `!!!`, `!low`, `!medium`, `!high` | Priority, the same marks the Today page shows |
| `@habit` | Makes it a habit, which a plan leaves out |
| `@20min`, `@1h` | Habit tracked in minutes |
| `@8x`, `@8times` | Habit tracked as a count |
| `@5km`, `@10pages` | Habit tracked as an amount in that unit |
| `@3/week`, `@10/month` | Habit counted per week or month; without `@every` it has no set days |

More rules:

- **Line kinds**
  - Bullets can be `-`, `*`, `+`, `•`, `1.` or `1)`, with or without a Markdown checkbox (`- [ ]`).
  - Bold, italics and code marks are dropped.
  - If the text has no bullets at all, every line is a task.
- **Code blocks**: if the text has a ``` block, only the first one is read. AI replies wrap the list in one and chat around it.
- **Headings**: lines that aren't bullets.
  - The first heading, before any item, is the plan's name.
  - "Day 3", a full weekday name ("Wednesday"), "Today", "Tomorrow" or a date at the start of a heading puts the items under it on that day.
  - Short weekday names don't count in headings, so "SAT prep" stays a name.
- **Subtasks**
  - Any line indented more than the first item is a subtask of the item above it. Deeper levels are flattened into subtasks.
  - Subtasks take no tags. A duration on a task or subtask stays in its title as "(45min)".
  - Indented lines under a habit become notes, since habits have no subtasks.
- **Tags**: an unknown `@word` stays in the title, so "Email @sam" keeps its "@sam", and is listed under Check these.
- **Limits**: up to 100 items, 50 subtasks each and 20,000 characters.

Tests: `PastedListTest` (format, including that the prompt's own example reads cleanly), `TimelineTest` (order and the now mark), `PlansTest` (moving missed days along, weekdays, pauses, keeping dates, and pasted plans).
