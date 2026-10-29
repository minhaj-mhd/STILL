# Focus mode

Stage 6, built 2026-10-24 after mockups (the Frame look). The rules live in `focusmode/FocusMode.kt` and `FocusModeMonitor`.

- **Start**: hold the clock ring on home for 2 seconds; the ring fills as you hold, and a tap still opens the alarms. Without Precision mode running, STILL says that apps opened from Recents or notifications get past it.
- **End**: hold the ring for 30 seconds, with "Keep holding · 24s" inside it. Letting go starts the count again.
- **What opens**: every app reachable from home (favourites, apps in folders, the corner buttons' apps), STILL, the phone and clock apps (emergency calls, alarms), and Android's own screens apps open ("Open with", the share sheet, permission and install prompts, the file picker, the in-call screen; `EssentialApps.isSystemScreen`, which blocks, gates and the daily limit leave open too). Launchers are left alone so Recents and the home gesture work, but any other app opened from Recents is covered. So curating home is choosing the allowlist.
- **Everything else is locked**, Settings included, whether opened from STILL, Recents, a notification or a link. Precision mode checks focus mode before anything else; the app's own limits, blocks and gates still apply to apps on home.
- **Home**: an accent frame around the whole screen and "Focus mode · 42m" under the date. The app list page goes, and Insights (swipe up), held-back notifications, Customise (long-press), swipe down, app and folder menus, and reordering are off. The left page (Today, Notes) stays.
- It's saved, so it stays on through a restart until the ring is held to end it.

Not possible: clearing Recents. Android doesn't let an app remove other apps' tasks, and tapping "Clear all" through Precision mode would mean reading the screen.
