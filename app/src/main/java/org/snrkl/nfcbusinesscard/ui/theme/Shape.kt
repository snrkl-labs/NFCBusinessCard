package org.snrkl.nfcbusinesscard.ui.theme

import androidx.compose.material3.Shapes

/**
 * The MD3 shape scale.
 *
 * Compose's default [Shapes] carries the specified corner radii — extra small 4dp
 * through extra large 28dp — and components pick the right one for themselves:
 * buttons fully rounded, cards medium, dialogs extra large. Overriding it would
 * mean hand-maintaining magic numbers that the compliance audit specifically
 * looks for.
 */
internal val AppShapes = Shapes()
