package org.snrkl.nfcbusinesscard.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.data.ShareableItemRepository
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository

/** Which field failed validation, so the form can mark exactly that field. */
internal enum class EditorError { LabelRequired, UrlRequired, UrlInvalid, NameRequired }

internal data class ItemEditorUiState(
    val item: ShareableItem = ShareableItem(),
    val isNew: Boolean = true,
    val errors: Set<EditorError> = emptySet(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
)

internal class ItemEditorViewModel(
    private val itemRepository: ShareableItemRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val itemId: Long,
    private val itemType: ShareableItemType,
) : ViewModel() {

    private val state = MutableStateFlow(ItemEditorUiState())
    val uiState: StateFlow<ItemEditorUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (itemId != 0L) itemRepository.getItem(itemId) else null
            state.value = ItemEditorUiState(
                item = existing ?: ShareableItem(
                    type = itemType,
                    // A vCard item starts with one empty phone and one empty
                    // email so there is somewhere to type. Blank entries are
                    // dropped on the way to storage, so an untouched row costs
                    // nothing.
                    vCard = if (itemType == ShareableItemType.VCard) {
                        VCardData(
                            phones = listOf(PhoneNumber()),
                            emails = listOf(EmailAddress()),
                        )
                    } else {
                        null
                    },
                ),
                isNew = existing == null,
                isLoading = false,
            )
        }
    }

    fun updateLabel(value: String) = mutate { it.copy(label = value) }

    fun updateUrl(value: String) = mutate { it.copy(url = value) }

    fun updateVCard(transform: (VCardData) -> VCardData) = mutate { item ->
        item.copy(vCard = transform(item.vCard ?: VCardData()))
    }

    fun updatePhoto(uri: String?) = updateVCard { it.copy(photoUri = uri) }

    fun addPhone() = updateVCard { it.copy(phones = it.phones + PhoneNumber()) }

    fun removePhone(index: Int) = updateVCard {
        it.copy(phones = it.phones.filterIndexed { i, _ -> i != index })
    }

    fun updatePhone(index: Int, value: String? = null, type: ContactFieldType? = null) =
        updateVCard { data ->
            data.copy(
                phones = data.phones.mapIndexed { i, phone ->
                    if (i == index) {
                        phone.copy(
                            value = value ?: phone.value,
                            type = type ?: phone.type,
                        )
                    } else {
                        phone
                    }
                },
            )
        }

    fun addEmail() = updateVCard { it.copy(emails = it.emails + EmailAddress()) }

    fun removeEmail(index: Int) = updateVCard {
        it.copy(emails = it.emails.filterIndexed { i, _ -> i != index })
    }

    fun updateEmail(index: Int, value: String? = null, type: ContactFieldType? = null) =
        updateVCard { data ->
            data.copy(
                emails = data.emails.mapIndexed { i, email ->
                    if (i == index) {
                        email.copy(
                            value = value ?: email.value,
                            type = type ?: email.type,
                        )
                    } else {
                        email
                    }
                },
            )
        }

    /**
     * Validates and saves.
     *
     * A brand-new first item is made active on save. Without that, adding the
     * very first item leaves nothing selected and the main screen still says
     * there is no active item, which reads as the save having failed.
     */
    fun save() {
        val item = state.value.item
        val errors = validate(item)
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }

        viewModelScope.launch {
            val wasEmpty = itemRepository.count() == 0
            val savedId = itemRepository.save(item.trimmed())
            if (wasEmpty) {
                preferencesRepository.setActiveItemId(savedId)
            }
            state.update { it.copy(isSaved = true, errors = emptySet()) }
        }
    }

    private fun mutate(transform: (ShareableItem) -> ShareableItem) {
        // Clearing errors on edit means the form stops shouting the moment the
        // operator starts fixing the thing it complained about.
        state.update { it.copy(item = transform(it.item), errors = emptySet()) }
    }

    private fun validate(item: ShareableItem): Set<EditorError> = buildSet {
        if (item.label.isBlank()) add(EditorError.LabelRequired)

        when (item.type) {
            ShareableItemType.Url -> {
                val url = item.url?.trim().orEmpty()
                when {
                    url.isEmpty() -> add(EditorError.UrlRequired)
                    !isValidHttpsUrl(url) -> add(EditorError.UrlInvalid)
                }
            }

            ShareableItemType.VCard -> {
                if (item.vCard?.formattedName.isNullOrBlank()) add(EditorError.NameRequired)
            }
        }
    }

    /**
     * Accepts only absolute `https://` URLs.
     *
     * Deliberately strict. The payload is handed to a stranger's phone, which
     * will follow it without asking, so an `http://` link would put the
     * operator's contact details on the wire in the clear. A relative or
     * scheme-less string would encode as a URI record with prefix `0x00` and
     * simply fail to open.
     */
    private fun isValidHttpsUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val remainder = url.removePrefix("https://").removePrefix("HTTPS://")
        // Needs at least a host with a dot in it. "https://foo" is syntactically
        // a URL but is not something a phone can resolve off a stranger's tap.
        val host = remainder.substringBefore('/').substringBefore('?')
        return host.contains('.') && host.length > 2 && !host.startsWith('.')
    }
}

/** Trims whitespace that would otherwise reach the wire. */
private fun ShareableItem.trimmed(): ShareableItem = copy(
    label = label.trim(),
    url = url?.trim(),
    vCard = vCard?.let { data ->
        data.copy(
            formattedName = data.formattedName.trim(),
            givenName = data.givenName.trim(),
            familyName = data.familyName.trim(),
            organisation = data.organisation.trim(),
            jobTitle = data.jobTitle.trim(),
            url = data.url.trim(),
            address = data.address.trim(),
            note = data.note.trim(),
        )
    },
)
