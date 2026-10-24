package app.still.launcher.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.practice.ChoiceAnswer
import app.still.launcher.limit.Pause
import app.still.launcher.limit.PauseContext
import app.still.launcher.ui.LauncherApp
import app.still.launcher.ui.components.formatDuration
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.limit.appLimitSummary
import app.still.launcher.ui.theme.stillPalette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * "What are you opening it for?" before an app whose limits ask, now and then. Any answer is
 * fine; answering is what makes "Open" appear. Going home (or back to the focus session) is
 * always the bigger button, Back does the same, and nothing opens on its own.
 */
@Composable
fun WhyPauseScreen(
    app: LauncherApp,
    pause: Pause,
    /** Today's chosen quality, if there is one. */
    quality: String?,
    onTurnBack: (ChoiceAnswer?) -> Unit,
    onOpen: (ChoiceAnswer) -> Unit,
    /** A minute to settle instead of opening it. */
    onQuietMinute: (ChoiceAnswer?) -> Unit,
) {
    var answer by rememberSaveable { mutableStateOf<ChoiceAnswer?>(null) }
    BackHandler { onTurnBack(answer) }
    val visitMinutes = app.appLimit?.limit?.visitMinutes?.takeIf { it > 0 }

    val context = when (pause.context) {
        PauseContext.FOCUS -> pause.focus?.let { focus ->
            val minutes = ((System.currentTimeMillis() - focus.startMillis) / 60_000L).toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.pause_focus, minutes, minutes, focus.title)
        }
        PauseContext.OFTEN -> pause.firstOpenMillis?.let { first ->
            pluralStringResource(R.plurals.pause_often, pause.opensLastHour, pause.opensLastHour, formatTime(Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).toLocalTime()))
        }
        PauseContext.USUAL -> quality?.let { stringResource(R.string.pause_quality, it) }
    }
    val question = if (pause.context == PauseContext.OFTEN) stringResource(R.string.pause_question_often) else stringResource(R.string.pause_question, app.label)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = app.appLimit?.let { status ->
                    status.left?.let { stringResource(R.string.pause_used_of, app.label, formatDuration(status.usedToday), formatDuration(Duration.ofMinutes(status.limit.dailyMinutes.toLong()))) }
                        ?: "${app.label} · ${appLimitSummary(status.limit)}"
                } ?: app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            context?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = stillPalette.accent) }
            Text(question, style = MaterialTheme.typography.headlineSmall)
            val chosen = answer
            if (chosen == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ANSWERS.forEach { (option, label) ->
                        OutlinedButton(onClick = { answer = option }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(label), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                Text(
                    text = when (chosen) {
                        ChoiceAnswer.PURPOSE -> visitMinutes?.let { pluralStringResource(R.plurals.pause_reply_purpose_visit, it, it) } ?: stringResource(R.string.pause_reply_purpose)
                        ChoiceAnswer.BREAK -> visitMinutes?.let { pluralStringResource(R.plurals.pause_reply_break_visit, it, it) } ?: stringResource(R.string.pause_reply_break)
                        ChoiceAnswer.CHECKING -> stringResource(R.string.pause_reply_checking)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onTurnBack(answer) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                ) {
                    val focus = pause.focus
                    Text(if (pause.context == PauseContext.FOCUS && focus != null) stringResource(R.string.pause_back_to, focus.title) else stringResource(R.string.limit_go_home))
                }
                if (chosen != null) {
                    OutlinedButton(onClick = { onOpen(chosen) }) {
                        Text(if (chosen == ChoiceAnswer.CHECKING) stringResource(R.string.pause_open_anyway) else stringResource(R.string.delay_open, app.label))
                    }
                }
            }
            TextButton(onClick = { onQuietMinute(answer) }) { Text(stringResource(R.string.quiet_open)) }
        }
    }
}

private val ANSWERS = listOf(
    ChoiceAnswer.PURPOSE to R.string.pause_answer_purpose,
    ChoiceAnswer.BREAK to R.string.pause_answer_break,
    ChoiceAnswer.CHECKING to R.string.pause_answer_checking,
)
