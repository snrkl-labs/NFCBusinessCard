package org.snrkl.nfcbusinesscard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.data.ShareableItemRepository
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio
import org.snrkl.nfcbusinesscard.hce.NfcAvailability
import org.snrkl.nfcbusinesscard.hce.TapEvents

internal data class MainUiState(
    val items: List<ShareableItem> = emptyList(),
    val activeItem: ShareableItem? = null,
    val cardAspectRatio: CardAspectRatio = CardAspectRatio.Australian,
    val cardFaceUri: String? = null,
    val nfcAvailability: NfcAvailability = NfcAvailability.Available,
    /** Whether Send Mode is currently claimed. Never survives backgrounding. */
    val sendModeActive: Boolean = false,
    /**
     * Set when a Send Mode claim was attempted and refused. Worth showing: it
     * means another installed card-emulation app may answer the tap instead.
     */
    val sendModeClaimFailed: Boolean = false,
    val isLoading: Boolean = true,
    /**
     * Number of successful reads this session.
     *
     * A counter rather than a boolean: two taps in quick succession must each
     * produce feedback, and a flag that is already true cannot signal the
     * second one.
     */
    val successfulTapCount: Int = 0,
    val flashOnTap: Boolean = true,
    val soundOnTap: Boolean = false,
    val vibrateOnTap: Boolean = true,
) {
    val hasItems: Boolean get() = items.isNotEmpty()

    /** Send Mode is only offerable when there is something to send and NFC works. */
    val canUseSendMode: Boolean
        get() = activeItem?.isPresentable == true &&
            nfcAvailability == NfcAvailability.Available

    val canShowQr: Boolean get() = activeItem?.isPresentable == true
}

internal class MainViewModel(
    private val itemRepository: ShareableItemRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val successfulTaps = MutableStateFlow(0)
    private val sendModeActive = MutableStateFlow(false)
    private val sendModeClaimFailed = MutableStateFlow(false)
    private val nfcAvailability = MutableStateFlow(NfcAvailability.Available)

    init {
        // The card-emulation service is started by the platform and has no
        // reference to any screen, so it announces reads on a process-wide flow.
        viewModelScope.launch {
            TapEvents.successfulReads.collect { successfulTaps.update { it + 1 } }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        itemRepository.items,
        preferencesRepository.preferences,
        sendModeActive,
        sendModeClaimFailed,
        nfcAvailability,
        successfulTaps,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val items = values[0] as List<ShareableItem>
        val preferences = values[1] as org.snrkl.nfcbusinesscard.data.prefs.AppPreferences
        val sendMode = values[2] as Boolean
        val claimFailed = values[3] as Boolean
        val nfc = values[4] as NfcAvailability
        val taps = values[5] as Int
        // Falling back to the first item means a fresh install with one item
        // already has something active, rather than presenting an empty tag and
        // making the operator hunt for the selection they never made.
        val active = items.firstOrNull { it.id == preferences.activeItemId }
            ?: items.firstOrNull()

        MainUiState(
            items = items,
            activeItem = active,
            cardAspectRatio = preferences.cardAspectRatio,
            cardFaceUri = preferences.cardFaceUri,
            nfcAvailability = nfc,
            sendModeActive = sendMode,
            sendModeClaimFailed = claimFailed,
            successfulTapCount = taps,
            flashOnTap = preferences.flashOnSuccessfulTap,
            soundOnTap = preferences.soundOnSuccessfulTap,
            vibrateOnTap = preferences.vibrateOnSuccessfulTap,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    val sendModeRequested: StateFlow<Boolean> = sendModeActive.asStateFlow()

    fun setActiveItem(id: Long) {
        viewModelScope.launch { preferencesRepository.setActiveItemId(id) }
    }

    fun onNfcAvailabilityChanged(availability: NfcAvailability) {
        nfcAvailability.value = availability
        if (availability != NfcAvailability.Available) {
            sendModeActive.value = false
        }
    }

    /** Toggles the Send Mode request. The activity performs the actual claim. */
    fun setSendMode(enabled: Boolean) {
        sendModeActive.value = enabled
        if (!enabled) sendModeClaimFailed.value = false
    }

    fun onSendModeClaimResult(succeeded: Boolean) {
        sendModeClaimFailed.value = !succeeded
        if (!succeeded) sendModeActive.value = false
    }

    /**
     * Called when the activity stops being resumed.
     *
     * `setPreferredService` requires a resumed activity, so the claim is gone
     * whether or not the UI admits it. Clearing the flag here keeps the toggle
     * honest instead of showing an "on" state that the platform has revoked.
     */
    fun onLostForeground() {
        sendModeActive.value = false
        sendModeClaimFailed.value = false
    }
}
