package app.still.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.still.launcher.data.AccentColor
import app.still.launcher.data.ClockWeight
import app.still.launcher.data.FontChoice
import app.still.launcher.data.TextSize
import app.still.launcher.data.TimeFormat
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.WallpaperChoice
import app.still.launcher.data.productivity.PlanColor

/** The user's look from Customise: accent, wallpaper, type and time. */
@Immutable
data class Appearance(
    val accent: AccentColor = AccentColor.TEAL,
    val wallpaper: WallpaperChoice = WallpaperChoice.BLACK,
    val wallpaperDim: Float = LauncherSettings.DEFAULT_WALLPAPER_DIM,
    val font: FontChoice = FontChoice.SYSTEM,
    val textSize: TextSize = TextSize.DEFAULT,
    val timeFormat: TimeFormat = TimeFormat.PHONE,
    val clockAmPm: Boolean = false,
    val clockWeight: ClockWeight = ClockWeight.LIGHT,
) {
    /** Paper is STILL's one light wallpaper; everything else is dark. */
    val isLight: Boolean get() = wallpaper == WallpaperChoice.PAPER
}

/** The look these settings give. */
fun LauncherSettings.appearance(): Appearance =
    Appearance(accent, wallpaper, wallpaperDim, font, textSize, timeFormat, clockAmPm, clockWeight)

/** The look of the [StillTheme] around the caller. */
val currentAppearance: Appearance
    @Composable
    @ReadOnlyComposable
    get() = LocalAppearance.current

/** STILL's colours beyond Material's: the accent of the rings and folders, and the tones for warnings and limits. */
@Immutable
data class StillPalette(
    val accent: Color = Color(0xFF5DCAA5),
    val warning: Color = Color(0xFFFAC775),
    val danger: Color = Color(0xFFF09595),
    /** Unlit ticks, empty segments and quiet outlines. */
    val faint: Color = Color(0x2EF1EFE8),
)

private val LocalStillPalette = staticCompositionLocalOf { StillPalette() }
private val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** The palette of the [StillTheme] around the caller. */
val stillPalette: StillPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalStillPalette.current

/** Each accent on a dark wallpaper and on a light one. */
private fun AccentColor.colors(): Pair<Color, Color> = when (this) {
    AccentColor.TEAL, AccentColor.WALLPAPER -> Color(0xFF5DCAA5) to Color(0xFF0F6E56)
    AccentColor.CORAL -> Color(0xFFF0997B) to Color(0xFF993C1D)
    AccentColor.VIOLET -> Color(0xFFAFA9EC) to Color(0xFF534AB7)
    AccentColor.AMBER -> Color(0xFFEF9F27) to Color(0xFF854F0B)
    AccentColor.STONE -> Color(0xFFD3D1C7) to Color(0xFF444441)
}

/** How an accent looks as a swatch in Customise, on today's wallpaper. */
@Composable
fun accentSwatch(accent: AccentColor, appearance: Appearance): Color = accentFor(accent, appearance.isLight)

@Composable
private fun accentFor(accent: AccentColor, light: Boolean): Color {
    if (accent == AccentColor.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (light) dynamicLightColorScheme(context).primary else dynamicDarkColorScheme(context).primary
    }
    val (dark, onLight) = accent.colors()
    return if (light) onLight else dark
}

/** A plan's colour on today's wallpaper: the accent of the same name. */
@Composable
@ReadOnlyComposable
fun planColor(color: PlanColor): Color {
    val accent = when (color) {
        PlanColor.TEAL -> AccentColor.TEAL
        PlanColor.VIOLET -> AccentColor.VIOLET
        PlanColor.CORAL -> AccentColor.CORAL
        PlanColor.AMBER -> AccentColor.AMBER
    }
    val (dark, onLight) = accent.colors()
    return if (LocalAppearance.current.isLight) onLight else dark
}

/** The colour of each of STILL's own wallpapers; the phone's wallpaper sits on black elsewhere. */
fun WallpaperChoice.baseColor(): Color = when (this) {
    WallpaperChoice.BLACK, WallpaperChoice.PHONE -> Color.Black
    WallpaperChoice.NIGHT -> Color(0xFF042C53)
    WallpaperChoice.FOREST -> Color(0xFF173404)
    WallpaperChoice.PAPER -> Color(0xFFF1EFE8)
    WallpaperChoice.SHAPES -> Color(0xFF26215C)
}

private fun darkScheme(background: Color): ColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color.Black,
    background = background,
    onBackground = Color.White,
    surface = lerp(background, Color.White, 0.07f),
    onSurface = Color.White,
    surfaceVariant = lerp(background, Color.White, 0.12f),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainerLow = lerp(background, Color.White, 0.07f),
    surfaceContainerHigh = lerp(background, Color.White, 0.12f),
    outline = Color(0xFF5C5C5C),
)

private val PaperScheme = lightColorScheme(
    primary = Color(0xFF2C2C2A),
    onPrimary = Color(0xFFF1EFE8),
    secondary = Color(0xFF5F5E5A),
    onSecondary = Color(0xFFF1EFE8),
    background = Color(0xFFF1EFE8),
    onBackground = Color(0xFF2C2C2A),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF2C2C2A),
    surfaceVariant = Color(0xFFE5E3DB),
    onSurfaceVariant = Color(0xFF5F5E5A),
    surfaceContainerLow = Color(0xFFFAF9F5),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFB4B2A9),
)

@Composable
fun StillTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val light = appearance.isLight
    val palette = StillPalette(
        accent = accentFor(appearance.accent, light),
        warning = if (light) Color(0xFF854F0B) else Color(0xFFFAC775),
        danger = if (light) Color(0xFFA32D2D) else Color(0xFFF09595),
        faint = if (light) Color(0x292C2C2A) else Color(0x2EF1EFE8),
    )
    val scheme = if (light) PaperScheme else darkScheme(appearance.wallpaper.baseColor())
    val typography = remember(appearance.font) { typographyIn(fontFamilyOf(appearance.font)) }
    // STILL's text size scales on top of Android's font size, as if the person had set it there.
    val density = LocalDensity.current
    val scaled = remember(density, appearance.textSize) { Density(density.density, density.fontScale * appearance.textSize.scale) }
    CompositionLocalProvider(LocalStillPalette provides palette, LocalAppearance provides appearance, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * What's behind the swipeable pages: a colour, the Shapes pattern, or the phone's own wallpaper
 * darkened by the dim setting so the rings and names stay readable. Screens on top keep their
 * own solid backgrounds.
 */
@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    when (appearance.wallpaper) {
        WallpaperChoice.PHONE -> Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = appearance.wallpaperDim)))
        WallpaperChoice.SHAPES -> Canvas(modifier.fillMaxSize().background(appearance.wallpaper.baseColor())) {
            drawCircle(Color(0xFF3C3489), radius = size.width * 0.55f, center = Offset(size.width * 0.9f, size.height * 0.86f))
            drawCircle(Color(0x80534AB7), radius = size.width * 0.26f, center = Offset(size.width * 0.08f, size.height * 0.78f))
            drawCircle(Color(0x403C3489), radius = size.width * 0.18f, center = Offset(size.width * 0.82f, size.height * 0.12f))
        }
        else -> Box(modifier.fillMaxSize().background(appearance.wallpaper.baseColor()))
    }
}
