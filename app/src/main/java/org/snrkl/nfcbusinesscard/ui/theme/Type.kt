package org.snrkl.nfcbusinesscard.ui.theme

import androidx.compose.material3.Typography

/**
 * The MD3 type scale.
 *
 * Compose's default [Typography] already *is* the MD3 scale in Roboto, with all
 * fifteen roles from Display Large down to Label Small at the specified sizes,
 * weights and tracking. Redeclaring those values by hand would add a second
 * source of truth that can only drift from the spec.
 *
 * Roboto is the correct MD3 default typeface — the general advice to avoid it
 * belongs to non-Material design systems and does not apply here.
 */
internal val AppTypography = Typography()
