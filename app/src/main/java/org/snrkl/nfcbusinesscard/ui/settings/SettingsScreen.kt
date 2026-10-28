package org.snrkl.nfcbusinesscard.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.unit.dp
import org.snrkl.nfcbusinesscard.BuildConfig
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferences
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio
import org.snrkl.nfcbusinesscard.ui.qr.labelRes
import org.snrkl.nfcbusinesscard.vcard.DensityTier

/**
 * The settings screen.
 *
 * Built on MD3 list patterns — `ListItem` with headline, supporting and trailing
 * slots, grouped under section headers with dividers between groups. Deliberately
 * not a stack of ad-hoc dialogs: choices with a small fixed set of options open a
 * single-choice dialog, and booleans are inline switches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onManageItems: () -> Unit,
    onPickCardFace: () -> Unit,
    onClearCardFace: () -> Unit,
    onDefaultTierChange: (DensityTier) -> Unit,
    onMaxBrightnessChange: (Boolean) -> Unit,
    onAspectRatioChange: (CardAspectRatio) -> Unit,
    onShowChangelog: () -> Unit,
    onShowLicences: () -> Unit,
    onFlashOnTapChange: (Boolean) -> Unit,
    onSoundOnTapChange: (Boolean) -> Unit,
    onVibrateOnTapChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tierDialogVisible by remember { mutableStateOf(false) }
    var aspectDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
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
        ) {
            SectionHeader(stringResource(R.string.settings_items_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_manage_items)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_manage_items_supporting))
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onManageItems),
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_card_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_card_face)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (preferences.cardFaceUri == null) {
                                R.string.settings_card_face_placeholder
                            } else {
                                R.string.settings_card_face_custom
                            },
                        ),
                    )
                },
                trailingContent = {
                    if (preferences.cardFaceUri != null) {
                        TextButton(onClick = onClearCardFace) {
                            Text(stringResource(R.string.settings_card_face_clear))
                        }
                    }
                },
                modifier = Modifier.clickable(onClick = onPickCardFace),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_card_aspect)) },
                supportingContent = { Text(stringResource(preferences.cardAspectRatio.labelRes)) },
                modifier = Modifier.clickable { aspectDialogVisible = true },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_qr_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_default_tier)) },
                supportingContent = {
                    Text(stringResource(preferences.defaultDensityTier.labelRes))
                },
                modifier = Modifier.clickable { tierDialogVisible = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_max_brightness)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_max_brightness_supporting))
                },
                trailingContent = {
                    Switch(
                        checked = preferences.maxBrightnessInQrView,
                        onCheckedChange = onMaxBrightnessChange,
                    )
                },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_feedback_section))
            ListItem(
                supportingContent = {
                    Text(stringResource(R.string.settings_feedback_supporting))
                },
                headlineContent = {},
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_flash_on_tap)) },
                trailingContent = {
                    Switch(
                        checked = preferences.flashOnSuccessfulTap,
                        onCheckedChange = onFlashOnTapChange,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sound_on_tap)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_sound_on_tap_supporting))
                },
                trailingContent = {
                    Switch(
                        checked = preferences.soundOnSuccessfulTap,
                        onCheckedChange = onSoundOnTapChange,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_vibrate_on_tap)) },
                trailingContent = {
                    Switch(
                        checked = preferences.vibrateOnSuccessfulTap,
                        onCheckedChange = onVibrateOnTapChange,
                    )
                },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_about_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_changelog)) },
                modifier = Modifier.clickable(onClick = onShowChangelog),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licences)) },
                modifier = Modifier.clickable(onClick = onShowLicences),
            )
        }
    }

    if (tierDialogVisible) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_default_tier),
            options = DensityTier.ordered,
            selected = preferences.defaultDensityTier,
            label = { stringResource(it.labelRes) },
            onSelect = {
                onDefaultTierChange(it)
                tierDialogVisible = false
            },
            onDismiss = { tierDialogVisible = false },
        )
    }

    if (aspectDialogVisible) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_card_aspect),
            options = CardAspectRatio.entries.toList(),
            selected = preferences.cardAspectRatio,
            label = { stringResource(it.labelRes) },
            onSelect = {
                onAspectRatioChange(it)
                aspectDialogVisible = false
            },
            onDismiss = { aspectDialogVisible = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * A single-choice dialog.
 *
 * `selectable` with `Role.RadioButton` on the row rather than a click on the
 * radio itself: the whole row becomes the 48dp touch target and TalkBack
 * announces the selected state.
 */
@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(label(option)) },
                        leadingContent = {
                            RadioButton(selected = option == selected, onClick = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}

/** Display names for the card shapes. */
internal val CardAspectRatio.labelRes: Int
    get() = when (this) {
        CardAspectRatio.Iso -> R.string.aspect_iso
        CardAspectRatio.Australian -> R.string.aspect_au
        CardAspectRatio.Us -> R.string.aspect_us
    }
