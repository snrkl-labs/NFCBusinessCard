import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Generates the pro-forma Card Face placeholders and the Kotlin constants the
 * layout uses.
 *
 * The placeholder is a diagnostic instrument, not decoration. Each one carries:
 *
 *  - a **border around the full edge** — if any part of it is missing on screen,
 *    the card region is cropping rather than fitting;
 *  - **both diagonals, corner to corner** — if they miss the corners, the aspect
 *    ratio is wrong; if they look soft or stair-stepped, the bitmap is being
 *    resampled instead of drawn 1:1;
 *  - its **own pixel dimensions printed on it** — which tells you at a glance
 *    which asset actually loaded.
 *
 * Because both the image and the Kotlin constants come out of the same
 * [CardFaceSpec], the printed number cannot go stale. Change the spec and the
 * task regenerates, so the image never claims a size it is not.
 *
 * Rendering is deliberately aliased for the border and text: this asset exists to
 * make resampling visible, and antialiasing the source would blunt the very
 * artefact it is meant to expose. The diagonals are antialiased, because a
 * jagged diagonal is expected and a *soft* one is the signal being looked for.
 */
@CacheableTask
abstract class GenerateCardFaceAssets : DefaultTask() {

    @get:OutputDirectory
    abstract val resOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinOutputDir: DirectoryProperty

    /**
     * Declaring the geometry as inputs is what makes the "printed number never
     * lies" guarantee mechanical: change a dimension and Gradle sees the input
     * change and regenerates, rather than serving a cached image with the old
     * number on it.
     */
    @get:Input
    val widthPx: Int get() = CardFaceSpec.WIDTH_PX

    @get:Input
    val widthDp: Int get() = CardFaceSpec.WIDTH_DP

    @get:Input
    val aspectSummary: String
        get() = CardFaceSpec.Aspect.values().joinToString(";") {
            "${it.id}:${CardFaceSpec.WIDTH_PX}x${it.heightPx}"
        }

    @TaskAction
    fun generate() {
        val drawableDir = resOutputDir.get().asFile.resolve("drawable-nodpi")
        drawableDir.mkdirs()

        CardFaceSpec.Aspect.values().filterNot { it.hasSampleArtwork }.forEach { aspect ->
            val image = renderPlaceholder(aspect)
            val target = drawableDir.resolve("${aspect.drawableName}.png")
            ImageIO.write(image, "png", target)
            logger.lifecycle(
                "Card Face placeholder: ${target.name} — " +
                    "${CardFaceSpec.WIDTH_PX} x ${aspect.heightPx} px " +
                    "(${CardFaceSpec.WIDTH_DP} x ${aspect.heightDp} dp at density " +
                    "${CardFaceSpec.TARGET_DENSITY}.0)",
            )
        }

        ImageIO.write(renderServiceBanner(), "png", drawableDir.resolve("hce_service_banner.png"))

        writeKotlinConstants()
    }

    private fun renderPlaceholder(aspect: CardFaceSpec.Aspect): BufferedImage {
        val width = CardFaceSpec.WIDTH_PX
        val height = aspect.heightPx
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.color = Color(0xFA, 0xFA, 0xFA)
        g.fillRect(0, 0, width, height)

        // Diagonals first, so the border draws cleanly over their endpoints.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x9E, 0x9E, 0x9E)
        g.stroke = BasicStroke(2f)
        // Inset by one pixel so a corner-to-corner line is fully inside the
        // bitmap — a line drawn to `width` exactly lands on the pixel after the
        // last one and is clipped away, which would look like the very cropping
        // bug this asset is meant to detect.
        g.drawLine(0, 0, width - 1, height - 1)
        g.drawLine(width - 1, 0, 0, height - 1)

        // Border: aliased and one pixel wide, so any softening is unmistakable.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.color = Color(0x21, 0x21, 0x21)
        g.stroke = BasicStroke(1f)
        g.drawRect(0, 0, width - 1, height - 1)
        // A second, inset rule. If the outer one is clipped by the layout but
        // this one survives, the overflow is exactly one pixel and the cause is
        // rounding rather than a wrong aspect ratio.
        g.drawRect(4, 4, width - 9, height - 9)

        val dimensionText = "$width x $height px"
        val detailText = "${CardFaceSpec.WIDTH_DP} x ${aspect.heightDp} dp  @${CardFaceSpec.TARGET_DENSITY}x"

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.color = Color(0x21, 0x21, 0x21)

        g.font = Font(Font.MONOSPACED, Font.BOLD, height / 9)
        drawCentred(g, image, dimensionText, height / 2 - height / 24)

        g.font = Font(Font.MONOSPACED, Font.PLAIN, height / 16)
        drawCentred(g, image, detailText, height / 2 + height / 9)

        g.font = Font(Font.MONOSPACED, Font.PLAIN, height / 20)
        drawCentred(g, image, aspect.label, height / 2 + height / 5)

        g.font = Font(Font.MONOSPACED, Font.PLAIN, height / 24)
        drawCentred(g, image, "NFC Business Card - placeholder", height - height / 10)

        g.dispose()
        return image
    }

