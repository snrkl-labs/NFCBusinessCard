package org.snrkl.nfcbusinesscard.vcard

import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * Serialises a [VCardData] to vCard 3.0 text, per RFC 2426.
 *
 * Pure Kotlin, no Android dependency. This output is what goes into the NDEF
 * `text/vcard` record and into the QR payload, so a subtle mistake here is a
 * contact card that imports with a mangled name on someone else's phone — and
 * neither an emulator nor a lint rule will ever catch it. It is tested instead.
 *
 * vCard 3.0 rather than 4.0 deliberately: 3.0 is what iOS Contacts and Android
 * both import without complaint, and 4.0 buys nothing this app needs.
 */
internal object VCardSerializer {

    /** RFC 2426 requires CRLF line endings, not the platform's newline. */
    private const val CRLF = "\r\n"

    /** Lines longer than 75 octets are folded. */
    private const val MAX_LINE_OCTETS = 75

    fun serialize(data: VCardData): String {
        val lines = buildList {
            add("BEGIN:VCARD")
            add("VERSION:3.0")

            // N is structured: Family;Given;Additional;Prefix;Suffix. It is
            // mandatory in 3.0, so it is emitted even when only FN is known —
            // some importers reject a card without it outright.
            add("N:${escape(data.familyName)};${escape(data.givenName)};;;")
            add("FN:${escape(data.formattedName)}")

            if (data.organisation.isNotBlank()) {
                add("ORG:${escape(data.organisation)}")
            }
            if (data.jobTitle.isNotBlank()) {
                add("TITLE:${escape(data.jobTitle)}")
            }

            data.phones.filter { it.value.isNotBlank() }.forEach { phone ->
                add("TEL;TYPE=${phone.type.vCardTypeParameter}:${escape(phone.value)}")
            }
            data.emails.filter { it.value.isNotBlank() }.forEach { email ->
                add("EMAIL;TYPE=${email.type.vCardTypeParameter}:${escape(email.value)}")
            }

            if (data.url.isNotBlank()) {
                add("URL:${escape(data.url)}")
            }
            if (data.address.isNotBlank()) {
                // ADR is structured: PO box; extended; street; locality; region;
                // postcode; country. The model holds one free-text address, which
                // goes in the street component — the only one where an importer
                // will display it intact rather than trying to parse a suburb out
                // of it.
                add("ADR;TYPE=WORK:;;${escape(data.address)};;;;")
            }
            if (data.note.isNotBlank()) {
                add("NOTE:${escape(data.note)}")
            }

            add("END:VCARD")
        }

        return lines.joinToString(CRLF) { fold(it) } + CRLF
    }

    /**
     * Escapes the characters RFC 2426 gives structural meaning to.
     *
     * Order matters: the backslash must be escaped first, or the backslashes
     * introduced by the later replacements get escaped a second time and the
     * value arrives doubled on the far end.
     */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace(",", "\\,")
        .replace(";", "\\;")

    /**
     * Folds a long line by inserting CRLF and a single space.
     *
     * Folding counts octets, not characters — a multi-byte UTF-8 character split
     * across a fold boundary is unparseable, so the split point is walked back to
     * a character boundary. In practice contact fields rarely reach 75 octets,
     * but "rarely" is exactly when an untested path bites.
     */
    private fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_LINE_OCTETS) return line

        val out = StringBuilder()
        var octetsOnLine = 0
        var isContinuation = false

        for (character in line) {
            val characterOctets = character.toString().toByteArray(Charsets.UTF_8).size
            // A continuation line's leading space counts toward its own budget.
            val limit = if (isContinuation) MAX_LINE_OCTETS - 1 else MAX_LINE_OCTETS
            if (octetsOnLine + characterOctets > limit) {
                out.append(CRLF).append(' ')
                octetsOnLine = 0
                isContinuation = true
            }
            out.append(character)
            octetsOnLine += characterOctets
        }
        return out.toString()
    }
}
