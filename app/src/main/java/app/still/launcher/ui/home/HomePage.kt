package app.still.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.launcher.R
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.AppLabel
import app.still.launcher.ui.components.rememberCurrentMinute
import java.time.format.DateTimeFormatter

@Composable
fun HomePage(
    favorites: List<LauncherApp>,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    onClockClick: () -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        if (!isDefaultLauncher) {
            NotDefaultLauncherBanner(onSetDefaultLauncher)
            Spacer(Modifier.height(24.dp))
        }
        Clock(onClick = onClockClick)
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(favorites, key = { it.key }) { app ->
                        AppLabel(
                            app = app,
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongPress(app) },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Clock(onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val now by rememberCurrentMinute()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(timePattern, locale)),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern(datePattern, locale)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotDefaultLauncherBanner(onSetDefaultLauncher: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.home_not_default_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onSetDefaultLauncher, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_not_default_action))
            }
        }
    }
}
