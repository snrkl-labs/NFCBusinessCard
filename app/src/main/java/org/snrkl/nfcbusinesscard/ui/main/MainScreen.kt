package org.snrkl.nfcbusinesscard.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.hce.NfcAvailability

/**
 * The widest the content column is allowed to get.
 *
 * Phones are unaffected. On a tablet or an unfolded foldable this keeps the
 * controls at a readable, reachable width instead of letting them span the
 * display, per MD3's guidance on large window sizes.
 */
private val CONTENT_MAX_WIDTH = 600.dp

/**
 * The main screen.
 *
 * Layout follows the spec: a business-card-aspect region at the top holding the
 * Card Face, and the remainder given to the controls — Send Mode, Show QR, and
 * the Active Item picker. Everything is on an 8dp spacing grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    onSendModeChange: (Boolean) -> Unit,
    onSetActiveItem: (Long) -> Unit,
    onShowQr: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddItem: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenFullCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Tap confirmation. The operator usually cannot see the screen during a tap,
    // so each channel is independently switchable in settings.
    val flashColour = rememberTapFlashOverlay(
        trigger = state.successfulTapCount,
        enabled = state.flashOnTap,
    )
    TapAudioHapticFeedback(
        trigger = state.successfulTapCount,
        soundEnabled = state.soundOnTap,
        vibrateEnabled = state.vibrateOnTap,
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cap the content width and centre it. Without this the controls
            // stretch the full width of a tablet or an unfolded foldable, which
            // MD3's layout guidance calls out specifically — a button spanning
            // 800dp is neither readable nor reachable.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            CardFace(
                aspectRatio = state.cardAspectRatio,
                cardFaceUri = state.cardFaceUri,
                vCard = state.activeItem?.vCard,
                activeItem = state.activeItem,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.main_open_full_card),
                    onClick = onOpenFullCard,
                ),
            )

            if (state.nfcAvailability != NfcAvailability.Available) {
                NfcWarningCard(
                    availability = state.nfcAvailability,
                    onOpenNfcSettings = onOpenNfcSettings,
                )
            }

            if (!state.hasItems) {
                EmptyLibraryCard(onAddItem = onAddItem)
            } else {
                ActiveItemCard(
                    activeItem = state.activeItem,
                    onChangeClick = { pickerVisible = true },
                )

                SendModeCard(
                    enabled = state.canUseSendMode,
                    active = state.sendModeActive,
                    claimFailed = state.sendModeClaimFailed,
                    onChange = onSendModeChange,
                )

                FilledTonalButton(
                    onClick = onShowQr,
                    enabled = state.canShowQr,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.main_show_qr))
                }
            }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    TapFlashOverlay(colour = flashColour)

    if (pickerVisible) {
        ModalBottomSheet(
            onDismissRequest = { pickerVisible = false },
            sheetState = sheetState,
        ) {
            ActiveItemPicker(
                items = state.items,
                activeItemId = state.activeItem?.id,
                onSelect = { id ->
                    onSetActiveItem(id)
                    pickerVisible = false
                },
            )
        }
    }
}

@Composable
private fun ActiveItemCard(
    activeItem: ShareableItem?,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(activeItem?.label ?: stringResource(R.string.main_no_active_item))
            },
            overlineContent = { Text(stringResource(R.string.main_active_item)) },
            supportingContent = {
                val detail = when {
                    activeItem == null -> null
                    activeItem.type == ShareableItemType.Url -> activeItem.url
                    else -> activeItem.vCard?.formattedName
                }
                if (!detail.isNullOrBlank()) Text(detail)
            },
            leadingContent = {
                Icon(
                    imageVector = when (activeItem?.type) {
                        ShareableItemType.VCard -> Icons.Default.Person
                        else -> Icons.Default.Share
                    },
                    contentDescription = null,
                )
            },
            trailingContent = {
                TextButton(onClick = onChangeClick) {
                    Text(stringResource(R.string.main_change_item))
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun SendModeCard(
    enabled: Boolean,
    active: Boolean,
    claimFailed: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.main_send_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            if (active) R.string.main_send_mode_on else R.string.main_send_mode_off,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = onChange,
                    enabled = enabled,
                )
            }

            // setPreferredService() requires a resumed activity, so the toggle
            // genuinely cannot persist in the background. Saying so is better
            // than letting the operator discover it at a conference.
            if (active) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.main_send_mode_foreground_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (claimFailed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.main_send_mode_claim_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun NfcWarningCard(
    availability: NfcAvailability,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.size(16.dp))
                Text(
                    text = stringResource(
                        if (availability == NfcAvailability.NoHardware) {
                            R.string.nfc_unavailable_title
                        } else {
                            R.string.nfc_disabled_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (availability == NfcAvailability.NoHardware) {
                        R.string.nfc_unavailable_body
                    } else {
                        R.string.nfc_disabled_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (availability == NfcAvailability.Disabled) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenNfcSettings) {
                    Text(stringResource(R.string.nfc_open_settings))
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.main_no_active_item),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.main_no_active_item_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddItem) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.main_add_first_item))
            }
        }
    }
}

/**
 * The Active Item picker.
 *
 * A bottom sheet reachable in one tap from the main screen — the spec is
 * explicit that selection must not be buried in settings, because it is the
 * thing the operator does most often and usually while someone is waiting.
 */
@Composable
private fun ActiveItemPicker(
    items: List<ShareableItem>,
    activeItemId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.main_active_item),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        items.forEach { item ->
            ListItem(
                headlineContent = { Text(item.label) },
                supportingContent = {
                    val detail = when (item.type) {
                        ShareableItemType.Url -> item.url
                        ShareableItemType.VCard -> item.vCard?.formattedName
                    }
                    if (!detail.isNullOrBlank()) Text(detail)
                },
                leadingContent = {
                    RadioButton(
                        selected = item.id == activeItemId,
                        // Null so the whole row carries the click and the radio
                        // is not a second, smaller target for the same action.
                        onClick = null,
                    )
                },
                // selectable() on the row rather than clickable() on the radio:
                // it gives the whole list item the touch target and announces
                // itself to TalkBack as a radio button with a selected state,
                // which a bare clickable row does not.
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = item.id == activeItemId,
                        role = Role.RadioButton,
                        onClick = { onSelect(item.id) },
                    ),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
