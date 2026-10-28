package org.snrkl.nfcbusinesscard.data.prefs

import org.snrkl.nfcbusinesscard.vcard.DensityTier

/**
 * The card region's aspect ratio.
 *
 * Business cards are not standardised worldwide, and a card region at the wrong
 * ratio either crops the artwork or letterboxes it. Default is the Australian
 * 90 × 55 per the spec.
 */
enum class CardAspectRatio(val widthMm: Double, val heightMm: Double) {
    /** ISO/IEC 7810 ID-1, the credit-card size. */
    Iso(85.6, 54.0),

    /** Australian standard, and the project default. */
    Australian(90.0, 55.0),

    /** US standard, 3.5 × 2 inches. */
    Us(88.9, 50.8),
    ;

    val ratio: Double get() = widthMm / heightMm
}

/**
 * Scalar app settings.
 *
 * These live in Preferences DataStore rather than Room. The item library is a
 * relational graph and belongs in a database; these are half a dozen independent
 * scalars with no relationships, and putting them in a table would mean a schema
 * migration every time a setting is added.
 */
data class AppPreferences(
    /** The id of the Active Item, or null when nothing is selected. */
    val activeItemId: Long? = null,

    /** Which tier QR View opens at. Tier changes within a session are transient. */
    val defaultDensityTier: DensityTier = DensityTier.LinkOnly,

    /** Whether QR View drives the window to full brightness on entry. */
    val maxBrightnessInQrView: Boolean = true,

    /** The shape of the Card Face region. */
    val cardAspectRatio: CardAspectRatio = CardAspectRatio.Australian,

    /**
     * A user-supplied Card Face PNG, as a persisted content URI string.
     * Null means the built-in placeholder is in use.
     */
    val cardFaceUri: String? = null,

    /** Flash the screen when a reader successfully reads the card. */
    val flashOnSuccessfulTap: Boolean = true,

    /** Play a sound when a reader successfully reads the card. */
    val soundOnSuccessfulTap: Boolean = false,

    /**
     * Vibrate when a reader successfully reads the card.
     *
     * Often the most useful of the three: during a tap the phone is usually
     * turned away from the operator, so a flash is facing the other person and
     * a sound may be lost in a noisy room.
     */
    val vibrateOnSuccessfulTap: Boolean = true,
)
