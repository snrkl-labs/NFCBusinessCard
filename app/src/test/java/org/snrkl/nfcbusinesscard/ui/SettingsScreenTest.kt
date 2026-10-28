package org.snrkl.nfcbusinesscard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.snrkl.nfcbusinesscard.BuildConfig
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferences
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio
import org.snrkl.nfcbusinesscard.ui.settings.SettingsScreen
import org.snrkl.nfcbusinesscard.ui.theme.NfcBusinessCardTheme
import org.snrkl.nfcbusinesscard.vcard.DensityTier

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        preferences: AppPreferences = AppPreferences(),
        onDefaultTierChange: (DensityTier) -> Unit = {},
    ) {
        composeRule.setContent {
            NfcBusinessCardTheme {
                SettingsScreen(
                    preferences = preferences,
                    onBack = {},
                    onManageItems = {},
                    onPickCardFace = {},
                    onClearCardFace = {},
                    onDefaultTierChange = onDefaultTierChange,
                    onMaxBrightnessChange = {},
                    onAspectRatioChange = {},
                    onShowChangelog = {},
                    onShowLicences = {},
                    onFlashOnTapChange = {},
                    onSoundOnTapChange = {},
                    onVibrateOnTapChange = {},
                )
            }
        }
    }

    @Test
    fun everySettingsSectionTheMvpNeedsIsPresent() {
        setContent()

        // The screen scrolls, so the later sections are scrolled to. The claim
        // is that every section the MVP needs exists, not that they all fit on
        // one screen.
        composeRule.onNodeWithText("Shareable items").assertIsDisplayed()
        composeRule.onNodeWithText("Card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("QR code").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("About").performScrollTo().assertIsDisplayed()

        // No NFC section. Its only row reported requireDeviceUnlock, a manifest
        // attribute with no runtime API — a setting the operator could read and
        // never change, which is a row that answers a question nobody asked.
        composeRule.onNodeWithText("NFC").assertDoesNotExist()
    }

    // setContent may only be called once per test, so the two states are two
    // tests rather than one with a re-render.
    @Test
    fun theCardImageRowReportsThePlaceholderWhenNoImageIsChosen() {
        setContent(AppPreferences(cardFaceUri = null))
        composeRule.onNodeWithText("Drawn from your contact details").assertIsDisplayed()
    }

    @Test
    fun theCardImageRowReportsACustomImageWhenOneIsChosen() {
        setContent(AppPreferences(cardFaceUri = "content://example/card.png"))
        composeRule.onNodeWithText("Using your own image").assertIsDisplayed()
    }

    @Test
    fun theCardShapeShowsTheSelectedRatio() {
        setContent(AppPreferences(cardAspectRatio = CardAspectRatio.Australian))
        composeRule.onNodeWithText("Australian — 90 × 55").assertIsDisplayed()
    }

    @Test
    fun choosingADefaultTierOpensASingleChoiceDialog() {
        val chosen = mutableListOf<DensityTier>()
        setContent(onDefaultTierChange = { chosen.add(it) })

        composeRule.onNodeWithText("Opens at").performScrollTo().performClick()
        composeRule.onNodeWithText("Standard").assertIsDisplayed()
        composeRule.onNodeWithText("Standard").performClick()

        check(chosen == listOf(DensityTier.Standard)) { "expected Standard, got $chosen" }
    }

    @Test
    fun theVersionIsShown() {
        setContent()
        composeRule.onNodeWithText("Version").performScrollTo().assertIsDisplayed()
        // Read from BuildConfig rather than hardcoded: a literal here turns
        // every version bump into a spurious test failure, which trains you to
        // ignore it. The claim is that the screen shows the version it was
        // built with, not that the version is any particular string.
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).performScrollTo().assertIsDisplayed()
    }
}
