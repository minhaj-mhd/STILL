# App limits and Inner practice

Stage 5, built 2026-10-23 after mockups. The rules below are what the code does; change them here and in `limit/AppLimits.kt` and `keynote/Practice.kt` together.

## App limits

Any app except the phone app, Settings and STILL can have its own limits, set from its long-press menu (Limits) or Settings > App limits:

- **A day**: minutes per day, counted with Usage access on the daily limit's day (from midnight or 4 AM). Once used up, the app is done for today until the next day starts.
- **Each visit**: the longest one visit lasts. With Precision mode a visit runs from the app coming to the front until it has been away for a minute; without it, from STILL opening the app until STILL is on screen again. When it runs out, Precision mode shows "That's your N minutes" with Leave; without it, a notification does.
- **Wait between visits**: after a visit ends, the app opens again only after this wait.
- **Ask what I'm opening it for**: the pause, below.

Tightening counts at once. Loosening (a higher or removed cap, a shorter wait, no pause) waits until tomorrow, except on the day the app's limits were first set. There's no emergency override; the daily phone limit has those.

Opening from STILL goes: blocked, daily phone limit, the app's own limits, task gates, the pause, launch delay, "How long?", open. Precision mode applies the same to apps opened any other way.

## The pause

"What are you opening it for?" with A specific purpose, An intentional break and Just checking. Open only appears after an answer, Go home (or Back to the focus session) is always the bigger button, and nothing opens on its own.

It asks always during a focus session, when the app has been opened 3 or more times in the last hour ("You've opened it 3 times since 13:00"), and otherwise at most once every 2 hours. Settings > Inner practice > Ask before limited apps turns every pause off.

Each pause and launch delay outcome is kept on the phone (the `choices` table): when, which app, the answer, and whether it opened or you turned back.

## Inner practice

Optional, under Settings > Inner practice; each part is on by default and has its own switch.

- **Daily quality**: on the first visit home each day from 4 AM, a card asks for one quality (Steadiness, Patience, Attention, Courage, Goodwill, or your own) and one concrete action, practised with a Today item, the action added as a task for today, or nothing in particular. "Not today" puts it off until tomorrow. Home then shows the quality with a mark for each time it was practised; tapping it lists the marks with their times, then phone time today, unlocks and limited apps' budgets. There's no score.
  - A mark: turning back at a pause or a launch delay (tries at one app less than 5 minutes apart count once), finishing the linked item, finishing a focus session (other than on the linked habit), and from 9 PM each app still within its daily budget.
- **Unlocks on home**: today's unlocks (Usage access keyguard events) against the median of the last 7 days by the same time of day, as fewer, about, or more than usual by now. "About" covers a difference of up to 15% (at least 2). With fewer than 3 days of history it shows just the count.
- **Ask before limited apps**: the pause above.

Qualities and choices live in Room (database version 8); limits and visits in DataStore. Nothing leaves the phone.

Added in stage 8 (2026-10-24):

- **This week** in Insights: turned back N of M times when STILL paused, unlocks a day against the week before (only with at least 3 days of history each), the qualities chosen with how many days, and tasks and habits finished. Lines with nothing to say are left out; there's still no score.
- **Quiet Minute**, from the pause before an app ("Take a quiet minute", which counts as turning back) and from today's quality: name what's here (or skip; nothing is kept), a minute with a breathing circle (in 4 s, out 6 s) and three gentle prompts, then One small step (the Today page), A real break (home) or Reach someone (the phone app). It never changes what opens.
- **Welcome back**: during a focus session, coming back to STILL after 30 seconds or more away shows how much is left and "What's the next small step?", with Back to it or End session.

Not built yet: Gentle Day, the reader.
