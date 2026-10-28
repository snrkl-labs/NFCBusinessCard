package org.snrkl.nfcbusinesscard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.ui.qr.QrScreen
import org.snrkl.nfcbusinesscard.ui.qr.QrUiState
import org.snrkl.nfcbusinesscard.ui.theme.NfcBusinessCardTheme
import org.snrkl.nfcbusinesscard.vcard.DensityTier
import org.snrkl.nfcbusinesscard.ui.qr.QrBitmapRenderer
import org.snrkl.nfcbusinesscard.vcard.TierFieldSelector

/** QR View rendering, which needs a real graphics stack and so runs on device. */
@RunWith(AndroidJUnit4::class)
class QrScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val urlItem = ShareableItem(
        id = 1,
        label = "Hosted vCard",
        type = ShareableItemType.Url,
        url = "https://example.com/card.vcf",
    )

    private val vCardItem = ShareableItem(
        id = 2,
        label = "Work card",
        type = ShareableItemType.VCard,
        vCard = VCardData(
            formattedName = "Alex Rivera",
            organisation = "Example Pty Ltd",
            url = "https://example.com/alex",
        ),
    )

    private fun setContent(state: QrUiState) {
        composeRule.setContent {
            NfcBusinessCardTheme {
                QrScreen(
                    state = state,
                    bitmapProvider = { tier, sizePx ->
                        state.item
                            ?.let { TierFieldSelector.payloadFor(it, tier) }
                            ?.let { QrBitmapRenderer.render(it, sizePx) }
                    },
                    onSelectItem = {},
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun aCodeIsRenderedForTheActiveItem() {
        setContent(
            QrUiState(
                item = urlItem,
                availableTiers = listOf(DensityTier.LinkOnly),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithContentDescription("QR code for the active item").assertIsDisplayed()
    }

    @Test
    fun theTierControlIsHiddenForAUrlItem() {
        // A URL has exactly one representation. Four identical codes would be a
        // control that visibly does nothing.
        setContent(
            QrUiState(
                item = urlItem,
                availableTiers = listOf(DensityTier.LinkOnly),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Minimal").assertDoesNotExist()
        composeRule.onNodeWithText("Standard").assertDoesNotExist()
        composeRule.onNodeWithText("Full").assertDoesNotExist()
    }

    @Test
    fun theTierControlIsShownForAContactCard() {
        setContent(
            QrUiState(
                item = vCardItem,
                availableTiers = DensityTier.ordered,
                initialTier = DensityTier.Minimal,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Link only").assertIsDisplayed()
        composeRule.onNodeWithText("Minimal").assertIsDisplayed()
        composeRule.onNodeWithText("Standard").assertIsDisplayed()
        composeRule.onNodeWithText("Full").assertIsDisplayed()
    }

    @Test
    fun theBrightnessSliderIsReachableByName() {
        // The two glyphs beside it are decorative, so without an explicit name
        // the slider announces itself to TalkBack as an unnamed control.
        setContent(
            QrUiState(
                item = urlItem,
                availableTiers = listOf(DensityTier.LinkOnly),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithContentDescription("Screen brightness").assertIsDisplayed()
    }

    @Test
    fun anItemWithNothingToEncodeSaysSoRatherThanShowingABlankSquare() {
        setContent(QrUiState(item = null, availableTiers = emptyList(), isLoading = false))

        composeRule.onNodeWithText("This item has nothing to encode.").assertIsDisplayed()
    }
}
