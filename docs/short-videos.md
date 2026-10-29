# Short videos

Stage 9, built 2026-10-24. The rules below are what the code does; change them here and in `shortvideo/ShortVideoRules.kt` together.

## What it does

Settings > Short videos explains the service and leads to Android's Accessibility settings, where the user turns on **STILL Short videos**. Once it's running:

- When YouTube Shorts or Instagram Reels fill the screen in the apps, STILL goes back, as the Back button does.
- In Chrome and Firefox, a Shorts or Reels page (youtube.com/shorts/…, instagram.com/reels/… or /reel/…) is swapped for the site's home page: STILL puts `https://m.youtube.com/` or `https://www.instagram.com/` in the address bar and presses Go (Android 11 and later). Going back alone could leave the page open in its tab for next time. If the address bar can't be used, it goes back instead.
- If the short video is still there, or leaving lands on another one, it tries once more, then goes home (2 exits in the last 4 seconds mean the next is home).
- It works however you get there: the tab, a shared link, a notification or a feed.
- "Short videos are blocked" shows as a toast the first time in each run of exits.
- Switches: YouTube Shorts and Instagram Reels (each covers the app and the site), and In Chrome and Firefox; all on by default.
- The rest of each app and site is left alone, including Shorts and Reels previews in feeds: in the apps the player only counts when it's visible and fills at least half the screen's height; on the web only the address counts, so a channel's Shorts grid (`/@name/shorts`) or a profile's Reels grid isn't blocked until a video is opened.

## Why a separate service

Precision mode promises it can't read the screen (`canRetrieveWindowContent="false"`), and its disclosure says so. Spotting a Shorts or Reels player or page needs window content, so short videos get an Accessibility service of their own, and people who only want Precision mode never grant screen access.

`android:packageNames` limits the events Android sends the service to these apps:

- `com.google.android.youtube`, `app.revanced.android.youtube`, `app.rvx.android.youtube`
- `com.instagram.android`
- `com.android.chrome`, `org.mozilla.firefox`

At runtime the service narrows that to the apps whose switches are on (`watchedPackages`); with everything off it's STILL alone, since an empty list would mean every app. The filter is on events: STILL only looks at the screen after an event from one of these apps, and only while one of them is in front. The service XML must list the packages in `SHORT_VIDEO_RULES` and `BROWSERS`; `ShortVideoRulesTest` checks.

## How it spots short videos

It listens for window-state changes, view scrolls and content changes, waits 250 ms after the first event in a burst, and then looks at the active window. In the apps, content changes that only change text or progress (as during playback) are ignored; in browsers they count, since a new address is a text change.

| Where | What it looks for | Notes |
|---|---|---|
| YouTube app | Views `reel_player_page_container`, `reel_watch_player` | Looked up under the running package and under `com.google.android.youtube`, as patched builds keep the original resource names |
| Instagram app | Views `clips_viewer_view_pager`, `clips_viewer_container` | |
| Chrome | The address in `url_bar` | Editable, so it's focused, given the new address and sent with the keyboard's Go |
| Chrome page, address bar hidden | Page elements whose own (HTML) id starts `clipsoverlay` on Instagram (seen on the phone 2026-10-26: the playing reel's overlay filled 0.79 of the screen, preloaded ones 0–0.05), `shorts-player` or `player-shorts-container` on YouTube (not yet seen) | Only while the address bar last showed YouTube or Instagram, at most once a second, filling at least half the screen; leaves with Back |
| Firefox | The address in `mozac_browser_toolbar_url_view` | Tapped to open `mozac_browser_toolbar_edit_url_view`; these ids may be gone in Firefox's newer toolbar, not yet checked on a phone |

Chrome hides its address bar as you scroll, and both sites move to Shorts and Reels without loading a new page, so the address alone missed them in testing (2026-10-25). Chrome passes each page element's own id on as its view id, which is what the page check reads.

An address is read with or without `https://`: its host (subdomains count) and the first part of its path must match, so `google.com/search?q=youtube.com/shorts` and `m.youtube.com/watch?v=…` are left alone. While the address bar is being typed in, it's ignored.

In the apps it reads only view ids and sizes; in the browsers only the address bar. It keeps nothing.

Debug builds note what they see in logcat under the tag `StillShortVideos` and in `files/short-videos-trace.txt` (read with `adb shell run-as app.still.launcher cat files/short-videos-trace.txt`; the system log only reaches back a few minutes). They note visible ids that look like a short-video player (`reel`, `clips`, `short`), in browsers the site and first part of the path on YouTube and Instagram only (never a full address, nor any other site), the ids that look like an address bar when none is found, and each exit. These ids come from the apps as they are now and are expected to break when YouTube, Instagram or the browsers change their layouts; the Settings page says so.

When any Accessibility service that reads window content is on, Chrome builds its accessibility tree for web pages, which can make pages a little slower. That comes with turning the service on, whatever STILL reads.

## Google Play

The Accessibility API declaration needs a second entry for this service. Draft:

- **Service**: STILL Short videos (`app.still.launcher.shortvideo.ShortVideoService`), not an accessibility tool (`isAccessibilityTool="false"`).
- **What it does**: Blocks YouTube Shorts and Instagram Reels on request, in those apps and in Chrome and Firefox. When the user opens one, the service goes back in the app, or opens the site's home page in the browser, so they stay in the rest of the app or site.
- **Data accessed**: Events from YouTube, Instagram, Chrome and Firefox only, restricted with `packageNames`. In YouTube and Instagram, the view hierarchy (view ids and on-screen size). In Chrome and Firefox, the address shown in the address bar, and on YouTube and Instagram pages the ids the page gives its elements, to recognise Shorts and Reels pages; page text and what the user types are not read. Nothing is stored, and nothing leaves the device.
- **Actions**: Back, Home, and in the browsers focusing the address bar, replacing its text with the site's home page and pressing Go.
- **Disclosure and consent**: Settings > Short videos explains this in the app (including that it reads the web address in Chrome and Firefox) and asks the user to "Agree and turn on" before opening Android's Accessibility settings.

Precision mode's declaration is unchanged.

## Not built yet

Facebook and Snapchat short videos, browsers other than Chrome and Firefox (Brave, Edge, Samsung Internet, Opera, DuckDuckGo), and a long-press menu switch per app.
