package app.still.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.still.launcher.R
import app.still.launcher.data.ClockWeight
import app.still.launcher.data.FontChoice

/** Google Fonts through Play services, which checks it's talking to Google with these certificates. */
private val GoogleFonts = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.play_services_font_certs,
)

/** The weights each font comes in, so asking for another picks the nearest rather than the phone's font. */
private fun FontChoice.weights(): List<Int> = when (this) {
    FontChoice.SYSTEM -> emptyList()
    FontChoice.INTER, FontChoice.JETBRAINS_MONO -> listOf(100, 200, 300, 400, 500, 600)
    FontChoice.MANROPE -> listOf(200, 300, 400, 500, 600)
    FontChoice.SPACE_GROTESK -> listOf(300, 400, 500, 600)
    FontChoice.LORA -> listOf(400, 500, 600)
}

private val families = HashMap<FontChoice, FontFamily>()

/**
 * The font family for [choice]. Until Play services has a Google font on the phone, and if it
 * can't get one, text shows in the phone's own font.
 */
fun fontFamilyOf(choice: FontChoice): FontFamily {
    val name = choice.googleName ?: return FontFamily.Default
    return families.getOrPut(choice) {
        val font = GoogleFont(name)
        FontFamily(choice.weights().map { weight -> Font(googleFont = font, fontProvider = GoogleFonts, weight = FontWeight(weight)) })
    }
}

/** Material's type scale in [family]. */
fun typographyIn(family: FontFamily): Typography {
    val base = Typography()
    if (family == FontFamily.Default) return base
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/** The home clock's weight. */
fun ClockWeight.fontWeight(): FontWeight = when (this) {
    ClockWeight.THIN -> FontWeight.Thin
    ClockWeight.LIGHT -> FontWeight.Light
    ClockWeight.REGULAR -> FontWeight.Normal
}
