package org.snrkl.nfcbusinesscard.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.data.ShareableItemRepository
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository

internal data class ItemListUiState(
    val items: List<ShareableItem> = emptyList(),
    val activeItemId: Long? = null,
    val isLoading: Boolean = true,
)

/** One-shot messages for the list screen, such as a failed import. */
internal sealed interface ItemListEvent {
    /**
     * An import produced nothing.
     *
     * [reason] is carried through rather than flattened to a single "it didn't
     * work": a refused read and an empty contact need different advice, and the
     * first version reported both identically, which is what made the contact
     * import bug hard to see from the outside.
     */
    data class ImportFailed(val reason: ImportFailureReason) : ItemListEvent
    data class Imported(val itemId: Long) : ItemListEvent
}

internal enum class ImportFailureReason { FileUnreadable, ContactUnreadable, PermissionDenied, Empty }

internal class ItemListViewModel(
    private val itemRepository: ShareableItemRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<ItemListUiState> = combine(
        itemRepository.items,
        preferencesRepository.preferences,
    ) { items, preferences ->
        ItemListUiState(
            items = items,
            activeItemId = preferences.activeItemId ?: items.firstOrNull()?.id,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItemListUiState(),
    )

    private val _events = MutableSharedFlow<ItemListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ItemListEvent> = _events.asSharedFlow()

    /**
     * Saves a contact card imported from a file or the contacts picker.
     *
     * Imported cards are saved immediately rather than dropped into an empty
     * editor: the operator picked a specific contact, so the useful default is
     * that it exists and can then be edited, not that it must be retyped.
     */
    fun saveImported(data: VCardData, fallbackLabel: String) {
        viewModelScope.launch {
            val id = itemRepository.save(
                ShareableItem(
                    label = data.formattedName.ifBlank { fallbackLabel },
                    type = ShareableItemType.VCard,
                    vCard = data,
                ),
            )
            if (itemRepository.count() == 1) {
                preferencesRepository.setActiveItemId(id)
            }
            _events.tryEmit(ItemListEvent.Imported(id))
        }
    }

    fun reportImportFailure(reason: ImportFailureReason) {
        _events.tryEmit(ItemListEvent.ImportFailed(reason))
    }

    fun setActive(id: Long) {
        viewModelScope.launch { preferencesRepository.setActiveItemId(id) }
    }

    /**
     * Deletes an item, and clears the active selection if it was the one deleted.
     *
     * Leaving a dangling active id would leave the HCE service pointing at a row
     * that no longer exists — which presents as an empty tag with no visible
     * reason why.
     */
    fun delete(item: ShareableItem) {
        viewModelScope.launch {
            itemRepository.delete(item.id)
            if (uiState.value.activeItemId == item.id) {
                preferencesRepository.setActiveItemId(null)
            }
        }
    }

    /**
     * Moves an item one place up or down.
     *
     * Up/down buttons rather than drag-and-drop: the library is a handful of
     * items, the buttons are reachable by a screen reader, and drag reordering
     * in a Compose list is a great deal of state for an interaction the operator
     * will perform about twice.
     */
    fun move(from: Int, to: Int) {
        val current = uiState.value.items
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch {
            itemRepository.applyOrder(reordered.map { it.id })
        }
    }
}
