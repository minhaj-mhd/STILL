# Minimalist Phone → STILL feature parity checklist

Reference list of everything Minimalist Phone v1.22.6 (build 234) does, reconstructed from the APK's screens, UI text, settings keys and manifest. Use it to make sure STILL covers every feature. Descriptions are paraphrased; implement from public Android APIs, not from the APK's code.

Legend: **Keep** = build the same behaviour · **Adapt** = build a STILL version of it · **Skip** = not needed for STILL

STILL layout: **left page** = productivity (notes, tasks, habits) · **center** = home · **right page** = all apps + blocking/tracking. In Minimalist, swiping left from home opens the app list, which matches STILL's right page.

---

## 1. Home screen — Keep

Android: `HOME` intent filter, `RoleManager.ROLE_HOME`, `LauncherApps`

- [ ] Large clock with date; tapping the clock opens the alarm/timer app
- [ ] Ring around the clock showing remaining battery level
- [ ] Charging animation while plugged in (toggle)
- [ ] Favorite apps list, text only, no icons
- [ ] Friction when adding an "addictive" app to favorites: confirm dialog warning it will increase usage
- [ ] Folders on the home screen: create, rename, remove, move apps in/out, "(empty)" placeholder
- [ ] App shortcuts on the home screen: pinned shortcuts from other apps (`CONFIRM_PIN_SHORTCUT`), create/remove, duplicate detection, "(X shortcut)" label
- [ ] Optional quick-access buttons on home: phone dialer and camera (each toggleable, "button visible on home screen")
- [ ] Choose which app the dialer/camera/alarm shortcuts open ("use default app" or pick one)
- [ ] Home banners: missing-permission warning (e.g. Accessibility lost after update/reboot) with one-tap fix
- [ ] Seasonal "snowfall" effect (toggle)
- [ ] Works as non-default launcher too (separate "no home" entry activity)

## 2. Gestures — Keep

Android: Compose gesture detectors; status bar expand via `StatusBarManager`/reflection (`EXPAND_STATUS_BAR`); lock via `DevicePolicyManager` (STILL: prefer Accessibility `GLOBAL_ACTION_LOCK_SCREEN`)

