package org.snrkl.nfcbusinesscard.data.contacts

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.snrkl.nfcbusinesscard.data.ShareableItemRepository
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository

/** The outcome of importing a card that arrived from outside the app. */
internal sealed interface SharedImportOutcome {
    data class Imported(val label: String) : SharedImportOutcome
    data object NotACard : SharedImportOutcome
    data object Unreadable : SharedImportOutcome
}

/**
 * Handles a vCard that arrived from another app — Contacts' share sheet, or a
 * `.vcf` opened from a file manager.
 *
 * This is the permission-free route into the contact library. The app that
 * shares the card grants read access on the exact URI it hands over, and that
 * grant is sufficient because nothing here derives a different URI from it —
 * which is precisely the mistake that made the in-app contact picker fail.
 *
 * Lives outside any screen because the import can arrive while the app is not
 * running at all.
 */
internal class SharedCardImporter(
    private val itemRepository: ShareableItemRepository,
    private val preferencesRepository: AppPreferencesRepository,
) {

    private val _outcomes = MutableSharedFlow<SharedImportOutcome>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Replay of 1, deliberately.
     *
     * The import runs as the activity is being created, which is before any
     * screen is collecting. Without replay the result would be emitted into
     * nothing and the operator would see a card appear with no acknowledgement.
     */
    val outcomes: SharedFlow<SharedImportOutcome> = _outcomes.asSharedFlow()

    suspend fun importFrom(context: Context, uri: Uri) {
        val outcome = when (val result = ContactImporter.fromVCardFile(context, uri)) {
            is ImportResult.Success -> {
                val data = result.data
                val label = data.formattedName.ifBlank { "Imported card" }
                val id = itemRepository.save(
                    ShareableItem(
                        label = label,
                        type = ShareableItemType.VCard,
                        vCard = data,
                    ),
                )
                // A first item with nothing active would leave the main screen
                // claiming there is no active item straight after an import.
                if (itemRepository.count() == 1) {
                    preferencesRepository.setActiveItemId(id)
                }
                SharedImportOutcome.Imported(label)
            }

            ImportResult.Empty -> SharedImportOutcome.NotACard
            is ImportResult.Failed -> SharedImportOutcome.NotACard
            ImportResult.PermissionDenied -> SharedImportOutcome.Unreadable
        }
        _outcomes.emit(outcome)
    }

    /** Clears the replayed value once a screen has shown it. */
    fun consume() {
        _outcomes.resetReplayCache()
    }
}
