package org.snrkl.nfcbusinesscard.vcard

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * Density tier field selection.
 *
 * The tiers only earn their place if dropping one genuinely drops the payload.
 * These tests assert the field boundaries; QrEncoderTest asserts that the
 * boundaries translate into a smaller QR version.
 */
class TierFieldSelectorTest {

    private val full = VCardData(
        formattedName = "Alex Rivera",
        givenName = "Alex",
        familyName = "Rivera",
        organisation = "Example Pty Ltd",
        jobTitle = "Engineer",
        phones = listOf(
            PhoneNumber(ContactFieldType.Mobile, "+61400000000"),
            PhoneNumber(ContactFieldType.Work, "+61299999999"),
        ),
        emails = listOf(
            EmailAddress(ContactFieldType.Work, "alex@example.com"),
            EmailAddress(ContactFieldType.Home, "alex@personal.example"),
        ),
        url = "https://example.com/alex",
        address = "1 Example Street, Sydney NSW 2000",
        note = "Met at the conference",
    )

    private val vCardItem = ShareableItem(
        id = 1,
        label = "Work card",
        type = ShareableItemType.VCard,
        vCard = full,
    )

    private val urlItem = ShareableItem(
        id = 2,
        label = "Hosted vCard",
        type = ShareableItemType.Url,
        url = "https://example.com/alex.vcf",
    )

    @Test
    fun `minimal carries the name and exactly one phone and one email`() {
        val selected = TierFieldSelector.select(full, DensityTier.Minimal)
        assertThat(selected.formattedName).isEqualTo("Alex Rivera")
        assertThat(selected.phones).hasSize(1)
        assertThat(selected.emails).hasSize(1)
        // The first entered number is the primary one and must be the one kept.
        assertThat(selected.phones.first().value).isEqualTo("+61400000000")
        assertThat(selected.organisation).isEmpty()
        assertThat(selected.jobTitle).isEmpty()
        assertThat(selected.url).isEmpty()
        assertThat(selected.address).isEmpty()
        assertThat(selected.note).isEmpty()
    }

    @Test
    fun `standard adds organisation, title and website but no address or note`() {
        val selected = TierFieldSelector.select(full, DensityTier.Standard)
        assertThat(selected.organisation).isEqualTo("Example Pty Ltd")
        assertThat(selected.jobTitle).isEqualTo("Engineer")
        assertThat(selected.url).isEqualTo("https://example.com/alex")
        assertThat(selected.address).isEmpty()
        assertThat(selected.note).isEmpty()
        // Still only the primary contacts.
        assertThat(selected.phones).hasSize(1)
        assertThat(selected.emails).hasSize(1)
    }

    @Test
    fun `full carries everything including secondary contacts`() {
        val selected = TierFieldSelector.select(full, DensityTier.Full)
        assertThat(selected).isEqualTo(full)
        assertThat(selected.phones).hasSize(2)
        assertThat(selected.emails).hasSize(2)
        assertThat(selected.address).isNotEmpty()
        assertThat(selected.note).isNotEmpty()
    }

    @Test
    fun `each tier is a superset of the one below it`() {
        val minimal = TierFieldSelector.select(full, DensityTier.Minimal)
        val standard = TierFieldSelector.select(full, DensityTier.Standard)
        val fullSelected = TierFieldSelector.select(full, DensityTier.Full)

        assertThat(standard.formattedName).isEqualTo(minimal.formattedName)
        assertThat(standard.phones).containsAtLeastElementsIn(minimal.phones)
        assertThat(fullSelected.phones).containsAtLeastElementsIn(standard.phones)
        assertThat(fullSelected.emails).containsAtLeastElementsIn(standard.emails)
    }

    @Test
    fun `a url item collapses to a single tier`() {
        // Four identical codes would be a control that visibly does nothing.
        assertThat(TierFieldSelector.availableTiers(urlItem))
            .containsExactly(DensityTier.LinkOnly)
    }

    @Test
    fun `a url item encodes its url at that tier`() {
        assertThat(TierFieldSelector.payloadFor(urlItem, DensityTier.LinkOnly))
            .isEqualTo("https://example.com/alex.vcf")
    }

