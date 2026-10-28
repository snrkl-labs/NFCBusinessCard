package org.snrkl.nfcbusinesscard.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 3x2 / 5x2 boundary.
 *
 * One widget serves both shapes by hiding its button column when narrow, and
 * this decides when. RemoteViews itself has no coverage — it is not reachable
 * from the Robolectric suite — so pinning the boundary here is the only
 * automated check the resize behaviour gets.
 *
 * Widths are the launcher's `70 * n - 30` cell formula.
 */
class WidgetSizeTest {

    @Test
    fun theFiveColumnWidgetKeepsItsButtons() {
        assertTrue(widgetShowsButtons(320))
    }

    @Test
    fun theThreeColumnWidgetDropsThem() {
        // The whole point of the small size: the card gets the tile to itself.
        assertFalse(widgetShowsButtons(180))
    }

    @Test
    fun fourColumnsIsWideEnough() {
        // 250dp. Refusing the buttons here would draw the line one cell early —
        // there is still room for the card and both controls.
        assertTrue(widgetShowsButtons(250))
        assertFalse(widgetShowsButtons(249))
    }

    @Test
    fun anUnmeasuredWidgetGetsTheButtons() {
        // The options bundle is empty until the launcher fills it. A widget that
        // has not been measured is far likelier to be at its placed 5x2 size
        // than dragged in, so the default is the full layout rather than the
        // stripped one.
        assertTrue(widgetShowsButtons(0))
        assertTrue(widgetShowsButtons(-1))
    }
}
