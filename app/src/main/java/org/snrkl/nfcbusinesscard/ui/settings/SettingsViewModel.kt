package org.snrkl.nfcbusinesscard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferences
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio
import org.snrkl.nfcbusinesscard.vcard.DensityTier

internal class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences
        .map { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPreferences(),
        )

    fun setDefaultTier(tier: DensityTier) {
        viewModelScope.launch { preferencesRepository.setDefaultDensityTier(tier) }
    }

    fun setMaxBrightness(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMaxBrightnessInQrView(enabled) }
    }

    fun setCardAspectRatio(ratio: CardAspectRatio) {
        viewModelScope.launch { preferencesRepository.setCardAspectRatio(ratio) }
    }

    fun setCardFaceUri(uri: String?) {
        viewModelScope.launch { preferencesRepository.setCardFaceUri(uri) }
    }

    fun setFlashOnTap(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setFlashOnSuccessfulTap(enabled) }
    }

    fun setSoundOnTap(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundOnSuccessfulTap(enabled) }
    }

    fun setVibrateOnTap(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVibrateOnSuccessfulTap(enabled) }
    }
}
