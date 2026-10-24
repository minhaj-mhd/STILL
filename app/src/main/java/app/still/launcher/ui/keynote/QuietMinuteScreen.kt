package app.still.launcher.ui.keynote

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.theme.stillPalette
import kotlinx.coroutines.delay

/** What the Quiet Minute ends with. */
enum class QuietChoice {
    /** Back to one small thing: the Today page. */
    SMALL_STEP,

    /** A real break: home, nothing else. */
    BREAK,

    /** Reach someone: the phone app. */
    REACH_SOMEONE,
}

private enum class QuietStep { NOTICE, SETTLE, CHOOSE }

/** What might be here, to name without fixing it. Nothing is kept. */
private val FEELINGS = listOf(
    R.string.quiet_restless,
    R.string.quiet_worried,
    R.string.quiet_irritated,
    R.string.quiet_tired,
    R.string.quiet_unsure,
)

private val SETTLE_PROMPTS = listOf(R.string.quiet_prompt_feet, R.string.quiet_prompt_notice, R.string.quiet_prompt_breath)

/**
 * A minute for a hard moment: notice what's here, settle with the breathing circle for a minute,
 * then choose what would help. Nothing is recorded, and it never changes what opens.
 */
@Composable
fun QuietMinuteScreen(onChoose: (QuietChoice) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var step by rememberSaveable { mutableStateOf(QuietStep.NOTICE) }
    var feeling by rememberSaveable { mutableStateOf<Int?>(null) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Text(stringResource(R.string.quiet_title), style = MaterialTheme.typography.titleMedium, color = stillPalette.accent)
            when (step) {
                QuietStep.NOTICE -> {
                    Text(stringResource(R.string.quiet_notice), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    ChipRow {
                        FEELINGS.forEach { label ->
                            Chip(stringResource(label), selected = false) {
                                feeling = label
                                step = QuietStep.SETTLE
                            }
                        }
                    }
                    TextButton(onClick = { step = QuietStep.SETTLE }) { Text(stringResource(R.string.quiet_skip)) }
                }
                QuietStep.SETTLE -> Settle(feeling, onDone = { step = QuietStep.CHOOSE })
                QuietStep.CHOOSE -> {
                    Text(stringResource(R.string.quiet_choose), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        text = stringResource(R.string.quiet_choose_line),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceButton(R.string.quiet_small_step) { onChoose(QuietChoice.SMALL_STEP) }
                        ChoiceButton(R.string.quiet_break) { onChoose(QuietChoice.BREAK) }
                        ChoiceButton(R.string.quiet_reach) { onChoose(QuietChoice.REACH_SOMEONE) }
                    }
                }
            }
        }
    }
}

/** A minute with the breathing circle: in for 4 seconds, out for 6, with a gentle prompt now and then. */
@Composable
private fun Settle(@StringRes feeling: Int?, onDone: () -> Unit) {
    var secondsLeft by rememberSaveable { mutableIntStateOf(SETTLE_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onDone()
    }
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = MIN_SCALE,
        targetValue = MIN_SCALE,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BREATH_MILLIS
                MIN_SCALE at 0 using FastOutSlowInEasing
                1f at INHALE_MILLIS using FastOutSlowInEasing
                MIN_SCALE at BREATH_MILLIS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath scale",
    )
    val accent = stillPalette.accent
    val faint = stillPalette.faint
    val elapsed = SETTLE_SECONDS - secondsLeft
    val prompt = SETTLE_PROMPTS[(elapsed / PROMPT_SECONDS).coerceAtMost(SETTLE_PROMPTS.lastIndex)]

    feeling?.let { Text(stringResource(R.string.quiet_feeling_is_here, stringResource(it)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(CIRCLE_SIZE)) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 3.dp.toPx()
            // The minute, as a ring filling around the breath.
            drawArc(faint, 0f, 360f, false, topLeft = Offset(width, width), size = Size(size.width - 2 * width, size.height - 2 * width), style = Stroke(width))
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * elapsed / SETTLE_SECONDS,
                useCenter = false,
                topLeft = Offset(width, width),
                size = Size(size.width - 2 * width, size.height - 2 * width),
                style = Stroke(width, cap = StrokeCap.Round),
            )
            drawCircle(accent.copy(alpha = 0.25f), radius = size.minDimension / 2 * 0.72f * scale)
        }
    }
    Text(stringResource(prompt), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    TextButton(onClick = onDone) { Text(stringResource(R.string.quiet_settled)) }
}

@Composable
private fun ChoiceButton(@StringRes label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label), modifier = Modifier.fillMaxWidth())
    }
}

private const val SETTLE_SECONDS = 60
private const val PROMPT_SECONDS = 20
private const val INHALE_MILLIS = 4_000
private const val BREATH_MILLIS = 10_000
private const val MIN_SCALE = 0.55f
private val CIRCLE_SIZE = 200.dp