    private fun drawCentred(
        g: java.awt.Graphics2D,
        image: BufferedImage,
        text: String,
        baselineY: Int,
    ) {
        val metrics = g.fontMetrics
        val x = (image.width - metrics.stringWidth(text)) / 2
        g.drawString(text, x, baselineY)
    }

    /**
     * The banner Android shows for this service in Tap & pay settings.
     *
     * The platform expects 260 x 96 px. Anything else is scaled by the system
     * settings UI, which is out of this app's control.
     */
    private fun renderServiceBanner(): BufferedImage {
        val width = 260
        val height = 96
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color(0x67, 0x50, 0xA4)
        g.fillRoundRect(0, 0, width, height, 16, 16)

        g.color = Color.WHITE
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
        val line1 = "NFC Business"
        val line2 = "Card"
        var metrics = g.fontMetrics
        g.drawString(line1, (width - metrics.stringWidth(line1)) / 2, height / 2 - 2)
        metrics = g.fontMetrics
        g.drawString(line2, (width - metrics.stringWidth(line2)) / 2, height / 2 + 24)

        g.dispose()
        return image
    }

    /**
     * Emits the same geometry as Kotlin constants for the Compose layout.
     *
     * Generated rather than hand-written so the layout and the placeholder can
     * never disagree about how big the card is.
     */
    private fun writeKotlinConstants() {
        val packageDir = kotlinOutputDir.get().asFile
            .resolve("org/snrkl/nfcbusinesscard/ui/main")
        packageDir.mkdirs()

        val aspectEntries = CardFaceSpec.Aspect.values().joinToString("\n") { aspect ->
            """    /** ${aspect.label} — ${CardFaceSpec.WIDTH_PX} x ${aspect.heightPx} px. */
    ${aspect.name}(
        id = "${aspect.id}",
        widthPx = ${CardFaceSpec.WIDTH_PX},
        heightPx = ${aspect.heightPx},
        widthDp = ${CardFaceSpec.WIDTH_DP},
        heightDp = ${aspect.heightDp},
        ratio = ${aspect.widthUnits}f / ${aspect.heightUnits}f,
    ),"""
        }

        val content = """
// GENERATED FILE — DO NOT EDIT.
// Produced by the :app:generateCardFaceAssets task from buildSrc/CardFaceSpec.kt.
// Edit the spec, not this file: the placeholder PNGs are generated from the same
// numbers, so hand-editing here would make the dimensions printed on the images
// a lie.
package org.snrkl.nfcbusinesscard.ui.main

/**
 * Card Face geometry, generated from the build's CardFaceSpec.
 *
 * [widthPx] x [heightPx] is the exact bitmap size that maps one source pixel to
 * one screen pixel when drawn at [widthDp] x [heightDp] on a density-${CardFaceSpec.TARGET_DENSITY}.0
 * device — the target Pixel 8 Pro. Artwork exported at these dimensions is
 * displayed without resampling.
 */
internal enum class GeneratedCardAspect(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
    val widthDp: Int,
    val heightDp: Int,
    val ratio: Float,
) {
$aspectEntries
    ;

    companion object {
        /** The width the card region is capped at, in dp. */
        const val CARD_WIDTH_DP: Int = ${CardFaceSpec.WIDTH_DP}

        /** The exported bitmap width, in pixels, for every aspect. */
        const val CARD_WIDTH_PX: Int = ${CardFaceSpec.WIDTH_PX}
    }
}
""".trimStart()

        packageDir.resolve("GeneratedCardAspect.kt").writeText(content)
    }
}
