package org.snrkl.nfcbusinesscard.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio
import org.snrkl.nfcbusinesscard.ui.main.CardFace
import org.snrkl.nfcbusinesscard.ui.main.GeneratedCardAspect
import org.snrkl.nfcbusinesscard.ui.main.toGenerated
import org.snrkl.nfcbusinesscard.ui.theme.NfcBusinessCardTheme
import kotlin.math.abs

/**
 * Card Face geometry, measured on a real device.
 *
 * The claim being checked is the one the published artwork dimensions rest on:
 * that the region holds its aspect ratio exactly. If it does not, artwork
 * exported at 1242 x 759 is resampled and small type goes soft.
 */
@RunWith(AndroidJUnit4::class)
class CardFaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theCardHoldsItsAspectRatioAtTheDesignWidth() {
        val aspect = CardAspectRatio.Australian
        val geometry = aspect.toGenerated()

        composeRule.setContent {
            NfcBusinessCardTheme {
                CardFace(
                    aspectRatio = aspect,
                    cardFaceUri = null,
                    modifier = Modifier.width(GeneratedCardAspect.CARD_WIDTH_DP.dp),
                )
            }
        }

        val bounds = composeRule
            .onNodeWithContentDescription("Your business card")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        val measuredRatio = bounds.width / bounds.height
        // A dp is not a whole number of pixels at every density, so a small
        // tolerance is honest; anything larger means the ratio is actually wrong.
        assertRatio(measuredRatio, geometry.ratio, tolerance = 0.01f)
    }

    // One setContent per test, so each shape gets its own test rather than a
    // loop that re-renders.
    @Test
    fun theIsoCardHoldsItsRatio() = assertShapeHoldsRatio(CardAspectRatio.Iso)

    @Test
    fun theUsCardHoldsItsRatio() = assertShapeHoldsRatio(CardAspectRatio.Us)

    private fun assertShapeHoldsRatio(aspect: CardAspectRatio) {
        val geometry = aspect.toGenerated()
        composeRule.setContent {
            NfcBusinessCardTheme {
                CardFace(
                    aspectRatio = aspect,
                    cardFaceUri = null,
                    modifier = Modifier.width(GeneratedCardAspect.CARD_WIDTH_DP.dp),
                )
            }
        }

        val bounds = composeRule
            .onNodeWithContentDescription("Your business card")
            .fetchSemanticsNode()
            .boundsInRoot

        assertRatio(bounds.width / bounds.height, geometry.ratio, tolerance = 0.01f)
    }

    @Test
    fun theGeneratedGeometryMatchesTheDimensionsPublishedToTheOperator() {
        // These are the numbers in the release notes and the README. If the
        // generator ever drifts from them, artwork exported against the
        // published figures would be resampled — so they are pinned here too.
        assertEqualsInt(GeneratedCardAspect.CARD_WIDTH_PX, 1242)
        assertEqualsInt(GeneratedCardAspect.CARD_WIDTH_DP, 414)
        assertEqualsInt(GeneratedCardAspect.Australian.heightPx, 759)
        assertEqualsInt(GeneratedCardAspect.Iso.heightPx, 784)
        assertEqualsInt(GeneratedCardAspect.Us.heightPx, 710)

        // 1242 x 759 must be exactly 18:11, or the ratio carries a rounding
        // error that shows as a seam along one edge.
        assertEqualsInt(GeneratedCardAspect.CARD_WIDTH_PX % 18, 0)
        assertEqualsInt(GeneratedCardAspect.Australian.heightPx % 11, 0)
    }

    @Test
    fun aUrlItemShowsItsQrCodeRatherThanACard() {
        // A link has no details to draw a card from. Showing a specimen card for
        // one was the "pretend card" complaint: it looked like somebody's card
        // while carrying nobody's information.
        composeRule.setContent {
            NfcBusinessCardTheme {
                CardFace(
                    aspectRatio = CardAspectRatio.Australian,
                    cardFaceUri = null,
                    modifier = Modifier.width(GeneratedCardAspect.CARD_WIDTH_DP.dp),
                    activeItem = ShareableItem(
                        id = 1,
                        label = "Hosted vCard",
                        type = ShareableItemType.Url,
                        url = "https://example.com/card.vcf",
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("QR code for the active item").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Your business card").assertDoesNotExist()
    }

    @Test
    fun aUrlItemShowsTheLinkBesideItsCode() {
        // A QR is square and the card region is landscape, so a centred code
        // leaves most of the card empty. The link fills that space and says
        // *which* link is about to be shared, rather than only that something
        // is. The scheme is dropped; the path is not.
        composeRule.setContent {
            NfcBusinessCardTheme {
                CardFace(
                    aspectRatio = CardAspectRatio.Australian,
                    cardFaceUri = null,
                    modifier = Modifier.width(GeneratedCardAspect.CARD_WIDTH_DP.dp),
                    activeItem = ShareableItem(
                        id = 1,
                        label = "Hosted vCard",
                        type = ShareableItemType.Url,
                        url = "https://example.com/card.vcf",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("example.com/card.vcf").assertIsDisplayed()
    }

    @Test
    fun anEmptyLibraryStillShowsTheSpecimenCard() {
        // With nothing configured the specimen is the right thing to draw: it
        // makes a fresh install look like something rather than an empty
        // rectangle. Nothing is active in that state, so it cannot be mistaken
        // for a card that is about to be shared.
        composeRule.setContent {
            NfcBusinessCardTheme {
                CardFace(
                    aspectRatio = CardAspectRatio.Australian,
                    cardFaceUri = null,
                    modifier = Modifier.width(GeneratedCardAspect.CARD_WIDTH_DP.dp),
                    activeItem = null,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Your business card").assertIsDisplayed()
    }

    private fun assertRatio(actual: Float, expected: Float, tolerance: Float) {
        check(abs(actual - expected) <= tolerance) {
            "aspect ratio was $actual, expected $expected (tolerance $tolerance)"
        }
    }

    private fun assertEqualsInt(actual: Int, expected: Int) {
        check(actual == expected) { "expected $expected but was $actual" }
    }
}
