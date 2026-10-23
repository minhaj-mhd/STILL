package app.still.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Stage 1 ships one black theme; colors, fonts and wallpapers become user settings later.
private val StillColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    outline = Color(0xFF5C5C5C),
)

@Composable
fun StillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StillColors, content = content)
}
