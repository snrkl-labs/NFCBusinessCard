package org.snrkl.nfcbusinesscard.vcard

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * `.vcf` import.
 *
 * Real files come from Outlook, Google Contacts, iOS and a long tail of tools
 * that each read the spec slightly differently, so the cases here are the shapes
 * that actually turn up rather than the shapes the RFC describes.
 */
class VCardParserTest {

    private fun vcard(vararg lines: String) =
        (listOf("BEGIN:VCARD", "VERSION:3.0") + lines + "END:VCARD").joinToString("\r\n")

    @Test
    fun `parses the fields this app models`() {
        val parsed = VCardParser.parseFirst(
            vcard(
                "N:Rivera;Alex;;;",
                "FN:Alex Rivera",
                "ORG:Example Pty Ltd",
                "TITLE:Engineer",
                "TEL;TYPE=CELL:+61400000000",
                "EMAIL;TYPE=WORK:alex@example.com",
                "URL:https://example.com/alex",
                "NOTE:Met at the conference",
            ),
        )

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.formattedName).isEqualTo("Alex Rivera")
        assertThat(parsed.givenName).isEqualTo("Alex")
        assertThat(parsed.familyName).isEqualTo("Rivera")
        assertThat(parsed.organisation).isEqualTo("Example Pty Ltd")
        assertThat(parsed.jobTitle).isEqualTo("Engineer")
        assertThat(parsed.url).isEqualTo("https://example.com/alex")
        assertThat(parsed.note).isEqualTo("Met at the conference")
        assertThat(parsed.phones).containsExactly(
            PhoneNumber(ContactFieldType.Mobile, "+61400000000"),
        )
        assertThat(parsed.emails).containsExactly(
            EmailAddress(ContactFieldType.Work, "alex@example.com"),
        )
    }

    @Test
    fun `round-trips what the serialiser produces`() {
        // The strongest single guarantee: anything this app can write, it can
        // read back unchanged.
        val original = VCardData(
            formattedName = "Alex Rivera",
            givenName = "Alex",
            familyName = "Rivera",
            organisation = "Example Pty Ltd",
            jobTitle = "Senior Engineer",
            phones = listOf(
                PhoneNumber(ContactFieldType.Mobile, "+61400000000"),
                PhoneNumber(ContactFieldType.Work, "+61299999999"),
            ),
            emails = listOf(EmailAddress(ContactFieldType.Work, "alex@example.com")),
            url = "https://example.com/alex",
            address = "1 Example Street, Sydney NSW 2000",
            note = "Met at the conference",
        )

        val parsed = VCardParser.parseFirst(VCardSerializer.serialize(original))

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.formattedName).isEqualTo(original.formattedName)
        assertThat(parsed.givenName).isEqualTo(original.givenName)
        assertThat(parsed.familyName).isEqualTo(original.familyName)
        assertThat(parsed.organisation).isEqualTo(original.organisation)
        assertThat(parsed.jobTitle).isEqualTo(original.jobTitle)
        assertThat(parsed.url).isEqualTo(original.url)
        assertThat(parsed.note).isEqualTo(original.note)
        assertThat(parsed.phones).isEqualTo(original.phones)
        assertThat(parsed.emails).isEqualTo(original.emails)
        assertThat(parsed.address).isEqualTo(original.address)
    }

    @Test
    fun `unfolds continuation lines`() {
        // Folding happens at 75 octets, so any long note arrives folded.
        // Missing this truncates values at apparently arbitrary points.
        val folded = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Alex Rivera\r\nNOTE:This note is long en\r\n ough to have been folded\r\nEND:VCARD"
        val parsed = VCardParser.parseFirst(folded)
        assertThat(parsed!!.note).isEqualTo("This note is long enough to have been folded")
    }

    @Test
    fun `unescapes commas and semicolons`() {
        val parsed = VCardParser.parseFirst(vcard("""FN:Smith\, John\; Jr"""))
        assertThat(parsed!!.formattedName).isEqualTo("Smith, John; Jr")
    }

    @Test
    fun `a semicolon inside a component does not split it`() {
        val parsed = VCardParser.parseFirst(vcard("""N:O\;Brien;Alex;;;""", "FN:Alex O;Brien"))
        assertThat(parsed!!.familyName).isEqualTo("O;Brien")
    }

    @Test
    fun `derives a formatted name when the file omits FN`() {
        // Mandatory in 3.0, and still absent from plenty of real exports.
        val parsed = VCardParser.parseFirst("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Rivera;Alex;;;\r\nEND:VCARD")
        assertThat(parsed!!.formattedName).isEqualTo("Alex Rivera")
    }

    @Test
    fun `maps type parameters onto the field types`() {
        val parsed = VCardParser.parseFirst(
            vcard(
                "FN:Alex Rivera",
                "TEL;TYPE=HOME:+61212345678",
                "TEL;TYPE=WORK,VOICE:+61299999999",
                "TEL;TYPE=CELL:+61400000000",
            ),
        )
        assertThat(parsed!!.phones.map { it.type }).containsExactly(
            ContactFieldType.Home,
            ContactFieldType.Work,
            ContactFieldType.Mobile,
        ).inOrder()
    }

    @Test
    fun `joins address components into one readable line`() {
        val parsed = VCardParser.parseFirst(
            vcard("FN:Alex Rivera", "ADR;TYPE=WORK:;;1 Example Street;Sydney;NSW;2000;Australia"),
        )
        assertThat(parsed!!.address)
            .isEqualTo("1 Example Street, Sydney, NSW, 2000, Australia")
    }

    @Test
    fun `an embedded photo is discarded, not imported`() {
        // Photos are display-only in this app and never transmitted. Importing
        // an embedded one would store a large payload only to throw it away.
        val parsed = VCardParser.parseFirst(
            vcard("FN:Alex Rivera", "PHOTO;ENCODING=b;TYPE=JPEG:/9j/4AAQSkZJRgABAQAAAQ"),
        )
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.photoUri).isNull()
        assertThat(parsed.formattedName).isEqualTo("Alex Rivera")
    }

    @Test
    fun `unknown properties are skipped rather than treated as fatal`() {
        // A parser that rejects anything non-conformant rejects most real files.
        val parsed = VCardParser.parseFirst(
            vcard(
                "FN:Alex Rivera",
                "X-CUSTOM-THING:whatever",
                "item1.X-ABLabel:_\$!<Work>!\$_",
                "REV:2026-09-10T12:00:00Z",
            ),
        )
        assertThat(parsed!!.formattedName).isEqualTo("Alex Rivera")
    }

    @Test
    fun `only the first card in a multi-card file is taken`() {
        val two = listOf(
            "BEGIN:VCARD", "VERSION:3.0", "FN:First Person", "END:VCARD",
            "BEGIN:VCARD", "VERSION:3.0", "FN:Second Person", "END:VCARD",
        ).joinToString("\r\n")
        assertThat(VCardParser.parseFirst(two)!!.formattedName).isEqualTo("First Person")
    }

    @Test
    fun `text that is not a vcard returns null`() {
        assertThat(VCardParser.parseFirst("just some text")).isNull()
        assertThat(VCardParser.parseFirst("")).isNull()
    }

    @Test
    fun `handles LF-only line endings`() {
        // The RFC requires CRLF; plenty of exporters emit bare LF anyway.
        val parsed = VCardParser.parseFirst("BEGIN:VCARD\nVERSION:3.0\nFN:Alex Rivera\nEND:VCARD")
        assertThat(parsed!!.formattedName).isEqualTo("Alex Rivera")
    }
}