- [ ] Swipe left → all apps page
- [ ] Swipe up → open search (toggle)
- [ ] Swipe down → expand notification shade (toggle; fallback message on phones that don't support it)
- [ ] Double-tap → lock screen (needs device admin in Minimalist)

## 3. All apps page (STILL right page) — Keep

Android: `LauncherApps.getActivityList`, `<queries>` for MAIN/LAUNCHER, package add/remove callbacks, `UserManager` for work profile

- [ ] Alphabetical text-only list of all launchable apps
- [ ] Fast scroller with letter indicator along the edge
- [ ] Search bar; optional auto-open keyboard when the page opens (with first-run prompt)
- [ ] Pressing Enter on the keyboard opens the top search result
- [ ] Search matches Japanese kana/kanji readings (WanaKana + kanji reading tables) — Adapt: transliteration for other scripts if wanted
- [ ] "Recently installed" section
- [ ] Work-profile apps shown with a "work" label
- [ ] Hidden apps excluded from the list
- [ ] Long-press context menu per app:
  - [ ] Add to / remove from favorites
  - [ ] Rename / restore original name
  - [ ] Hide / unhide
  - [ ] Move to folder / remove from folder
  - [ ] Block app (see §5)
  - [ ] In-app time reminder settings for this app (see §6)
  - [ ] Block short videos toggle (for supported apps, see §8)
  - [ ] App info (system settings page)
  - [ ] Uninstall (`REQUEST_DELETE_PACKAGES`)

## 4. Hidden apps — Keep

- [ ] Hidden apps folder reachable from settings/menu (extra friction, not removal)
- [ ] Empty-state hint explaining how to hide an app
- [ ] Unhide from the hidden apps screen

## 5. App blocking — Keep

Android: launcher interception + Accessibility foreground-app detection; `UsageStatsManager` for screen time

- [ ] Block a single app for a preset duration: 1h, 2h, 4h, 8h, 12h, 24h, 48h, 3d, 7d, 14d, 30d
- [ ] Block screen shows how long you spent on the app in the last 7 days, as motivation
- [ ] Suggest other apps to block, sorted by 7-day screen time ("also block")
- [ ] Slide-to-confirm control for starting a block (deliberate action)
- [ ] "App is blocked" screen when opening a blocked app, with "blocked until <date/time>"
- [ ] 7-day detox challenge prompt (block your most addictive apps)

## 6. Blocking schedules — Keep (STILL: build on the shared scheduling model)

- [ ] Multiple named schedules, list with on/off state
- [ ] Each schedule: start time, end time (supports overnight, "next day"), days of week
- [ ] Recurrence: every week, odd weeks, even weeks
- [ ] Choose apps to block, list sorted by 7-day screen time, with search
- [ ] Edit schedule with confirmation (friction) and save dialog
- [ ] Turn off: "just for today, turns back on tomorrow" or "permanently", with confirmation
- [ ] Templates during setup: "Mindful morning" (from wake-up time) and "No screen evening" (until bedtime)
- [ ] Onboarding asks wake-up and bedtime to create these
- [ ] Empty state with hint to add the first schedule

## 7. In-app time reminder — Keep

Android: foreground-app detection (Accessibility or usage-stats polling), overlay window (`SYSTEM_ALERT_WINDOW`) — STILL: let the Accessibility service show the UI where possible

- [ ] Apply to a chosen set of apps; on by default for games/social categories
- [ ] When opening a covered app: ask "how much time do you want to spend now?" with preset minutes + custom time
- [ ] Dialog shows time spent today and in the last 7 days
- [ ] Floating progress bar while using the app (hide for this session / hide always / turn off)
- [ ] "1 minute left" and "no time left" toasts
- [ ] When time is over, choose behaviour: only remind, or exit and block the app for N minutes
- [ ] Reminder screen with "take me out of here" (go home) and "more time"
- [ ] **Mindful launch delay**: a pause (≈15 s) before the app opens, "are you sure you want to open X?"
- [ ] **Mindful extension delay**: a pause before granting more time
- [ ] Ongoing notification while the reminder is active
- [ ] Xiaomi "autostart" permission guidance so the reminder keeps working

## 8. Short-video blocking — Keep (hardest to maintain)

Android: Accessibility service reading view IDs and browser URL bars; breaks when target apps update

- [ ] YouTube Shorts (also YouTube variants like ReVanced) — detect the Shorts player and leave/pause it
- [ ] Instagram Reels
- [ ] Facebook short videos
- [ ] The app also references Instagram Lite, Threads, Facebook Lite, Messenger, TikTok, Reddit, X/Twitter and WhatsApp packages, likely for its "addictive apps" defaults
- [ ] Snapchat Spotlight
- [ ] Short-video pages in browsers by reading the URL bar: Chrome, Brave, Firefox, Opera / Opera GX / Opera Mini, Samsung Internet, DuckDuckGo, Ecosia → redirect to a "blocked" page
- [ ] Toast "short videos blocked" when it intervenes
- [ ] Global setting + per-app toggle in the context menu

## 9. Precision mode (Accessibility) — Adapt

- [ ] Optional Accessibility service that makes foreground-app detection reliable (recommended on Android 14+)
- [ ] Required for short-video blocking
- [ ] Explicit consent screen explaining what data it reads and that it stays on device (Play policy requirement)
- [ ] Step-by-step guide to enable it, including "restricted settings" on sideloaded installs
- [ ] Detect when the permission is lost (after update/reboot) and prompt from home

## 10. Notification filter — Keep

Android: `NotificationListenerService`

- [ ] Allow-list of apps whose notifications get through
- [ ] Notifications from other apps are dismissed and stored in a "Filtered notifications" tab
- [ ] Clear all stored notifications (with confirmation)
- [ ] Ongoing status notification showing how many were cleared (caps at 99+)
- [ ] Option to also hide ongoing notifications (with warning about restart)
- [ ] Ongoing notifications are not filtered by default

## 11. Monochrome mode — Keep (advanced)

Android: toggles the system greyscale (daltonizer) secure setting; needs `WRITE_SECURE_SETTINGS` granted once over ADB

- [ ] Greyscale only while selected apps are in the foreground
- [ ] Choose which apps
- [ ] Setup instructions for the one-time ADB grant (Minimalist emails a link)
- [ ] Ongoing notification while active

## 12. Appearance — Adapt (STILL goes further: wallpapers, gradients, presets)

- [ ] Color themes: black, white, e-ink, follow system, custom color
- [ ] Custom theme: pick background color, light or dark text, contrast warning, recent custom themes
- [ ] Fonts: default, light, thin, medium, condensed, monospace, OpenDyslexic, Architect, Goldman, Orbitron
- [ ] Font size: small, medium, large, extra large
- [ ] Time format: 24h / AM-PM; date format (system or chosen)
- [ ] Automatically set a matching solid lock screen wallpaper (with confirmation)
- [ ] Respects system "animations off"

## 13. Settings structure — Keep the grouping

- [ ] Groups: Home screen · Display · Gestures · More
- [ ] Entry points for: blocking schedules, in-app reminder, notification filter, monochrome, precision mode, hidden apps, short-video blocking
- [ ] Show tutorial again
- [ ] About, version, third-party licenses
- [ ] FAQ link, contact support by email (prefilled subject per problem)
- [ ] "Exit" flow: choose another home screen or uninstall

## 14. Onboarding and permissions — Adapt

- [ ] Intro screens explaining the philosophy (text-only apps, endless feeds, notifications)
- [ ] Feature overview screen
- [ ] Permission setup flow with progress: usage access, overlay, notification access, post notifications, battery optimization exemption (Android 15+), Xiaomi autostart, Accessibility (optional)
- [ ] Set as default home screen with device-specific instructions (generic, Oppo, Poco/Xiaomi)
- [ ] Interactive tutorial hints: search an app, add a favorite, hide an app, block an app
- [ ] "Recommended apps" list offered when picking first favorites
- [ ] Gesture hints: swipe left = apps, up = search, down = notifications; tap clock = alarms; battery ring meaning
- [ ] Setup reminder notification if setup is not finished
- [ ] "Detox roadmap" screen (today → 3 months → 6 months)

## 15. Business features — Skip for STILL

Not needed for a local-first app; listed so nothing is forgotten.

- [ ] ~~Free vs full version limits (custom themes, favorites, renamed and hidden apps are capped in free)~~
- [ ] ~~Hard paywall, free trial, subscriptions, lifetime purchase, restore purchases, my payments, refunds~~
- [ ] ~~Email + one-time-password login to their backend~~
- [ ] ~~Remote config gating ("your experience is being prepared")~~
- [ ] ~~Referral / ambassador program, Product Hunt and review prompts, satisfaction survey, uninstall-reason survey~~
- [ ] ~~Mixpanel, Branch, Firebase analytics; developer menu and log requests~~

If STILL ever charges on Play, any paid features must use Google Play Billing.

---

## STILL additions (not in Minimalist)

Kept short; the full requirements live in `STILL_Productivity_Launcher_Spec_v2.md`.

- [ ] Left page: notes, tasks (one-time, recurring, subtasks, reminders), habits (yes/no, counts, durations, quotas, streaks), due/overdue dashboard
- [ ] Shared scheduling model used by tasks, habits and blocking schedules
- [ ] Task gates: block selected apps while tasks/habits are pending, by specific item or by priority level (High/Medium/Low) — design in [task-gated-blocking.md](task-gated-blocking.md)
- [ ] Daily usage budgets, session limits, cooldowns, focus sessions, emergency exceptions
- [ ] Long-term usage history stored locally (Room) and an Insights page
- [ ] Wallpapers, gradients, blur/dim, layouts, home modules, appearance presets
- [ ] Backup/export
