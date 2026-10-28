package org.snrkl.nfcbusinesscard.card

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.ui.main.GeneratedCardAspect

/**
 * Draws a business card from a vCard's own details.
 *
 * Replaces the pro-forma placeholder as the built-in card. The placeholder
 * earned its keep while the layout was being proven — border, diagonals and
 * printed dimensions made cropping and resampling obvious — but once real
 * artwork exists nobody wants a test pattern on screen, and an operator without
 * artwork is better served by a plain card carrying their actual details than by
 * a fake one carrying none.
 *
 * Rendered at exactly the published pixel dimensions, so it displays 1:1 on the
 * target device with no resampling, on the same terms as supplied artwork.
 *
 * Deliberately plain. This is not an attempt to design someone a brand: it is
 * legible type on a clean ground, which is what the view is actually for.
 */
internal object GeneratedCardRenderer {

    fun render(
        context: Context,
        data: VCardData,
        aspect: GeneratedCardAspect,
        darkTheme: Boolean,
    ): ImageBitmap {
        val width = aspect.widthPx
        val height = aspect.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val ink = if (darkTheme) Color.WHITE else Color.rgb(0x1C, 0x1B, 0x1F)
        val muted = if (darkTheme) Color.rgb(0xB0, 0xB0, 0xB8) else Color.rgb(0x55, 0x55, 0x5C)
        val ground = if (darkTheme) Color.rgb(0x1C, 0x1B, 0x1F) else Color.WHITE

        canvas.drawColor(ground)

        // Proportions rather than constants, so the layout holds at any of the
        // three card ratios rather than only the one it was tuned on.
        val margin = width * 0.06f
        val photoSize = height * 0.42f
        val photo = data.photoUri?.let { loadPhoto(context, it, photoSize.toInt()) }

        val textRight = if (photo != null) width - margin - photoSize - margin * 0.6f else width - margin
        val textWidth = textRight - margin

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Name, sized to fit rather than sized and hoped for: a long name at a
        // fixed size would run into the photo or off the edge.
        paint.color = ink
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val nameSize = fitTextSize(paint, data.formattedName, textWidth, height * 0.16f, height * 0.07f)
        paint.textSize = nameSize
        var y = height * 0.42f
        canvas.drawText(data.formattedName, margin, y, paint)

        // Job title and organisation.
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.color = muted
        val subtitle = listOf(data.jobTitle, data.organisation)
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
        if (subtitle.isNotBlank()) {
            paint.textSize = fitTextSize(paint, subtitle, textWidth, height * 0.075f, height * 0.045f)
            y += height * 0.10f
            canvas.drawText(subtitle, margin, y, paint)
        }

        // Contact lines. Only what exists, in the order someone would read it.
        paint.color = ink
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val details = buildList {
            data.phones.firstOrNull { it.value.isNotBlank() }?.let { add(it.value) }
            data.emails.firstOrNull { it.value.isNotBlank() }?.let { add(it.value) }
            if (data.url.isNotBlank()) add(data.url.removePrefix("https://").removePrefix("http://"))
        }
        var detailY = y + height * 0.12f
        details.forEach { line ->
            paint.textSize = fitTextSize(paint, line, textWidth, height * 0.075f, height * 0.04f)
            canvas.drawText(line, margin, detailY, paint)
            detailY += height * 0.095f
        }

        if (photo != null) {
            val left = width - margin - photoSize
            val top = (height - photoSize) / 2f
            canvas.drawBitmap(
                circleCrop(photo, photoSize.toInt()),
                left,
                top,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }

        return bitmap.asImageBitmap()
    }

    /**
     * Largest text size at or below [maxSize] that fits [available] width.
     *
     * Shrinks rather than truncating: a card that abbreviates someone's surname
     * to fit is worse than one that sets it a point smaller. Stops at [minSize],
     * below which the point of the view — that the details are readable — is
     * lost anyway.
     */
    private fun fitTextSize(
        paint: Paint,
        text: String,
        available: Float,
        maxSize: Float,
        minSize: Float,
    ): Float {
        if (text.isEmpty()) return maxSize
        var size = maxSize
        while (size > minSize) {
            paint.textSize = size
            if (paint.measureText(text) <= available) return size
            size -= 1f
        }
        return minSize
    }

    private fun loadPhoto(context: Context, uri: String, size: Int): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            // Decoded at roughly the size it is drawn at, not full resolution: a
            // multi-megapixel contact photo scaled to a 300px circle wastes
            // memory on every recomposition for no visible gain.
            val bytes = stream.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / size.coerceAtLeast(1))
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }.getOrNull()

    /** Centre-crops to a circle, the conventional treatment for a portrait. */
    private fun circleCrop(source: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val side = minOf(source.width, source.height)
        val src = Rect(
            (source.width - side) / 2,
            (source.height - side) / 2,
            (source.width + side) / 2,
            (source.height + side) / 2,
        )
        val dst = RectF(0f, 0f, size.toFloat(), size.toFloat())

        canvas.drawOval(dst, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, src, dst, paint)
        return output
    }
}
