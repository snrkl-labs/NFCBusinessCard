package org.snrkl.nfcbusinesscard.ui.qr

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import org.snrkl.nfcbusinesscard.qr.QrEncoder
import org.snrkl.nfcbusinesscard.ui.theme.QrBackgroundColor
import org.snrkl.nfcbusinesscard.ui.theme.QrModuleColor

/**
 * Turns encoded QR modules into a bitmap.
 *
 * The one thing that matters here is that module edges stay hard. A QR decoder
 * thresholds luminance, and a soft edge is a row of intermediate greys the
 * decoder has to guess at. So the bitmap is built at an exact integer multiple
 * of the module grid and drawn with no filtering — never scaled to an arbitrary
 * view size and never interpolated.
 */
internal object QrBitmapRenderer {

    /**
     * Renders [content] to a bitmap that fits within [targetSizePx].
     *
     * The result is usually *smaller* than the target, because the module size
     * is floored to a whole number of pixels. Drawing a 33-module code into 500
     * pixels means 15px modules and a 495px bitmap; stretching that last 5px
     * across the code would put a half-pixel seam in every module. The view
     * centres the slightly smaller bitmap instead.
     */
    fun render(content: String, targetSizePx: Int): ImageBitmap? {
        if (targetSizePx <= 0) return null
        val matrix = QrEncoder.encode(content) ?: return null

        val modules = matrix.width
        if (modules <= 0) return null

        val moduleSizePx = targetSizePx / modules
        if (moduleSizePx < 1) {
            // The view is too small to give each module even one pixel. Better
            // to draw nothing than a code that provably cannot be decoded.
            return null
        }

        val sizePx = moduleSizePx * modules
        val onColor = QrModuleColor.toArgb()
        val offColor = QrBackgroundColor.toArgb()

        // A row buffer rather than per-pixel setPixel calls: the same work, but
        // one JNI crossing per row instead of one per pixel.
        val pixels = IntArray(sizePx * sizePx)
        for (moduleY in 0 until modules) {
            val rowStart = moduleY * moduleSizePx * sizePx
            for (moduleX in 0 until modules) {
                val color = if (matrix.get(moduleX, moduleY)) onColor else offColor
                val xStart = moduleX * moduleSizePx
                for (dy in 0 until moduleSizePx) {
                    val offset = rowStart + dy * sizePx + xStart
                    java.util.Arrays.fill(pixels, offset, offset + moduleSizePx, color)
                }
            }
        }

        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
}
