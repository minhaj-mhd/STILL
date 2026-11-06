package app.still.launcher.ui.read

import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.ui.components.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt

/** Android's own PDF renderer over one file. It draws one page at a time, so drawing takes turns. */
private class PdfDocument(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Page [index] drawn [widthPx] wide on white, or null once closed. */
    suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock null
            renderer.openPage(index).use { page ->
                val width = widthPx.coerceAtLeast(1)
                val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(width, height)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }

    suspend fun close() = mutex.withLock {
        if (closed) return@withLock
        closed = true
        renderer.close()
        descriptor.close()
    }
}

/** Light on dark: each colour turned around, as for reading at night. */
private val NIGHT = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Closes documents once their reader has gone. */
private val closer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Past this, the page is drawn again at twice the width so text stays sharp. */
private const val SHARPER_ZOOM = 1.6f

/**
 * A PDF a page at a time: swipe to turn, pinch or double-tap to zoom, tap for the controls. The
 * page you're on is passed to [onPage]; anything you do there is passed to [onActivity] so
 * reading time counts.
 */
@Composable
fun PdfReader(
    file: File,
    title: String,
    startPage: Int,
    night: Boolean,
    sittingMillis: () -> Long,
    onNight: (Boolean) -> Unit,
    onPage: (page: Int, pageCount: Int) -> Unit,
    onActivity: () -> Unit,
    onBack: () -> Unit,
) {
    val document = remember(file) { runCatching { PdfDocument(file) }.getOrNull() }
    DisposableEffect(document) {
        // Closing waits for a page being drawn, so it happens off the main thread.
        onDispose { document?.let { closer.launch { it.close() } } }
    }
    if (document == null || document.pageCount == 0) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.read_cannot_open), color = Color.White)
        }
        return
    }
    val count = document.pageCount
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, count - 1)) { count }
    var controls by remember { mutableStateOf(false) }
    var zoomedPage by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPage(page, count)
            onActivity()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = zoomedPage != pagerState.currentPage,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                ZoomablePage(
                    document = document,
                    index = index,
                    widthPx = widthPx,
                    night = night,
                    onTap = {
                        controls = !controls
                        onActivity()
                    },
                    onZoom = { zoomed ->
                        zoomedPage = if (zoomed) index else zoomedPage.takeIf { it != index }
                        onActivity()
                    },
                )
            }
        }

        if (controls) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onBack) { Text("‹", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.read_page_count, pagerState.currentPage + 1, count),
                    color = Color(0xFF9E9E9E),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            var sliderPage by remember(pagerState.currentPage) { mutableFloatStateOf(pagerState.currentPage.toFloat()) }
            var sitting by remember { mutableLongStateOf(sittingMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    sitting = sittingMillis()
                    delay(SITTING_TICK_MILLIS)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (count > 1) {
                    Slider(
                        value = sliderPage,
                        onValueChange = { sliderPage = it },
                        valueRange = 0f..(count - 1).toFloat(),
                        onValueChangeFinished = {
                            scope.launch { pagerState.scrollToPage(sliderPage.roundToInt()) }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.read_sitting, formatDuration(Duration.ofMillis(sitting))),
                        color = Color(0xFF9E9E9E),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(if (night) R.string.read_night_on else R.string.read_night_off),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clickable { onNight(!night) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

/** One page, fitted to the width; pinch or double-tap to zoom, drag to move about while zoomed. */
@Composable
private fun ZoomablePage(
    document: PdfDocument,
    index: Int,
    widthPx: Int,
    night: Boolean,
    onTap: () -> Unit,
    onZoom: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val sharper = scale > SHARPER_ZOOM
    val bitmap by produceState<ImageBitmap?>(null, document, index, widthPx, sharper) {
        value = document.render(index, if (sharper) widthPx * 2 else widthPx) ?: value
    }

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = size.width * (zoom - 1) / 2
        val maxY = size.height * (zoom - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val zoom = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = clamp(offset + panChange, zoom)
        if ((zoom > 1f) != (scale > 1f)) onZoom(zoom > 1f)
        scale = zoom
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val zoom = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        offset = Offset.Zero
                        if ((zoom > 1f) != (scale > 1f)) onZoom(zoom > 1f)
                        scale = zoom
                    },
                )
            }
            .transformable(transform, canPan = { scale > 1f }),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.read_page_description, index + 1),
                colorFilter = if (night) NIGHT else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}

private const val SITTING_TICK_MILLIS = 30_000L
