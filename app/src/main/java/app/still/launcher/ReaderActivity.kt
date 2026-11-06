package app.still.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.still.launcher.data.ReadFont
import app.still.launcher.data.ReadText
import app.still.launcher.data.ReadTheme
import app.still.launcher.data.read.Book
import app.still.launcher.data.read.BookFormat
import app.still.launcher.read.ReadingClock
import app.still.launcher.read.isAtEnd
import app.still.launcher.read.pdfProgress
import app.still.launcher.ui.read.EpubControls
import app.still.launcher.ui.read.PdfReader
import app.still.launcher.ui.read.pageColor
import app.still.launcher.ui.theme.StillTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * STILL Read's reader for one book: a PDF drawn by Android's own renderer, or an EPUB shown by
 * Readium. It's part of STILL, so the daily limit doesn't count it and focus mode leaves it open.
 * Reading time counts while pages turn ([ReadingClock]); where you are is saved as you go, so the
 * reader always opens from that rather than from saved view state.
 */
class ReaderActivity : FragmentActivity() {

    private val container by lazy { (application as StillApplication).container }
    private val clock = ReadingClock()
    private var book: Book? = null
    private var publication: Publication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Nothing is restored: Readium's view can't come back without its book, and the place is in the database.
        super.onCreate(null)
        val root = FrameLayout(this)
        val pages = FragmentContainerView(this).apply { id = R.id.reader_fragment }
        val overlay = ComposeView(this)
        root.addView(pages, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        // Readium's pages stay clear of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(pages) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        lifecycleScope.launch {
            val loaded = container.readRepository.book(bookId)
            if (loaded == null) {
                finish()
                return@launch
            }
            book = loaded
            clock.activity(System.currentTimeMillis())
            when (loaded.format) {
                BookFormat.PDF -> showPdf(loaded, overlay)
                BookFormat.EPUB -> showEpub(loaded, root, overlay)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (book != null) clock.activity(System.currentTimeMillis())
    }

    override fun onPause() {
        super.onPause()
        OwnScreens.markLeft()
        val current = book ?: return
        val sitting = clock.stop(System.currentTimeMillis()) ?: return
        container.applicationScope.launch {
            container.readRepository.addReading(current.id, java.time.Instant.ofEpochMilli(sitting.startedAtMillis), sitting.millis)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
        publication = null
    }

    private suspend fun showPdf(book: Book, overlay: ComposeView) {
        val file = container.readRepository.fileOf(book)
        val startPage = book.position?.toIntOrNull() ?: 0
        val night = mutableStateOf(container.preferences.current().readPdfNight)
        overlay.setContent {
            StillTheme {
                var isNight by night
                PdfReader(
                    file = file,
                    title = book.title,
                    startPage = startPage,
                    night = isNight,
                    sittingMillis = { clock.millisSoFar(System.currentTimeMillis()) },
                    onNight = { on ->
                        isNight = on
                        container.applicationScope.launch { container.preferences.setReadPdfNight(on) }
                    },
                    onPage = { page, count ->
                        container.applicationScope.launch {
                            container.readRepository.savePosition(
                                book = book,
                                position = page.toString(),
                                label = getString(R.string.read_page_of, page + 1, count),
                                progress = pdfProgress(page, count),
                                atEnd = page == count - 1,
                            )
                        }
                    },
                    onActivity = { clock.activity(System.currentTimeMillis()) },
                    onBack = ::finish,
                )
            }
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private suspend fun showEpub(book: Book, root: FrameLayout, overlay: ComposeView) {
        val opened = container.publications.open(container.readRepository.fileOf(book))
        if (opened == null) {
            Toast.makeText(this, R.string.read_cannot_open, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        publication = opened
        var text by mutableStateOf(container.preferences.current().readText)
        var showControls by mutableStateOf(false)
        var locator by mutableStateOf<Locator?>(null)
        applyTheme(root, text.theme)

        val initial = book.position?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(opened).createFragmentFactory(
            initialLocator = initial,
            initialPreferences = text.toEpubPreferences(),
        )
        supportFragmentManager.commitNow { replace(R.id.reader_fragment, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG) }
        val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment

        // Taps near the edges turn the page; a tap in the middle shows or hides the controls.
        navigator.addInputListener(DirectionalNavigationAdapter(navigator))
        navigator.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    clock.activity(System.currentTimeMillis())
                    showControls = !showControls
                    return true
                }

                override fun onDrag(event: DragEvent): Boolean {
                    clock.activity(System.currentTimeMillis())
                    return false
                }
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { current ->
                    locator = current
                    clock.activity(System.currentTimeMillis())
                    val progress = current.locations.totalProgression ?: 0.0
                    container.applicationScope.launch {
                        container.readRepository.savePosition(book, current.toJSON().toString(), current.title, progress, isAtEnd(progress))
                    }
                }
            }
        }

        overlay.setContent {
            StillTheme {
                if (showControls) {
                    EpubControls(
                        title = book.title,
                        chapter = locator?.title,
                        progress = locator?.locations?.totalProgression ?: book.progress,
                        text = text,
                        sittingMillis = { clock.millisSoFar(System.currentTimeMillis()) },
                        onText = { changed ->
                            text = changed
                            applyTheme(root, changed.theme)
                            navigator.submitPreferences(changed.toEpubPreferences())
                            container.applicationScope.launch { container.preferences.setReadText(changed) }
                        },
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    /** The page colour behind the bars, and bar icons that show on it. */
    private fun applyTheme(root: FrameLayout, theme: ReadTheme) {
        root.setBackgroundColor(theme.pageColor().toArgb())
        val style = if (theme == ReadTheme.DARK) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        private const val EXTRA_BOOK_ID = "app.still.launcher.BOOK_ID"
        private const val NAVIGATOR_TAG = "navigator"

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}

/** STILL's text settings as Readium's. */
@OptIn(ExperimentalReadiumApi::class)
private fun ReadText.toEpubPreferences(): EpubPreferences = EpubPreferences(
    fontSize = scale.toDouble(),
    fontFamily = when (font) {
        ReadFont.SERIF -> FontFamily.SERIF
        ReadFont.SANS -> FontFamily.SANS_SERIF
        ReadFont.DYSLEXIC -> FontFamily.OPEN_DYSLEXIC
    },
    theme = when (theme) {
        ReadTheme.LIGHT -> Theme.LIGHT
        ReadTheme.SEPIA -> Theme.SEPIA
        ReadTheme.DARK -> Theme.DARK
    },
)
