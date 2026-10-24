package app.still.launcher.focusmode

import app.still.launcher.data.CornerChoice

/** The package an app key ("package/activity#profile") belongs to. */
fun packageOfKey(key: String): String = key.substringBefore('/')

/**
 * The packages focus mode lets open: every app reachable from home ([homeKeys], the favourites
 * and the apps in folders, as app keys), the apps the corner buttons open, and [alwaysOpen].
 */
fun focusAllowlist(homeKeys: Collection<String>, cornerPackages: Collection<String?>, alwaysOpen: Collection<String?>): Set<String> =
    buildSet {
        homeKeys.mapTo(this, ::packageOfKey)
        cornerPackages.filterNotNullTo(this)
        alwaysOpen.filterNotNullTo(this)
    }

/** The package a corner button opens: none when it's off, the chosen app, or else [defaultPackage]. */
fun cornerPackage(choice: CornerChoice, defaultPackage: String?): String? = when {
    !choice.on -> null
    choice.appKey != null -> packageOfKey(choice.appKey)
    else -> defaultPackage
}

/** How long the ring has to be held: [START_HOLD_MILLIS] to start focus mode, [END_HOLD_MILLIS] to end it. */
fun holdMillisFor(focusOn: Boolean): Long = if (focusOn) END_HOLD_MILLIS else START_HOLD_MILLIS

/** Long enough that brushing the ring doesn't start it, as ending it takes much longer. */
const val START_HOLD_MILLIS = 2_000L
const val END_HOLD_MILLIS = 30_000L
