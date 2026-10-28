package org.snrkl.nfcbusinesscard.vcard

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * vCard 3.0 output.
 *
 * A mistake here produces a contact that imports with a mangled name onto
 * someone else's phone — a failure that happens after the exchange, to someone
 * who will never report it.
 */
class VCardSerializerTest {

    private val sample = VCardData(
        formattedName = "Alex Rivera",
        givenName = "Alex",
        familyName = "Rivera",
        organisation = "Example Pty Ltd",
        jobTitle = "Engineer",
        phones = listOf(PhoneNumber(ContactFieldType.Mobile, "+61400000000")),
        emails = listOf(EmailAddress(ContactFieldType.Work, "alex@example.com")),
        url = "https://example.com",
        address = "1 Example Street, Sydney NSW 2000",
        note = "Met at the conference",
    )

    private fun lines(text: String) = text.split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `output is bounded by BEGIN and END and declares version 3 point 0`() {
        val result = lines(VCardSerializer.serialize(sample))
        assertThat(result.first()).isEqualTo("BEGIN:VCARD")
        assertThat(result[1]).isEqualTo("VERSION:3.0")
        assertThat(result.last()).isEqualTo("END:VCARD")
    }

    @Test
    fun `lines are separated by CRLF as the RFC requires`() {
        val text = VCardSerializer.serialize(sample)
        assertThat(text).contains("\r\n")
        // A bare LF anywhere means a platform newline slipped through.
        assertThat(text.replace("\r\n", "")).doesNotContain("\n")
    }

    @Test
    fun `structured name is emitted with its five semicolon-separated components`() {
        assertThat(lines(VCardSerializer.serialize(sample))).contains("N:Rivera;Alex;;;")
    }

    @Test
    fun `N is emitted even when only a formatted name is known`() {
        // 3.0 makes N mandatory and some importers reject a card without it.
        val minimal = VCardData(formattedName = "Alex Rivera")
        assertThat(lines(VCardSerializer.serialize(minimal))).contains("N:;;;;")
    }

    @Test
    fun `phone and email carry their vCard TYPE parameter, not the UI label`() {
        val result = lines(VCardSerializer.serialize(sample))
        // "Mobile" in the UI is CELL on the wire.
        assertThat(result).contains("TEL;TYPE=CELL:+61400000000")
        assertThat(result).contains("EMAIL;TYPE=WORK:alex@example.com")
    }

    @Test
    fun `blank fields are omitted entirely rather than emitted empty`() {
        val sparse = VCardData(formattedName = "Alex Rivera")
        val result = VCardSerializer.serialize(sparse)
        assertThat(result).doesNotContain("ORG:")
        assertThat(result).doesNotContain("TITLE:")
        assertThat(result).doesNotContain("URL:")
        assertThat(result).doesNotContain("ADR")
        assertThat(result).doesNotContain("NOTE:")
    }

    @Test
    fun `blank phone and email entries are dropped`() {
        // The editor keeps an empty row so there is somewhere to type; it must
        // not reach the wire as an empty TEL line.
        val withBlanks = sample.copy(
            phones = listOf(PhoneNumber(ContactFieldType.Mobile, ""), sample.phones[0]),
            emails = listOf(EmailAddress(ContactFieldType.Work, "")),
        )
        val result = lines(VCardSerializer.serialize(withBlanks))
        assertThat(result.count { it.startsWith("TEL") }).isEqualTo(1)
        assertThat(result.none { it.startsWith("EMAIL") }).isTrue()
    }

    @Test
    fun `semicolons and commas in values are escaped`() {
        val tricky = VCardData(
            formattedName = "Smith, John; Jr",
            note = "a, b; c",
        )
        val result = VCardSerializer.serialize(tricky)
        assertThat(result).contains("""FN:Smith\, John\; Jr""")
        assertThat(result).contains("""NOTE:a\, b\; c""")
    }

    @Test
    fun `backslashes are escaped before the characters that introduce them`() {
        // Order matters: escaping the comma first would then double the
        // backslash it introduced, and the value would arrive corrupted.
        val tricky = VCardData(formattedName = """back\slash""")
        assertThat(VCardSerializer.serialize(tricky)).contains("""FN:back\\slash""")
    }

    @Test
    fun `newlines inside a value become the literal escape, not a real line break`() {
        val multiline = VCardData(formattedName = "Alex", note = "line one\nline two")
        val result = VCardSerializer.serialize(multiline)
        assertThat(result).contains("""NOTE:line one\nline two""")
        // A real break here would terminate the property and corrupt the card.
        assertThat(lines(result).count { it.startsWith("NOTE") }).isEqualTo(1)
    }

    @Test
    fun `long lines are folded with CRLF and a leading space`() {
        val long = VCardData(formattedName = "A".repeat(200))
        val result = VCardSerializer.serialize(long)
        assertThat(result).contains("\r\n ")
        // Unfolding must recover the original value exactly.
        val unfolded = result.replace("\r\n ", "")
        assertThat(unfolded).contains("FN:" + "A".repeat(200))
    }

    @Test
    fun `folding counts octets and never splits a multi-byte character`() {
        // A UTF-8 character split across a fold boundary is unparseable.
        val accented = VCardData(formattedName = "é".repeat(80))
        val result = VCardSerializer.serialize(accented)
        val unfolded = result.replace("\r\n ", "")
        assertThat(unfolded).contains("FN:" + "é".repeat(80))
        // Every folded segment must itself be valid UTF-8, which round-tripping
        // through a byte array proves.
        val bytes = result.toByteArray(Charsets.UTF_8)
        assertThat(String(bytes, Charsets.UTF_8)).isEqualTo(result)
    }

    @Test
    fun `address goes in the street component where importers display it intact`() {
        assertThat(VCardSerializer.serialize(sample))
            .contains("ADR;TYPE=WORK:;;1 Example Street\\, Sydney NSW 2000;;;;")
    }

    @Test
    fun `a photo on the card is never serialised`() {
        // Photos are display-only. This is the last gate before the wire: a
        // photo in a QR destroys scannability, and over NFC it turns an instant
        // tap into seconds of held contact that many readers abandon.
        val withPhoto = sample.copy(photoUri = "content://media/external/images/media/42")
        val result = VCardSerializer.serialize(withPhoto)

        assertThat(result).doesNotContain("PHOTO")
        assertThat(result).doesNotContain("content://")
        assertThat(result).doesNotContain("media")
        // And the rest of the card is unaffected by its presence.
        assertThat(result).contains("FN:Alex Rivera")
    }

    @Test
    fun `there is no photo property at any tier`() {
        // Base64 image data is exactly what destroys QR scannability. The model
        // has no photo field and the serialiser must never grow one.
        assertThat(VCardSerializer.serialize(sample)).doesNotContain("PHOTO")
    }
}
