package org.snrkl.nfcbusinesscard.qr

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.vcard.DensityTier
import org.snrkl.nfcbusinesscard.vcard.TierFieldSelector

/**
 * QR encoding, and the claim the whole tier feature rests on.
 *
 * ZXing core is pure JVM, so this needs no device and no Robolectric.
 */
class QrEncoderTest {

    private val item = ShareableItem(
        id = 1,
        label = "Work card",
        type = ShareableItemType.VCard,
        vCard = VCardData(
            formattedName = "Alex Rivera",
            givenName = "Alex",
            familyName = "Rivera",
            organisation = "Example Pty Ltd",
            jobTitle = "Senior Engineer",
            phones = listOf(
                PhoneNumber(ContactFieldType.Mobile, "+61400000000"),
                PhoneNumber(ContactFieldType.Work, "+61299999999"),
            ),
            emails = listOf(
                EmailAddress(ContactFieldType.Work, "alex.rivera@example.com"),
                EmailAddress(ContactFieldType.Home, "alex@personal.example"),
            ),
            url = "https://example.com/alex",
            address = "Level 5, 1 Example Street, Sydney NSW 2000, Australia",
            note = "Met at the Sydney conference, September",
        ),
    )

    @Test
    fun `a short payload encodes to a low version`() {
        val version = QrEncoder.versionFor("https://example.com")
        assertThat(version).isNotNull()
        assertThat(version!!).isAtMost(4)
    }

    @Test
    fun `dropping a tier drops the QR version`() {
        // This is the entire justification for the tier feature. If a sparser
        // tier does not produce a physically larger module grid, swiping buys
        // the operator nothing when a code will not scan.
        val versions = listOf(
            DensityTier.LinkOnly,
            DensityTier.Minimal,
            DensityTier.Standard,
            DensityTier.Full,
        ).map { tier ->
            val payload = TierFieldSelector.payloadFor(item, tier)
            assertThat(payload).isNotNull()
            QrEncoder.versionFor(payload!!)!!
        }

        assertThat(versions).isInOrder()
        assertThat(versions.first()).isLessThan(versions.last())
        // Every step must actually move, not just the ends.
        versions.zipWithNext().forEach { (sparser, denser) ->
            assertThat(sparser).isAtMost(denser)
        }
    }

    @Test
    fun `the link-only tier is dramatically sparser than the full card`() {
        val linkOnly = QrEncoder.versionFor(
            TierFieldSelector.payloadFor(item, DensityTier.LinkOnly)!!,
        )!!
        val fullCard = QrEncoder.versionFor(
            TierFieldSelector.payloadFor(item, DensityTier.Full)!!,
        )!!
        // A modest difference would not be worth a swipe gesture.
        assertThat(fullCard - linkOnly).isAtLeast(5)
    }

    @Test
    fun `matrix side length matches the encoded version`() {
        val matrix = QrEncoder.encode("https://example.com")!!
        val version = QrEncoder.versionFor("https://example.com")!!
        assertThat(matrix.width).isEqualTo(17 + 4 * version)
        assertThat(matrix.height).isEqualTo(matrix.width)
    }

    @Test
    fun `empty content encodes to nothing rather than an empty code`() {
        assertThat(QrEncoder.encode("")).isNull()
        assertThat(QrEncoder.versionFor("")).isNull()
    }

    @Test
    fun `a payload beyond QR capacity returns null rather than throwing`() {
        // The view turns null into "drop a tier", which is recoverable. An
        // exception here would take the screen down.
        val enormous = "a".repeat(10_000)
        assertThat(QrEncoder.encode(enormous)).isNull()
    }

    @Test
    fun `no quiet zone is baked into the matrix`() {
        // The Compose layer supplies the quiet zone as padding, so the matrix
        // must be modules only. A baked-in margin would be counted twice and
        // shrink the modules for no reason.
        val matrix = QrEncoder.encode("https://example.com")!!
        val hasDarkOnFirstRow = (0 until matrix.width).any { matrix.get(it, 0) }
        assertThat(hasDarkOnFirstRow).isTrue()
    }

    @Test
    fun `unicode content survives encoding`() {
        assertThat(QrEncoder.encode("https://exämple.com/ünïcode")).isNotNull()
    }
}
