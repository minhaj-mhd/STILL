package app.still.launcher.data

/** The accent of the rings, folders and highlights; [WALLPAPER] takes it from the phone's wallpaper (Android 12+). */
enum class AccentColor { TEAL, CORAL, VIOLET, AMBER, STONE, WALLPAPER }

/** What's behind home: one of STILL's colours, a pattern, or the phone's own wallpaper. */
enum class WallpaperChoice { BLACK, NIGHT, FOREST, PAPER, SHAPES, PHONE }

/**
 * STILL's typeface: the phone's own, or one of a few from Google Fonts, which Play services
 * downloads and keeps for every app that uses it. Scripts a font lacks, such as Malayalam, keep
 * the phone's own fonts.
 */
enum class FontChoice(val googleName: String?) {
    SYSTEM(null),
    INTER("Inter"),
    MANROPE("Manrope"),
    SPACE_GROTESK("Space Grotesk"),
    LORA("Lora"),
    JETBRAINS_MONO("JetBrains Mono"),
}

/** STILL's text size, on top of Android's own font size. */
enum class TextSize(val scale: Float) { SMALLER(0.9f), DEFAULT(1f), LARGER(1.15f), LARGEST(1.3f) }

/** 12- or 24-hour time in STILL; [PHONE] follows Android's own setting. */
enum class TimeFormat { PHONE, H24, H12 }

/** How heavy the home clock's digits are. */
enum class ClockWeight { THIN, LIGHT, REGULAR }
