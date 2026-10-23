# STILL

A productivity-first Android launcher. Design docs live in [`docs/`](docs): the [Minimalist Phone parity checklist](docs/minimalist-parity-checklist.md) and the [task-gates design](docs/task-gated-blocking.md).

## Stage 1 (this scaffold)

- Three pages you swipe between: **left** productivity (placeholder), **center** home, **right** all apps
- Home: clock and date (tap for alarms), favorite apps as text, banner until STILL is the default home app
- Apps: alphabetical text list, search (Enter opens the top match), work-profile label, hidden apps section
- Long-press any app: add to / remove from home, rename, hide / unhide, app info, uninstall
- Home button and Back always return to the home page

## Stack

Kotlin · Jetpack Compose (Material 3) · AndroidX ViewModel + Flow · DataStore Preferences · `LauncherApps` · AGP 9.4 (built-in Kotlin) · Gradle 9.6 · min SDK 28, target SDK 36, compile SDK 37

```
app/src/main/java/app/still/launcher/
├── StillApplication.kt, AppContainer.kt   app entry + manual dependency wiring
├── MainActivity.kt                        the HOME activity
├── data/                                  installed apps (LauncherApps) and user prefs (DataStore)
├── ui/                                    ViewModel, pager root, home / apps / productivity pages
└── util/DefaultLauncher.kt                default-home-app check and request
```

## Run it on your Pixel

1. Open this `STILL` folder in Android Studio and let Gradle sync.
2. On the phone, turn on Developer options → USB debugging, then connect it (or pair over Wi-Fi).
3. Press **Run**. STILL opens like a normal app.
4. Tap **Set as home app** on the home page, or go to Settings → Apps → Default apps → Home app → STILL.

To switch back, pick your previous launcher in the same Default apps screen.

## Build from the command line

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.