    @Test
    fun `a contact card with a website offers all four tiers`() {
        assertThat(TierFieldSelector.availableTiers(vCardItem))
            .containsExactly(
                DensityTier.LinkOnly,
                DensityTier.Minimal,
                DensityTier.Standard,
                DensityTier.Full,
            ).inOrder()
    }

    @Test
    fun `a contact card without a website drops the link-only tier`() {
        // Offering it would give the operator a tier they can swipe into and
        // find empty.
        val noWebsite = vCardItem.copy(vCard = full.copy(url = ""))
        assertThat(TierFieldSelector.availableTiers(noWebsite))
            .containsExactly(DensityTier.Minimal, DensityTier.Standard, DensityTier.Full)
            .inOrder()
    }

    @Test
    fun `link-only on a contact card encodes the website alone, not a vcard`() {
        val payload = TierFieldSelector.payloadFor(vCardItem, DensityTier.LinkOnly)
        assertThat(payload).isEqualTo("https://example.com/alex")
        assertThat(payload).doesNotContain("BEGIN:VCARD")
    }

    @Test
    fun `an item with no usable content encodes to null`() {
        val empty = ShareableItem(type = ShareableItemType.VCard, vCard = VCardData())
        assertThat(TierFieldSelector.payloadFor(empty, DensityTier.Minimal)).isNull()

        val blankUrl = ShareableItem(type = ShareableItemType.Url, url = "  ")
        assertThat(TierFieldSelector.payloadFor(blankUrl, DensityTier.LinkOnly)).isNull()
    }

    @Test
    fun `the shared-field summary matches what the tier actually encodes`() {
        // The panel in the QR view is a promise about what a stranger is about
        // to receive. If it drifts from the payload it is worse than absent.
        listOf(DensityTier.Minimal, DensityTier.Standard, DensityTier.Full).forEach { tier ->
            val payload = TierFieldSelector.payloadFor(vCardItem, tier)!!
            val described = TierFieldSelector.describeShared(vCardItem, tier)

            // The payload escapes commas and semicolons per RFC 2426, so it is
            // unescaped before comparing — the claim is that the field's value
            // is present, not that it is present verbatim.
            val unescaped = payload.replace("\\", "")
            described.forEach { field ->
                assertThat(unescaped).contains(field.value)
            }
        }
    }

    @Test
    fun `the summary grows with the tier`() {
        val minimal = TierFieldSelector.describeShared(vCardItem, DensityTier.Minimal)
        val standard = TierFieldSelector.describeShared(vCardItem, DensityTier.Standard)
        val full = TierFieldSelector.describeShared(vCardItem, DensityTier.Full)

        assertThat(minimal.size).isLessThan(standard.size)
        assertThat(standard.size).isLessThan(full.size)

        // Address and note appear only at Full — the two fields most likely to
        // be regretted, so they must not turn up early.
        assertThat(standard.map { it.kind }).doesNotContain(SharedFieldKind.Address)
        assertThat(standard.map { it.kind }).doesNotContain(SharedFieldKind.Note)
        assertThat(full.map { it.kind }).contains(SharedFieldKind.Address)
        assertThat(full.map { it.kind }).contains(SharedFieldKind.Note)
    }

    @Test
    fun `the summary never mentions a photo`() {
        val withPhoto = vCardItem.copy(vCard = full.copy(photoUri = "content://photo/1"))
        val described = TierFieldSelector.describeShared(withPhoto, DensityTier.Full)
        assertThat(described.none { it.value.contains("content://") }).isTrue()
    }

    @Test
    fun `a url item summarises as a single link`() {
        val described = TierFieldSelector.describeShared(urlItem, DensityTier.LinkOnly)
        assertThat(described).hasSize(1)
        assertThat(described.first().kind).isEqualTo(SharedFieldKind.Link)
        assertThat(described.first().value).isEqualTo("https://example.com/alex.vcf")
    }

    @Test
    fun `payloads get strictly longer as tiers get denser`() {
        val sizes = listOf(DensityTier.Minimal, DensityTier.Standard, DensityTier.Full)
            .map { TierFieldSelector.payloadFor(vCardItem, it)!!.length }

        assertThat(sizes).isInOrder()
        assertThat(sizes[0]).isLessThan(sizes[1])
        assertThat(sizes[1]).isLessThan(sizes[2])
    }
}
