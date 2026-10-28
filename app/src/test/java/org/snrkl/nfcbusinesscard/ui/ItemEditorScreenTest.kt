package org.snrkl.nfcbusinesscard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.ui.items.EditorError
import org.snrkl.nfcbusinesscard.ui.items.ItemEditorScreen
import org.snrkl.nfcbusinesscard.ui.items.ItemEditorUiState
import org.snrkl.nfcbusinesscard.ui.theme.NfcBusinessCardTheme

@RunWith(AndroidJUnit4::class)
class ItemEditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: ItemEditorUiState) {
        composeRule.setContent {
            NfcBusinessCardTheme {
                ItemEditorScreen(
                    state = state,
                    onLabelChange = {},
                    onUrlChange = {},
                    onVCardFieldChange = { _, _ -> },
                    onPhoneChange = { _, _ -> },
                    onPhoneTypeChange = { _, _ -> },
                    onAddPhone = {},
                    onRemovePhone = {},
                    onEmailChange = { _, _ -> },
                    onEmailTypeChange = { _, _ -> },
                    onAddEmail = {},
                    onRemoveEmail = {},
                    onPickPhoto = {},
                    onRemovePhoto = {},
                    onSave = {},
                    onCancel = {},
                    onSaved = {},
                )
            }
        }
    }

    @Test
    fun theUrlEditorShowsTheHttpsRequirementUpFront() {
        setContent(
            ItemEditorUiState(
                item = ShareableItem(type = ShareableItemType.Url),
                isNew = true,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("New link").assertIsDisplayed()
        composeRule.onNodeWithText("Must start with https://").assertIsDisplayed()
    }

    @Test
    fun validationErrorsAttachToTheFieldThatFailed() {
        // Errors belong on the field, not in a dialog — the operator should be
        // able to see what to fix and fix it in place.
        setContent(
            ItemEditorUiState(
                item = ShareableItem(type = ShareableItemType.Url),
                isNew = true,
                errors = setOf(EditorError.LabelRequired, EditorError.UrlInvalid),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("A label is required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enter a valid https:// link").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theContactCardEditorExposesTheStructuredFields() {
        // Structured, not an opaque .vcf string — the density tiers are field
        // subsets and a blob cannot be tiered.
        setContent(
            ItemEditorUiState(
                item = ShareableItem(
                    type = ShareableItemType.VCard,
                    vCard = VCardData(),
                ),
                isNew = true,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("New vCard").assertIsDisplayed()
        // The form is longer than the screen, so these are scrolled to rather
        // than asserted in place — the claim is that the field exists, not that
        // it fits above the fold.
        composeRule.onNodeWithText("Display name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Organisation").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Job title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Website").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theEditorOffersAPhotoAndSaysItStaysOnTheDevice() {
        // A contact photo looks exactly like something that would be shared.
        // Someone adding one is entitled to know, at the point of adding it,
        // that it will not be — base64 image data destroys QR scannability, and
        // over NFC it turns an instant tap into seconds of held contact.
        setContent(
            ItemEditorUiState(
                item = ShareableItem(type = ShareableItemType.VCard, vCard = VCardData()),
                isNew = true,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Photo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Shown in this app only. Never sent over NFC or in a QR code.",
        ).performScrollTo().assertIsDisplayed()
    }
}
