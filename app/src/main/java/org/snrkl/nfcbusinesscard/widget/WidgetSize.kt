package org.snrkl.nfcbusinesscard.widget

/**
 * Narrowest widget, in dp, that still shows the NFC and QR buttons.
 *
 * 250dp is four columns by the launcher's `70 * n - 30` formula. Three columns
 * is 180dp and loses them; four still has room for the card and both controls,
 * and refusing them there would be an arbitrary line drawn one cell early.
 */
internal const val BUTTONS_MIN_WIDTH_DP = 250

/**
 * Whether a widget of this width carries the NFC and QR buttons.
 *
 * Split out from the provider so the 3x2 / 5x2 boundary can be tested without an
 * AppWidgetManager. It is the whole of what makes one widget serve two shapes,
 * and RemoteViews is otherwise unreachable from the test suite.
 *
 * A width of zero or less means the launcher has told us nothing — the options
 * bundle is empty until it fills one in. That gets the buttons: an unmeasured
 * widget is far more likely to be sitting at its placed 5x2 size than to have
 * been dragged in.
 */
internal fun widgetShowsButtons(minWidthDp: Int): Boolean =
    minWidthDp <= 0 || minWidthDp >= BUTTONS_MIN_WIDTH_DP
