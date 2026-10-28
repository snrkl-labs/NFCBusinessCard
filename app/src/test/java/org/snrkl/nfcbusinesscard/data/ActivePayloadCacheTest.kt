package org.snrkl.nfcbusinesscard.data

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.ndef.NdefEncoder

/**
 * The bridge between the item store and the card-emulation service.
 *
 * Pure Kotlin — it only touches the encoder and the serialiser — so it needs no
 * device.
 */
class ActivePayloadCacheTest {

    @Before
    fun reset() = ActivePayloadCache.reset()

    @Test
    fun `no active item serves a valid empty tag`() {
        ActivePayloadCache.update(null)

        assertThat(ActivePayloadCache.hasPayload).isFalse()
        assertThat(ActivePayloadCache.ndefFileContent()).isEqualTo(byteArrayOf(0x00, 0x00))
    }

    @Test
    fun `a url item is encoded as a uri record`() {
        ActivePayloadCache.update(
            ShareableItem(
                id = 1,
                label = "Card",
                type = ShareableItemType.Url,
                url = "https://example.com/card",
            ),
        )

        assertThat(ActivePayloadCache.hasPayload).isTrue()
        assertThat(ActivePayloadCache.activeLabel).isEqualTo("Card")
        assertThat(ActivePayloadCache.ndefFileContent()).isEqualTo(
            NdefEncoder.ndefFileContent(NdefEncoder.uriRecord("https://example.com/card")),
        )
    }

    @Test
    fun `a contact card is encoded as a text vcard mime record`() {
        ActivePayloadCache.update(
            ShareableItem(
                id = 2,
                label = "Contact",
                type = ShareableItemType.VCard,
                vCard = VCardData(formattedName = "Alex Rivera"),
            ),
        )

        assertThat(ActivePayloadCache.hasPayload).isTrue()
        val content = ActivePayloadCache.ndefFileContent()
        assertThat(String(content, Charsets.UTF_8)).contains("text/vcard")
        assertThat(String(content, Charsets.UTF_8)).contains("BEGIN:VCARD")
    }

    @Test
    fun `an item with no usable content serves an empty tag`() {
        // Saveable but not presentable — a URL item the operator has not
        // finished filling in.
        ActivePayloadCache.update(
            ShareableItem(id = 3, label = "Half done", type = ShareableItemType.Url, url = ""),
        )

        assertThat(ActivePayloadCache.hasPayload).isFalse()
        assertThat(ActivePayloadCache.ndefFileContent()).isEqualTo(byteArrayOf(0x00, 0x00))
    }

    @Test
    fun `an oversized payload falls back to an empty tag rather than truncating`() {
        // A truncated NDEF record parses into nonsense on the reader. An empty
        // tag at least reports itself honestly.
        val huge = "https://example.com/" + "a".repeat(NdefEncoder.MAX_NDEF_MESSAGE_SIZE)
        ActivePayloadCache.update(
            ShareableItem(id = 4, label = "Too big", type = ShareableItemType.Url, url = huge),
        )

        assertThat(ActivePayloadCache.hasPayload).isFalse()
        assertThat(ActivePayloadCache.ndefFileContent()).isEqualTo(byteArrayOf(0x00, 0x00))
        // The label is still reported, so the UI can say which item is at fault.
        assertThat(ActivePayloadCache.activeLabel).isEqualTo("Too big")
    }

    @Test
    fun `switching the active item replaces the served payload`() {
        ActivePayloadCache.update(
            ShareableItem(id = 1, label = "First", type = ShareableItemType.Url, url = "https://one.example"),
        )
        val first = ActivePayloadCache.ndefFileContent()

        ActivePayloadCache.update(
            ShareableItem(id = 2, label = "Second", type = ShareableItemType.Url, url = "https://two.example"),
        )
        val second = ActivePayloadCache.ndefFileContent()

        assertThat(second).isNotEqualTo(first)
        assertThat(ActivePayloadCache.activeLabel).isEqualTo("Second")
    }
}
