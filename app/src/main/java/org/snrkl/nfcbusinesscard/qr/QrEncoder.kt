package org.snrkl.nfcbusinesscard.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes text as a QR bit matrix.
 *
 * ZXing core only — no Play Services. The target is a GrapheneOS device with no
 * Google Play Services at all, so ML Kit, Firebase and the Play barcode APIs are
 * not options, and reaching for one would produce an app that installs and then
 * silently fails to draw a code.
 *
 * The result is a [BitMatrix], not a bitmap: this layer stays free of Android
 * graphics types so the module counts it produces can be asserted in plain unit
 * tests, which is how the density tiers are actually verified to do anything.
 */
internal object QrEncoder {

    /**
     * Error correction level L — the lowest.
     *
     * Counter-intuitive but correct here. Higher levels add redundancy for
     * damage tolerance, which buys nothing on a clean phone screen that is not
     * going to get scuffed or rained on. What it costs is modules, and more
     * modules in the same physical area means each one is smaller and harder for
     * a struggling camera to resolve. Level L keeps the modules as large as
     * possible, which is the entire point of the density-tier feature.
     */
    private val ERROR_CORRECTION = ErrorCorrectionLevel.L

    /** No quiet zone from ZXing; the Compose layer adds its own as padding. */
    private const val MARGIN_MODULES = 0

    /**
     * Encodes [content], or returns null if it cannot be represented.
     *
     * Null means the payload exceeded what a QR code can hold even at the
     * largest version. The QR view turns that into an instruction to drop a
     * tier rather than showing a blank square, because a blank square looks like
     * a bug and a message looks like a recoverable situation — which it is.
     */
    fun encode(content: String): BitMatrix? {
        if (content.isEmpty()) return null
        return try {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                // Passing zero lets ZXing pick the smallest version that fits,
                // rather than padding up to a fixed pixel size. Scaling to the
                // view happens later, from the module grid.
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ERROR_CORRECTION,
                    EncodeHintType.MARGIN to MARGIN_MODULES,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        } catch (e: WriterException) {
            null
        } catch (e: IllegalArgumentException) {
            // ZXing raises this for content that overflows the largest version.
            null
        }
    }

    /**
     * The QR version a payload would need, or null if it does not fit.
     *
     * Derived from the module count: a version-N code is 17 + 4N modules on a
     * side. Used by tests to prove that dropping a tier actually drops the
     * version, which is the only thing that makes the tier feature worth having.
     */
    fun versionFor(content: String): Int? {
        val matrix = encode(content) ?: return null
        val modules = matrix.width
        if (modules < 21) return null
        val version = (modules - 17) / 4
        return if (version in 1..40 && 17 + 4 * version == modules) version else null
    }
}
