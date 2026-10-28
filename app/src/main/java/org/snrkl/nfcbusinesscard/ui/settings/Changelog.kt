package org.snrkl.nfcbusinesscard.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.snrkl.nfcbusinesscard.R

/**
 * One released version.
 *
 * The release process treats a version bump without a changelog entry as an
 * incomplete release, so this list is part of the release, not documentation
 * about it. It is kept in code rather than a resource so a missing entry is a
 * visible gap next to the version constant rather than a silent one.
 */
internal data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>,
)

/** Newest first, matching the convention used by the project's status files. */
internal val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = "0.04.00",
        date = "2026-10-28",
        changes = listOf(
            "The app is now published on GitHub, and can be installed and kept up to date with Obtainium.",
            "Releases are signed with a dedicated key. Builds before this one used a development key, so they had to be uninstalled before this version would install.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.06",
        date = "2026-09-18",
        changes = listOf(
            "The widget no longer draws a panel behind itself — it sits on your wallpaper, and the NFC and QR buttons carry their own rounded backgrounds.",
            "The widget can now be resized from 5x2 down to 3x2. At the smaller size it shows just the card; tap it to reach send mode and the code.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.05",
        date = "2026-09-18",
        changes = listOf(
            "Removed the NFC section from settings. Its one row could be read but never changed. Sharing still requires the phone to be unlocked, exactly as before.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.04",
        date = "2026-09-18",
        changes = listOf(
            "The app icon is now the real artwork, including a themed version that follows your wallpaper colours on Android 13 and later.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.03",
        date = "2026-09-18",
        changes = listOf(
            "A link's QR code now has the link shown beside it, on the main screen and on the widget, so you can see which link is about to be shared.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.02",
        date = "2026-09-18",
        changes = listOf(
            "Fixed: the NFC and QR icons on the widget were on their side.",
            "Fixed: a link item showed a specimen business card. It now shows its QR code — on the main screen, full size, and on the widget.",
            "The specimen card now appears only when nothing at all has been added yet, so a new install still looks like something.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.01",
        date = "2026-09-11",
        changes = listOf(
            "Fixed: the home screen widget would have failed to draw. The card image was several times larger than a widget is allowed to carry, which shows up as a blank widget rather than an error.",
        ),
    ),
    ChangelogEntry(
        version = "0.03.00",
        date = "2026-09-11",
        changes = listOf(
            "New: a 5x2 home screen widget showing your card, with NFC and QR buttons beside it. Tapping the card opens it full size; NFC opens it with sending already on; QR goes straight to the code.",
            "New: with no card image of your own, the app now draws one from your contact details and photo instead of showing a stand-in.",
            "New: on the QR screen, swipe the details panel to move between your items — useful for sharing a link first and a contact card second.",
            "Contact photos are now brought across when you import from Contacts or from a .vcf file. They stay on the device and are never sent.",
            "Fixed: the add button covered the delete button of the card behind it, and the last card in the list sat underneath it.",
            "Fixed: long email addresses wrapped badly on the QR screen.",
        ),
    ),
    ChangelogEntry(
        version = "0.02.02",
        date = "2026-09-10",
        changes = listOf(
            "Fixed: copying from Contacts never worked. It now asks for the Contacts permission, which Android genuinely requires to read a contact's phone numbers and email addresses.",
            "Added: share a contact straight into this app from the Contacts app, or open a .vcf from a file manager. Neither needs any permission.",
            "The phone and email fields in the vCard editor are wider; the type selector is now a compact chip instead of a second text box.",
            "Renamed: the add menu now reads Create a link and Create a vCard, so creating a new card is clearly different from copying an existing one.",
            "The built-in card image is now real sample artwork rather than the measurement placeholder.",
        ),
    ),
    ChangelogEntry(
        version = "0.02.01",
        date = "2026-09-10",
        changes = listOf(
            "The app is now released under the Apache License 2.0.",
            "Added Settings → Open source licences, listing the third-party components the app is built on.",
        ),
    ),
    ChangelogEntry(
        version = "0.02.00",
        date = "2026-09-10",
        changes = listOf(
            "Tap the card to open it full size, turned a quarter turn so the person beside you can read it. Tap again to turn it the other way. The phone itself does not rotate, so the buttons stay where your thumb is.",
            "The QR view now lists exactly which details the code carries, and updates as you change the detail level.",
            "Contact cards can carry a photo. It is shown in this app only and is never sent over NFC or in a QR code.",
            "Import a contact card from a .vcf file, or copy one from your Contacts. Copying from Contacts needs no permission.",
            "Optional confirmation when a reader takes your card: flash the screen, play a sound, vibrate. Each can be turned on separately in settings.",
            "Name, organisation, job title and address fields now capitalise as you type.",
            "Fixed: the keyboard covered the lower fields when editing a contact card.",
            "The card image now ships real sample artwork rather than the measurement placeholder.",
        ),
    ),
    ChangelogEntry(
        version = "0.01.01",
        date = "2026-09-10",
        changes = listOf(
            "Fixed: the card image did not fill its region correctly, and held its exact size only on one specific screen density. It now maps one image pixel to one screen pixel on any device.",
        ),
    ),
    ChangelogEntry(
        version = "0.01.00",
        date = "2026-09-10",
        changes = listOf(
            "First build.",
            "Share a link or a contact card over NFC by holding the phone against a reader.",
            "Shareable item library: links and contact cards with name, organisation, job title, multiple typed phone numbers and email addresses, website, address and note.",
            "Pick the active item in one tap from the main screen.",
            "Send mode toggle. It only works while this app is open — Android requires it.",
            "QR code view with four detail levels. Swipe between them when a code will not scan.",
            "Brightness slider in the QR view, and the screen stays awake while a code is showing.",
            "Card image region at business-card proportions, with Australian, ISO and US shapes.",
            "Replace the placeholder card image with your own PNG from settings.",
            "Unlocking is required before a tap is answered.",
        ),
    ),
)

@Composable
internal fun ChangelogDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changelog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                CHANGELOG.forEach { entry ->
                    Text(
                        text = "${entry.version} — ${entry.date}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    entry.changes.forEach { change ->
                        Text(
                            text = "•  $change",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
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

