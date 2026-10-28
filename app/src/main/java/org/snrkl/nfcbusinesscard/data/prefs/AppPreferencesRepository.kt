package org.snrkl.nfcbusinesscard.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.snrkl.nfcbusinesscard.vcard.DensityTier
import java.io.IOException

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

/**
 * Reads and writes the scalar app settings.
 *
 * Enums are stored by name for the same reason the Room converters do it: an
 * ordinal is positional, and reordering an enum would silently reinterpret
 * stored values.
 */
internal class AppPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.preferencesDataStore

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { throwable ->
            // A corrupt or unreadable preferences file should start the app at
            // its defaults, not crash it on launch with no way back.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { stored ->
            AppPreferences(
                activeItemId = stored[Keys.ACTIVE_ITEM_ID]?.takeIf { it > 0L },
                defaultDensityTier = stored[Keys.DEFAULT_DENSITY_TIER]
                    ?.let { name -> DensityTier.entries.firstOrNull { it.name == name } }
                    ?: DensityTier.LinkOnly,
                maxBrightnessInQrView = stored[Keys.MAX_BRIGHTNESS] ?: true,
                cardAspectRatio = stored[Keys.CARD_ASPECT_RATIO]
                    ?.let { name -> CardAspectRatio.entries.firstOrNull { it.name == name } }
                    ?: CardAspectRatio.Australian,
                cardFaceUri = stored[Keys.CARD_FACE_URI]?.takeIf { it.isNotBlank() },
                flashOnSuccessfulTap = stored[Keys.FLASH_ON_TAP] ?: true,
                soundOnSuccessfulTap = stored[Keys.SOUND_ON_TAP] ?: false,
                vibrateOnSuccessfulTap = stored[Keys.VIBRATE_ON_TAP] ?: true,
            )
        }

    suspend fun setActiveItemId(id: Long?) {
        dataStore.edit { preferences ->
            if (id == null) {
                preferences.remove(Keys.ACTIVE_ITEM_ID)
            } else {
                preferences[Keys.ACTIVE_ITEM_ID] = id
            }
        }
    }

    suspend fun setDefaultDensityTier(tier: DensityTier) {
        dataStore.edit { it[Keys.DEFAULT_DENSITY_TIER] = tier.name }
    }

    suspend fun setMaxBrightnessInQrView(enabled: Boolean) {
        dataStore.edit { it[Keys.MAX_BRIGHTNESS] = enabled }
    }

    suspend fun setCardAspectRatio(ratio: CardAspectRatio) {
        dataStore.edit { it[Keys.CARD_ASPECT_RATIO] = ratio.name }
    }

    suspend fun setCardFaceUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(Keys.CARD_FACE_URI)
            } else {
                preferences[Keys.CARD_FACE_URI] = uri
            }
        }
    }

    suspend fun setFlashOnSuccessfulTap(enabled: Boolean) {
        dataStore.edit { it[Keys.FLASH_ON_TAP] = enabled }
    }

    suspend fun setSoundOnSuccessfulTap(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND_ON_TAP] = enabled }
    }

    suspend fun setVibrateOnSuccessfulTap(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATE_ON_TAP] = enabled }
    }

    private object Keys {
        val ACTIVE_ITEM_ID = longPreferencesKey("active_item_id")
        val DEFAULT_DENSITY_TIER = stringPreferencesKey("default_density_tier")
        val MAX_BRIGHTNESS = booleanPreferencesKey("max_brightness_in_qr_view")
        val CARD_ASPECT_RATIO = stringPreferencesKey("card_aspect_ratio")
        val CARD_FACE_URI = stringPreferencesKey("card_face_uri")
        val FLASH_ON_TAP = booleanPreferencesKey("flash_on_successful_tap")
        val SOUND_ON_TAP = booleanPreferencesKey("sound_on_successful_tap")
        val VIBRATE_ON_TAP = booleanPreferencesKey("vibrate_on_successful_tap")
    }
}
