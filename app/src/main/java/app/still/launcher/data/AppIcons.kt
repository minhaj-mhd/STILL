package app.still.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** A one-colour picture of an app. */
sealed interface MonoIcon {
    val bitmap: ImageBitmap

    /** The logo's shape, to draw tinted with the theme. */
    data class Mask(override val bitmap: ImageBitmap) : MonoIcon

    /** A grey version of the whole icon, for logos that can't be lifted off their background. */
    data class Grey(override val bitmap: ImageBitmap) : MonoIcon
}

/**
 * One-colour app icons from each app's own logo. Apps that ship a monochrome layer for Android
 * 13's themed icons use it; others get their adaptive icon's logo layer as a shape to tint;
 * and the few whose logo fills the whole icon are shown in grey.
 */
class AppIcons(context: Context) {

    private val launcherApps = context.applicationContext.getSystemService(LauncherApps::class.java)

    // Icons only change with an app update, and a home screen holds few apps; keep them all.
    private val cache = ConcurrentHashMap<String, Any>()

    /** The app's one-colour icon, [sizePx] square, or null when its icon can't be read. */
    suspend fun monochrome(component: ComponentName, user: UserHandle, sizePx: Int): MonoIcon? {
        val key = "${component.flattenToShortString()}#${user.hashCode()}#$sizePx"
        cache[key]?.let { return it as? MonoIcon }
        val icon = withContext(Dispatchers.IO) { render(component, user, sizePx) }
        cache[key] = icon ?: NONE
        return icon
    }

    private fun render(component: ComponentName, user: UserHandle, sizePx: Int): MonoIcon? {
        val info = launcherApps.resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(component), user) ?: return null
        val icon = info.getIcon(0) ?: return null
        if (icon is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                icon.monochrome?.let { return MonoIcon.Mask(drawLayer(it, sizePx).asImageBitmap()) }
            }
            icon.foreground?.let { foreground ->
                val logo = drawLayer(foreground, sizePx)
                if (coverage(logo) < MAX_LOGO_COVERAGE) return MonoIcon.Mask(logo.asImageBitmap())
            }
        }
        return MonoIcon.Grey(drawGrey(icon, sizePx).asImageBitmap())
    }

    /**
     * An adaptive icon's layers are 108 units with the visible part in the middle 72, so a layer
     * is drawn half again as large as the icon, centred.
     */
    private fun drawLayer(layer: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val extra = sizePx / 4
        layer.mutate().setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
        layer.draw(Canvas(bitmap))
        return bitmap
    }

    private fun drawGrey(icon: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val grey = icon.mutate()
        grey.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        grey.setBounds(0, 0, sizePx, sizePx)
        grey.draw(Canvas(bitmap))
        return bitmap
    }

    /** The share of the picture that's solid; a logo layer that fills the icon isn't a logo shape. */
    private fun coverage(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it ushr 24 > SOLID_ALPHA }.toFloat() / pixels.size
    }

    private companion object {
        /** Cached for icons that can't be read, so they aren't looked up again. */
        val NONE = Any()
        const val MAX_LOGO_COVERAGE = 0.6f
        const val SOLID_ALPHA = 128
    }
}
