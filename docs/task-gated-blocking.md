# Task gates: block apps until tasks and habits are done

Selected apps stay blocked while chosen tasks or habits are still pending. When they are completed, the apps open normally. Tasks carry a priority, and rules can target a priority level instead of naming individual tasks.

Status: design agreed 2026-10-23 (see §8 for the confirmed defaults).

Build plan:
1. **Gates** (built 2026-10-23): `task_gates` and `gate_events` tables (DB v6), the gate rules in `gates/TaskGates.kt`, enforcement from STILL and through Precision mode, the gate screen with tickable items, emergency opens, and Settings > Task gates with a list and an editor. Apps gates never close are the essential ones plus the default messages, contacts, clock, camera and maps apps.
2. **Markers and exceptions** (built 2026-10-23): "Locks Instagram" on Today page items, "Locked by <gate>" in an app's long-press menu, a home line like "3 apps locked · 2 to go", Always open apps in Settings > Task gates, and a 10 s wait plus slide to confirm before skipping or deleting an item that locks apps. Still open: lowering an item's priority or moving its date in the editor opens a gate without friction.
3. **Focus timer** for duration habits, with timer-completed unlocks counted separately in Insights.

---

## 1. Priority on tasks and habits

Every task and habit gets a priority:

| Level | Meaning |
|---|---|
| High | Must be done today; gates the most apps |
| Medium | Should be done today |
| Low | Nice to do; usually gates nothing |
| None | Never gates anything (default for new items) |

Optional later: free-form labels (`work`, `study`, `health`) that rules can also match.

## 2. Gate rules

A **gate** = which apps are blocked + which pending items block them + when the gate applies.

Two kinds of condition:

1. **By priority** — "Block Instagram, YouTube and Reddit while any **High** task or habit is pending."
   - Match `at least` a level: `≥ High`, `≥ Medium`, `≥ Low`.
   - Choose whether it covers tasks, habits, or both.
2. **By specific items** — "Block games until *Read 15 minutes* and *Gym* are done."
   - Pick one or more tasks/habits; all must be completed (option: any one of them).

Each gate also has:

- **Apps**: individual apps and/or app groups (e.g. "Social", "Games"), reusing the blocking app picker.
- **Active window** (optional): days and times the gate applies, using the shared scheduling model (e.g. weekdays 06:00–18:00). Outside the window the apps open freely even if items are pending.
- **On/off** and a name.

Several gates can apply to one app; the app is blocked if **any** active gate is unsatisfied.

## 3. What counts as "pending"

Proposed default: an item is pending if it is **due today or overdue and not completed**.

- **One-time task with a due date**: pending from its due date (or due time, if set) until completed.
- **Task without a due date**: does **not** gate by default (otherwise it could block forever). Option per gate: "include undated tasks".
- **Recurring task**: only today's occurrence (plus overdue occurrences if "carry over" is on).
- **Daily habit**: pending until today's target is met (yes/no, count, or duration).
- **Weekly quota habit** (e.g. swim 3×/week): gates only when you are **behind pace** — when the remaining days in the week equal the remaining sessions needed. Not on days you can still skip.
- **Skipped / excused** occurrences are not pending (see §5).
- Subtasks: a task is complete only when the task itself is checked off; subtasks don't gate on their own.

## 4. What the user sees

When a gated app is opened:

1. STILL shows a **gate screen** instead of the app: "Finish these to open Instagram", with the pending items listed.
2. The items **can't be ticked off on the gate screen** (decided 2026-10-23): a tap right there would make the gate too easy to wave through. They're finished on the Today page, and the app opens normally once nothing is left.
3. Duration habits ("Read 15 min") offer **Start timer** — a focus session that completes the habit when it ends, so the unlock is earned, not just ticked.
4. "Not now" returns to home.

Elsewhere:

- Apps page: gated apps show a small lock marker and, on long-press, which gate blocks them.
- Today page: items that are currently gating apps are marked, e.g. "unlocks Instagram, YouTube".
- Home: optional module "3 apps locked · 2 tasks to go".

## 5. Safety and honesty

- **Never gateable**: phone/dialer, emergency, Settings, STILL itself, and any app the user marks essential (maps, banking, authenticator, messaging).
- **Emergency open**: per gate, allow N emergency opens per day, each behind a delay (e.g. 30 s countdown) and limited to a few minutes. Logged in Insights.
- **Skip today**: an item can be excused for today with a reason, behind friction (slide to confirm + delay). Counts as missed, not completed, in streaks.
- **Editing friction**: turning off or weakening a gate while it is blocking something requires a delay/confirmation, like Minimalist's schedule-edit confirmation. Strengthening is instant.
- **Honesty limits**: ticking a box can't be verified. Timer-completed habits are the only "proven" completions; Insights can show how many unlocks came from timers vs manual ticks.

## 6. Enforcement on Android

- **From STILL** (home, apps page, search): the launcher checks the gate before launching — no special permission needed.
- **From elsewhere** (notifications, Recents, links, other launchers): needs the optional Accessibility service to detect the app coming to the foreground and show the gate screen.
- Without Accessibility, gates still work for launches from STILL; the gate settings should say so plainly.

## 7. Data model sketch (Room)

```
Task / Habit        + priority: NONE | LOW | MEDIUM | HIGH
                    + labels: List<String>            (later)

Gate                id, name, enabled
                    conditionType: PRIORITY | ITEMS
                    minPriority: LOW | MEDIUM | HIGH  (PRIORITY)
                    includeTasks, includeHabits, includeUndated
                    itemIds: List<ItemRef>, requireAll (ITEMS)
                    scheduleId: optional active window
                    emergencyOpensPerDay, emergencyDelaySec

GateTarget          gateId, packageName | appGroupId
GateEvent           time, gateId, packageName, type: BLOCKED | UNLOCKED | EMERGENCY_OPEN
```

Evaluation: a `Flow` combining today's pending items, gates and the clock produces the current set of gated packages (with the reasons). The launcher and the Accessibility service both read that set, so the check on app launch is instant.

## 8. Decisions (confirmed 2026-10-23)

- [x] Four priority levels: None / Low / Medium / High; new items default to None
- [x] "Pending" = due today or overdue; undated tasks excluded unless a gate opts in
- [x] Weekly quotas gate only when behind pace
- [x] Emergency opens: 3 per gate per day, 30 s countdown, 5 minutes each (adjustable per gate)
- [x] Essential by default: Phone, Messages, Contacts, Clock, Camera, Maps, Settings, STILL; the user can add more (banking, authenticator, work chat)
- [x] No cool-off: a gate opens as soon as everything is done (decided 2026-10-23; an optional per-gate wait was considered and dropped)
- [x] Items can't be ticked off on the gate screen, only on the Today page (decided 2026-10-23)
