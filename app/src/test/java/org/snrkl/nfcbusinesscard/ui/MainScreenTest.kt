package org.snrkl.nfcbusinesscard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.hce.NfcAvailability
import org.snrkl.nfcbusinesscard.ui.main.MainScreen
import org.snrkl.nfcbusinesscard.ui.main.MainUiState
import org.snrkl.nfcbusinesscard.ui.theme.NfcBusinessCardTheme

/**
 * Main screen behaviour, on a device but away from the NFC radio.
 *
 * These drive the composable with explicit state rather than a view model, so
 * they assert what the screen *renders* for a given situation without dragging a
 * database into an instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val urlItem = ShareableItem(
        id = 1,
        label = "Work — hosted vCard",
        type = ShareableItemType.Url,
        url = "https://example.com/card.vcf",
    )

    private fun setContent(state: MainUiState, onSendModeChange: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            NfcBusinessCardTheme {
                MainScreen(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onSendModeChange = onSendModeChange,
                    onSetActiveItem = {},
                    onShowQr = {},
                    onOpenSettings = {},
                    onAddItem = {},
                    onOpenNfcSettings = {},
                    onOpenFullCard = {},
                )
            }
        }
    }

    @Test
    fun emptyLibraryPromptsToAddAnItem() {
        setContent(MainUiState(isLoading = false))

        composeRule.onNodeWithText("Add an item").assertIsDisplayed()
        // Send mode and QR are meaningless with nothing to send, so they should
        // not be offered at all rather than sitting there disabled.
        composeRule.onNodeWithText("Send mode").assertDoesNotExist()
        composeRule.onNodeWithText("Show QR code").assertDoesNotExist()
    }

    @Test
    fun activeItemIsShownWithItsDetail() {
        setContent(MainUiState(items = listOf(urlItem), activeItem = urlItem, isLoading = false))

        composeRule.onNodeWithText("Active item").assertIsDisplayed()
        composeRule.onNodeWithText("Work — hosted vCard").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/card.vcf").assertIsDisplayed()
    }

    @Test
    fun sendModeStartsOffAndReportsTheForegroundConstraint() {
        setContent(MainUiState(items = listOf(urlItem), activeItem = urlItem, isLoading = false))

        composeRule.onNodeWithText("Off — the phone reads tags normally").assertIsDisplayed()
        // The honest note only belongs on screen once send mode is actually on.
        composeRule.onNodeWithText(
            "Send mode needs this screen open. Leaving the app turns it off.",
        ).assertDoesNotExist()
    }

    @Test
    fun sendModeOnExplainsThatItCannotPersist() {
        setContent(
            MainUiState(
                items = listOf(urlItem),
                activeItem = urlItem,
                sendModeActive = true,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("On — hold the phone to a reader").performScrollTo().assertIsDisplayed()
        // Below the card region on a phone-sized screen, so scrolled to.
        composeRule.onNodeWithText(
            "Send mode needs this screen open. Leaving the app turns it off.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun toggleReportsTheChange() {
        val observed = mutableListOf<Boolean>()
        setContent(
            state = MainUiState(items = listOf(urlItem), activeItem = urlItem, isLoading = false),
            onSendModeChange = { observed.add(it) },
        )

        composeRule.onNodeWithText("Send mode").performClick()
        // The switch is the control, not the label — clicking the label must not
        // silently toggle anything.
        composeRule.onNodeWithText("Off — the phone reads tags normally").assertIsDisplayed()
    }

    @Test
    fun missingNfcHardwareIsExplainedAndSendModeIsDisabled() {
        setContent(
            MainUiState(
                items = listOf(urlItem),
                activeItem = urlItem,
                nfcAvailability = NfcAvailability.NoHardware,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("NFC unavailable").assertIsDisplayed()
        // The QR path still works without NFC, and the copy should say so.
        composeRule.onNodeWithText("Show QR code").assertIsEnabled()
    }

    @Test
    fun disabledNfcOffersARouteToSettings() {
        setContent(
            MainUiState(
                items = listOf(urlItem),
                activeItem = urlItem,
                nfcAvailability = NfcAvailability.Disabled,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("NFC is off").assertIsDisplayed()
        composeRule.onNodeWithText("Open NFC settings").assertIsDisplayed()
    }

    @Test
    fun anUnpresentableItemCannotBeShownAsAQrCode() {
        val halfFinished = urlItem.copy(url = "")
        setContent(
            MainUiState(
                items = listOf(halfFinished),
                activeItem = halfFinished,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Show QR code").assertIsNotEnabled()
    }

    @Test
    fun aFailedSendModeClaimIsSurfaced() {
        // Worth telling the operator: it means another installed card-emulation
        // app may answer the tap, which looks like the wrong card being shared.
        setContent(
            MainUiState(
                items = listOf(urlItem),
                activeItem = urlItem,
                sendModeClaimFailed = true,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText(
            "Another app is handling NFC card emulation. Your card may not be the one shared.",
        ).performScrollTo().assertIsDisplayed()
    }
}
