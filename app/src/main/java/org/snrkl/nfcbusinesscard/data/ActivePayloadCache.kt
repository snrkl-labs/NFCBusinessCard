package org.snrkl.nfcbusinesscard.data

import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.ndef.NdefEncoder
import org.snrkl.nfcbusinesscard.vcard.VCardSerializer

/**
 * The Active Item's NDEF file content, pre-encoded and ready to serve.
 *
 * **Why a cache rather than a database read inside the service.**
 * `HostApduService.processCommandApdu` runs on the main thread and is expected
 * to answer within the tap — a reader that waits is a reader that gives up, and
 * blocking the main thread on a database read is exactly how a tap becomes a
 * three-second stall or an ANR. So the encoded bytes are prepared whenever the
 * item or the selection changes, and the service does a volatile read.
 *
 * This still satisfies the requirement that nothing is hardcoded and that
 * switching the Active Item takes effect without a reinstall: the cache is
 * refreshed from the store the moment either changes, well before the next tap.
 *
 * [content] is `@Volatile` because it is written from a coroutine on a
 * background dispatcher and read from the main thread. The reference swap is
 * atomic and the arrays are never mutated in place, so a reader either sees the
 * whole old payload or the whole new one — never a torn mixture of both.
 */
internal object ActivePayloadCache {

    @Volatile
    private var content: ByteArray = NdefEncoder.emptyNdefFileContent()

    /** The label of the item currently cached, for diagnostics and UI. */
    @Volatile
    var activeLabel: String? = null
        private set

    /** Whether there is a real payload to serve, as opposed to an empty tag. */
    @Volatile
    var hasPayload: Boolean = false
        private set

    /** Returns the NDEF file content: NLEN followed by the message. */
    fun ndefFileContent(): ByteArray = content

    /**
     * Re-encodes the cache from [item].
     *
     * A null or unpresentable item yields a valid but empty NDEF file. That is a
     * deliberate choice over refusing to answer: an empty tag is a state a reader
     * understands and reports, whereas silence is indistinguishable from broken
     * hardware.
     */
    fun update(item: ShareableItem?) {
        if (item == null || !item.isPresentable) {
            content = NdefEncoder.emptyNdefFileContent()
            activeLabel = null
            hasPayload = false
            return
        }

        val encoded = try {
            when (item.type) {
                ShareableItemType.Url ->
                    NdefEncoder.ndefFileContent(NdefEncoder.uriRecord(item.url!!))

                ShareableItemType.VCard ->
                    NdefEncoder.ndefFileContent(
                        NdefEncoder.vCardRecord(VCardSerializer.serialize(item.vCard!!)),
                    )
            }
        } catch (e: IllegalArgumentException) {
            // The only expected cause is a payload larger than the Capability
            // Container advertises. Serving an empty tag beats serving a
            // truncated record that a reader will parse into nonsense.
            null
        }

        if (encoded == null) {
            content = NdefEncoder.emptyNdefFileContent()
            activeLabel = item.label
            hasPayload = false
        } else {
            content = encoded
            activeLabel = item.label
            hasPayload = true
        }
    }

    /** Test seam. Resets to the empty-tag state. */
    internal fun reset() {
        content = NdefEncoder.emptyNdefFileContent()
        activeLabel = null
        hasPayload = false
    }
}
