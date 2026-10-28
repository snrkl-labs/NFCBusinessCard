package org.snrkl.nfcbusinesscard.ui.nav

/**
 * The app's navigation graph.
 *
 * Four screens, addressed by route string. A single-activity Compose app with
 * this few destinations does not need a typed-route abstraction on top of
 * Navigation Compose; the routes are declared here so they are at least not
 * scattered as string literals across call sites.
 */
internal object Destinations {
    const val MAIN = "main"
    const val FULL_CARD = "full_card"
    const val QR = "qr"
    const val SETTINGS = "settings"
    const val ITEMS = "items"

    /** The item editor takes the id to edit, or [NEW_ITEM_ID] to create one. */
    const val ITEM_EDITOR_ROUTE = "item_editor/{itemId}/{itemType}"

    const val NEW_ITEM_ID = 0L

    fun itemEditor(itemId: Long, itemType: String): String = "item_editor/$itemId/$itemType"

    const val ARG_ITEM_ID = "itemId"
    const val ARG_ITEM_TYPE = "itemType"
}
