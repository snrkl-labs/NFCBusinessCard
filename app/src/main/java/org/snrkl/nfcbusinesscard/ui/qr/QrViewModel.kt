package org.snrkl.nfcbusinesscard.ui.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.data.ShareableItemRepository
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository
import org.snrkl.nfcbusinesscard.vcard.DensityTier
import org.snrkl.nfcbusinesscard.vcard.TierFieldSelector

internal data class QrUiState(
    /** Every defined item, so the summary can be swiped between them. */
    val items: List<ShareableItem> = emptyList(),
    val item: ShareableItem? = null,
    val availableTiers: List<DensityTier> = emptyList(),
    /** The tier the view should open at, taken from settings. */
    val initialTier: DensityTier = DensityTier.LinkOnly,
    val maxBrightness: Boolean = true,
    val isLoading: Boolean = true,
) {
    /**
     * Whether the tier control is worth showing.
     *
     * A URL item has exactly one representation, so four identical codes would
     * be a control that visibly does nothing. The spec calls for it to collapse.
     */
    val showTierControl: Boolean get() = availableTiers.size > 1
}

/**
 * Backs the QR View.
 *
 * Holds the per-session bitmap cache so swiping between tiers is instant. The
 * cache lives and dies with this view model, which is scoped to the screen —
 * matching the spec's requirement that codes are regenerated on the next open
 * rather than persisted. Nothing is ever written to disk: item content changes
 * whenever the operator edits it, and a stale code is worse than a slow one.
 */
internal class QrViewModel(
    itemRepository: ShareableItemRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    /**
     * Switches which item is showing.
     *
     * Sets the Active Item rather than keeping a view-local selection. The app's
     * model is that exactly one item is active and that NFC and the QR view
     * serve the same one — a QR showing a different card from the one a tap
     * would send is precisely the disagreement the active-item design exists to
     * prevent.
     */
    fun setActiveItem(id: Long) {
        viewModelScope.launch { preferencesRepository.setActiveItemId(id) }
    }

    /**
     * Keyed by item id, tier *and* pixel size.
     *
     * Size belongs in the key because a rotation or a window resize asks for a
     * different bitmap; keying on tier alone would serve a code rendered for the
     * old dimensions, scaled — which is exactly the softening that makes a code
     * fail to scan.
     */
    private data class CacheKey(val itemId: Long, val tier: DensityTier, val sizePx: Int)

    private val bitmapCache = mutableMapOf<CacheKey, ImageBitmap?>()

    val uiState: StateFlow<QrUiState> = combine(
        itemRepository.items,
        preferencesRepository.preferences,
    ) { items, preferences ->
        val active = items.firstOrNull { it.id == preferences.activeItemId }
            ?: items.firstOrNull()

        val available = active?.let { TierFieldSelector.availableTiers(it) }.orEmpty()

        QrUiState(
            items = items,
            item = active,
            availableTiers = available,
            // The configured default may not exist for this item — a contact
            // card with no website has no LinkOnly tier. Fall back to the
            // sparsest tier the item actually has rather than opening on a page
            // that cannot render.
            initialTier = preferences.defaultDensityTier
                .takeIf { it in available }
                ?: available.firstOrNull()
                ?: DensityTier.LinkOnly,
            maxBrightness = preferences.maxBrightnessInQrView,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QrUiState(),
    )

    /**
     * Returns the code for [item] at [tier], rendering it on first request.
     *
     * Null means the payload will not fit in a QR code even at version 40, which
     * the view turns into an instruction to drop a tier. Encoding is fast enough
     * — single-digit milliseconds for payloads this size — to do inline; pushing
     * it to a background dispatcher would add a frame of blank space on every
     * swipe to avoid a cost that is not there.
     */
    fun bitmapFor(item: ShareableItem, tier: DensityTier, sizePx: Int): ImageBitmap? {
        val key = CacheKey(item.id, tier, sizePx)
        if (bitmapCache.containsKey(key)) return bitmapCache[key]

        val payload = TierFieldSelector.payloadFor(item, tier)
        val bitmap = payload?.let { QrBitmapRenderer.render(it, sizePx) }
        bitmapCache[key] = bitmap
        return bitmap
    }

    /** Drops everything cached for [itemId], for when its content changed. */
    fun invalidate(itemId: Long) {
        bitmapCache.keys.filter { it.itemId == itemId }.forEach(bitmapCache::remove)
    }

    override fun onCleared() {
        bitmapCache.clear()
        super.onCleared()
    }
}
