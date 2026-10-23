package app.still.launcher.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.AccentColor
import app.still.launcher.data.AppDisplay
import app.still.launcher.data.CornerButton
import app.still.launcher.data.FolderOpening
import app.still.launcher.data.FolderStyle
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.WallpaperChoice
import app.still.launcher.ui.HomeButton
import app.still.launcher.ui.LauncherUiState
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.theme.accentSwatch
import app.still.launcher.ui.theme.baseColor
import app.still.launcher.ui.theme.stillPalette

/**
 * Settings > Customise: wallpaper (STILL's colours, a pattern, or the phone's own, dimmed),
 * accent, how apps show, how folders look and open, and what home's corner buttons open.
 * Changes apply at once.
 */
@Composable
fun CustomisePage(onBack: () -> Unit, viewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory)) {
    val context = LocalContext.current
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The corner button whose app is being chosen.
    var picking by remember { mutableStateOf<CornerButton?>(null) }
    BackHandler(onBack = onBack)

    EditorScaffold(title = stringResource(R.string.customise_title), onDone = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(stringResource(R.string.customise_wallpaper))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WallpaperChoice.entries.forEach { wallpaper ->
                    Swatch(
                        color = wallpaper.baseColor(),
                        label = stringResource(wallpaper.label()),
                        selected = appearance.wallpaper == wallpaper,
                        phone = wallpaper == WallpaperChoice.PHONE,
                    ) { viewModel.setWallpaper(wallpaper) }
                }
            }
            if (appearance.wallpaper == WallpaperChoice.PHONE) {
                Text(stringResource(R.string.customise_dim), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = appearance.wallpaperDim,
                    onValueChange = viewModel::setWallpaperDim,
                    valueRange = 0f..LauncherSettings.MAX_WALLPAPER_DIM,
                )
                TextButton(onClick = { context.startActivity(viewModel.wallpaperPickerIntent()) }) {
                    Text(stringResource(R.string.customise_change_wallpaper))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(stringResource(R.string.customise_accent))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries
                    .filter { it != AccentColor.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                    .forEach { accent ->
                        Swatch(
                            color = accentSwatch(accent, appearance),
                            label = stringResource(accent.label()),
                            selected = appearance.accent == accent,
                        ) { viewModel.setAccent(accent) }
                    }
            }
        }

        Column {
            FieldLabel(stringResource(R.string.customise_apps_shown_as))
            ChipRow {
                AppDisplay.entries.forEach { display ->
                    Chip(stringResource(display.label()), state.appDisplay == display) { viewModel.setAppDisplay(display) }
                }
            }
        }

        Column {
            FieldLabel(stringResource(R.string.customise_folder_style))
            ChipRow {
                FolderStyle.entries.forEach { style ->
                    Chip(stringResource(style.label()), state.folderStyle == style) { viewModel.setFolderStyle(style) }
                }
            }
        }

        Column {
            FieldLabel(stringResource(R.string.customise_folder_opening))
            ChipRow {
                FolderOpening.entries.forEach { opening ->
                    Chip(stringResource(opening.label()), state.folderOpening == opening) { viewModel.setFolderOpening(opening) }
                }
            }
            Text(
                text = stringResource(R.string.customise_opening_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            FieldLabel(stringResource(R.string.customise_corner_buttons))
            CornerButton.entries.forEach { button ->
                NavigationRow(title = stringResource(button.label()), value = cornerValue(state.homeButton(button))) { picking = button }
            }
        }
    }

    picking?.let { button ->
        CornerButtonSheet(
            title = stringResource(button.title()),
            button = state.homeButton(button),
            apps = state.apps,
            onChoose = { choice ->
                viewModel.setCornerButton(button, choice)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private fun LauncherUiState.homeButton(button: CornerButton): HomeButton = when (button) {
    CornerButton.PHONE -> phoneButton
    CornerButton.CAMERA -> cameraButton
}

/** What a corner button opens, as its row says it: off, a chosen app, or the phone's own one. */
@Composable
private fun cornerValue(button: HomeButton): String {
    val chosen = button.app?.takeIf { button.choice.appKey == it.key }
    return when {
        !button.choice.on -> stringResource(R.string.corner_off)
        chosen != null -> chosen.label
        button.defaultApp != null -> stringResource(R.string.corner_default_app, button.defaultApp.label)
        else -> stringResource(R.string.corner_choose)
    }
}

private fun CornerButton.label(): Int = when (this) {
    CornerButton.PHONE -> R.string.corner_phone
    CornerButton.CAMERA -> R.string.corner_camera
}

private fun CornerButton.title(): Int = when (this) {
    CornerButton.PHONE -> R.string.corner_phone_title
    CornerButton.CAMERA -> R.string.corner_camera_title
}

/** A round colour to pick, its name under it; the phone's wallpaper shows as an outlined circle. */
@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, phone: Boolean = false, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.onBackground else stillPalette.faint
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp).clickable(onClick = onClick)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
                .then(if (phone) Modifier else Modifier.background(color, CircleShape)),
        ) {
            if (phone) Text("◐", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun WallpaperChoice.label(): Int = when (this) {
    WallpaperChoice.BLACK -> R.string.wallpaper_black
    WallpaperChoice.NIGHT -> R.string.wallpaper_night
    WallpaperChoice.FOREST -> R.string.wallpaper_forest
    WallpaperChoice.PAPER -> R.string.wallpaper_paper
    WallpaperChoice.SHAPES -> R.string.wallpaper_shapes
    WallpaperChoice.PHONE -> R.string.wallpaper_phone
}

private fun AccentColor.label(): Int = when (this) {
    AccentColor.TEAL -> R.string.accent_teal
    AccentColor.CORAL -> R.string.accent_coral
    AccentColor.VIOLET -> R.string.accent_violet
    AccentColor.AMBER -> R.string.accent_amber
    AccentColor.STONE -> R.string.accent_stone
    AccentColor.WALLPAPER -> R.string.accent_wallpaper
}

private fun AppDisplay.label(): Int = when (this) {
    AppDisplay.TEXT -> R.string.display_text
    AppDisplay.ICON_AND_NAME -> R.string.display_icon_and_name
    AppDisplay.ICONS_ONLY -> R.string.display_icons_only
}

private fun FolderStyle.label(): Int = when (this) {
    FolderStyle.STRIP -> R.string.folder_style_strip
    FolderStyle.TABBED -> R.string.folder_style_tabbed
    FolderStyle.PAPER_STACK -> R.string.folder_style_paper
    FolderStyle.ORBIT_DOTS -> R.string.folder_style_dots
}

private fun FolderOpening.label(): Int = when (this) {
    FolderOpening.SPOTLIGHT -> R.string.opening_spotlight
    FolderOpening.DROP -> R.string.opening_drop
    FolderOpening.BLOOM -> R.string.opening_bloom
    FolderOpening.CASCADE -> R.string.opening_cascade
    FolderOpening.SHELF -> R.string.opening_shelf
    FolderOpening.ORBIT -> R.string.opening_orbit
}
