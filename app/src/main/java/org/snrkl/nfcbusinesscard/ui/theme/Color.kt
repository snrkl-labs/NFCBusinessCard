package org.snrkl.nfcbusinesscard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Material Design 3 baseline colour scheme.
 *
 * This is the fallback only. On the target device — a GrapheneOS Pixel, so
 * API 31+ — dynamic colour derives the scheme from the user's wallpaper and this
 * palette is never seen. It exists so the app is still correctly themed on an
 * API 29 or 30 device, which the minSdk of 29 permits.
 *
 * These are the published MD3 baseline values rather than an invented palette.
 * The project has no marketing name and no brand identity yet, so a brand
 * colour would be a branding decision made by accident.
 */

// Light
internal val LightPrimary = Color(0xFF6750A4)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFEADDFF)
internal val LightOnPrimaryContainer = Color(0xFF21005D)
internal val LightSecondary = Color(0xFF625B71)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFE8DEF8)
internal val LightOnSecondaryContainer = Color(0xFF1D192B)
internal val LightTertiary = Color(0xFF7D5260)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFFD8E4)
internal val LightOnTertiaryContainer = Color(0xFF31111D)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)
internal val LightBackground = Color(0xFFFFFBFE)
internal val LightOnBackground = Color(0xFF1C1B1F)
internal val LightSurface = Color(0xFFFFFBFE)
internal val LightOnSurface = Color(0xFF1C1B1F)
internal val LightSurfaceVariant = Color(0xFFE7E0EC)
internal val LightOnSurfaceVariant = Color(0xFF49454F)
internal val LightOutline = Color(0xFF79747E)
internal val LightOutlineVariant = Color(0xFFCAC4D0)
internal val LightScrim = Color(0xFF000000)
internal val LightInverseSurface = Color(0xFF313033)
internal val LightInverseOnSurface = Color(0xFFF4EFF4)
internal val LightInversePrimary = Color(0xFFD0BCFF)

// Dark
internal val DarkPrimary = Color(0xFFD0BCFF)
internal val DarkOnPrimary = Color(0xFF381E72)
internal val DarkPrimaryContainer = Color(0xFF4F378B)
internal val DarkOnPrimaryContainer = Color(0xFFEADDFF)
internal val DarkSecondary = Color(0xFFCCC2DC)
internal val DarkOnSecondary = Color(0xFF332D41)
internal val DarkSecondaryContainer = Color(0xFF4A4458)
internal val DarkOnSecondaryContainer = Color(0xFFE8DEF8)
internal val DarkTertiary = Color(0xFFEFB8C8)
internal val DarkOnTertiary = Color(0xFF492532)
internal val DarkTertiaryContainer = Color(0xFF633B48)
internal val DarkOnTertiaryContainer = Color(0xFFFFD8E4)
internal val DarkError = Color(0xFFF2B8B5)
internal val DarkOnError = Color(0xFF601410)
internal val DarkErrorContainer = Color(0xFF8C1D18)
internal val DarkOnErrorContainer = Color(0xFFF9DEDC)
internal val DarkBackground = Color(0xFF1C1B1F)
internal val DarkOnBackground = Color(0xFFE6E1E5)
internal val DarkSurface = Color(0xFF1C1B1F)
internal val DarkOnSurface = Color(0xFFE6E1E5)
internal val DarkSurfaceVariant = Color(0xFF49454F)
internal val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
internal val DarkOutline = Color(0xFF938F99)
internal val DarkOutlineVariant = Color(0xFF49454F)
internal val DarkScrim = Color(0xFF000000)
internal val DarkInverseSurface = Color(0xFFE6E1E5)
internal val DarkInverseOnSurface = Color(0xFF313033)
internal val DarkInversePrimary = Color(0xFF6750A4)

/**
 * The QR code's own colours, which are deliberately **not** theme tokens.
 *
 * A QR decoder thresholds luminance. Tinting the modules with a dynamic-colour
 * primary, or drawing them on a tonal surface, narrows the contrast ratio the
 * scanner has to work with — which is precisely the failure this app's density
 * tiers exist to fight. Pure black on pure white is the maximum contrast the
 * screen can produce, and nothing about the user's wallpaper should be allowed
 * to erode it.
 *
 * An MD3 compliance audit will flag these as hardcoded. That is the correct
 * finding and the intended answer is this comment: scannability outranks
 * theming inside the code's own bounds. Everything around the code — the
 * surface it sits on, the slider, the tier control — uses proper roles.
 */
internal val QrModuleColor = Color(0xFF000000)
internal val QrBackgroundColor = Color(0xFFFFFFFF)
