package org.snrkl.nfcbusinesscard.vcard

import org.snrkl.nfcbusinesscard.data.model.ContactFieldType
import org.snrkl.nfcbusinesscard.data.model.EmailAddress
import org.snrkl.nfcbusinesscard.data.model.PhoneNumber
import org.snrkl.nfcbusinesscard.data.model.VCardData

/**
 * Parses vCard text into the structured model.
 *
 * The mirror of [VCardSerializer], and pure Kotlin for the same reason: a
 * parser is exactly the kind of code that is subtly wrong on the inputs nobody
 * tried, and it can be exercised here without a device.
 *
 * Deliberately lenient. `.vcf` files in the wild come from Outlook, Google
 * Contacts, iOS and a long tail of tools that each interpret the spec slightly
 * differently. A parser that rejects anything non-conformant would reject most
 * real files, so unknown properties and malformed lines are skipped rather than
 * treated as fatal — a card that imports with one field missing beats one that
 * refuses to import at all.
 *
 * Handles vCard 2.1, 3.0 and 4.0 well enough for the fields this app models.
 * Photos are deliberately discarded: they are display-only in this app and are
 * never transmitted, so importing an embedded one would be storing megabytes to
 * throw away.
 */
internal object VCardParser {

    /**
     * Parses the first card in [text].
     *
     * A `.vcf` may hold many cards; this takes the first and ignores the rest,
     * because the import flow produces one item.
     */
    fun parseFirst(text: String): VCardData? {
        val lines = unfold(text)
        if (lines.none { it.trim().equals("BEGIN:VCARD", ignoreCase = true) }) return null

        var data = VCardData()
        val phones = mutableListOf<PhoneNumber>()
        val emails = mutableListOf<EmailAddress>()
        var started = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
                if (started) break // second card — stop
                started = true
                continue
            }
            if (!started) continue
            if (trimmed.equals("END:VCARD", ignoreCase = true)) break
            if (trimmed.isEmpty()) continue

            val colon = trimmed.indexOf(':')
            if (colon <= 0) continue

            val rawName = trimmed.substring(0, colon)
            val rawValue = trimmed.substring(colon + 1)

            // A property may carry parameters: TEL;TYPE=CELL:+61...
            val parts = rawName.split(';')
            val property = parts.first().substringAfter('.').uppercase()
            val parameters = parts.drop(1)

            // Quoted-printable turns up in vCard 2.1 exports from older tools.
            val value = if (parameters.any { it.contains("QUOTED-PRINTABLE", ignoreCase = true) }) {
                decodeQuotedPrintable(rawValue)
            } else {
                unescape(rawValue)
            }

            when (property) {
                "FN" -> data = data.copy(formattedName = value)

                "N" -> {
                    // Family;Given;Additional;Prefix;Suffix
                    val components = splitComponents(rawValue)
                    data = data.copy(
                        familyName = components.getOrNull(0).orEmpty(),
                        givenName = components.getOrNull(1).orEmpty(),
                    )
                }

                "ORG" -> data = data.copy(organisation = splitComponents(rawValue).firstOrNull().orEmpty())
                "TITLE" -> data = data.copy(jobTitle = value)
                "URL" -> if (data.url.isBlank()) data = data.copy(url = value)
                "NOTE" -> data = data.copy(note = value)

                "ADR" -> if (data.address.isBlank()) {
                    // PO box;extended;street;locality;region;postcode;country.
                    // Rejoined with commas because the model holds one free-text
                    // address, and dropping the components would lose the suburb.
                    data = data.copy(
                        address = splitComponents(rawValue)
                            .map { unescape(it).trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(", "),
                    )
                }

                "TEL" -> if (value.isNotBlank()) {
                    phones += PhoneNumber(type = fieldTypeFrom(parameters, default = ContactFieldType.Mobile), value = value)
                }

                "EMAIL" -> if (value.isNotBlank()) {
                    emails += EmailAddress(type = fieldTypeFrom(parameters, default = ContactFieldType.Work), value = value)
                }

                // PHOTO and everything else: ignored on purpose.
            }
        }

        if (!started) return null

        // Some exports omit FN even though it is mandatory in 3.0.
        val resolved = if (data.formattedName.isBlank()) {
            data.copy(
                formattedName = listOf(data.givenName, data.familyName)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
            )
        } else {
            data
        }

        return resolved.copy(phones = phones, emails = emails)
    }

    /**
     * Extracts an embedded PHOTO, if the card carries one.
     *
     * Separate from [parseFirst] so the domain model stays free of image bytes:
     * a photo is display-only and is stored as a file reference, not as part of
     * the card's data. Returns the decoded bytes, or null if there is no photo
     * or it is a URI reference rather than embedded content.
     */
    fun extractPhoto(text: String): ByteArray? {
        val lines = unfold(text)
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon)
            if (!name.substringBefore(';').substringAfter('.').equals("PHOTO", ignoreCase = true)) {
                continue
            }
            val parameters = name.split(';').drop(1).joinToString(";").uppercase()
            // A VALUE=URI photo points somewhere else; there is nothing to decode
            // and following it would be a network fetch this app does not do.
            if (parameters.contains("VALUE=URI") || parameters.contains("VALUE=URL")) return null

            val payload = line.substring(colon + 1).filterNot { it.isWhitespace() }
            if (payload.isEmpty()) return null
            // java.util.Base64, not android.util.Base64: this package is
            // deliberately free of Android dependencies so it can be tested on
            // the JVM without a device, and the Android one would drag the whole
            // parser onto a device to test. Available since API 26; minSdk is 29.
            // The MIME decoder tolerates the line breaks vCard folding leaves behind.
            return runCatching {
                java.util.Base64.getMimeDecoder().decode(payload)
            }.getOrNull()
        }
        return null
    }

    /**
     * Reverses RFC 2426 line folding.
     *
     * A continuation line begins with a space or tab and belongs to the line
     * before it. Folding happens at 75 octets, so any address or note of normal
     * length arrives folded — missing this produces values truncated at
     * apparently arbitrary points.
     */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        text.split("\r\n", "\n", "\r").forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.substring(1)
            } else {
                out += line
            }
        }
        return out
    }

    /** Splits on unescaped semicolons, so `\;` inside a value survives. */
    private fun splitComponents(value: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (character in value) {
            when {
                escaped -> { current.append(character); escaped = false }
                character == '\\' -> { current.append(character); escaped = true }
                character == ';' -> { out += current.toString(); current.clear() }
                else -> current.append(character)
            }
        }
        out += current.toString()
        return out.map { unescape(it) }
    }

    /** Reverses the escaping [VCardSerializer] applies. */
    private fun unescape(value: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(next)
                }
                index += 2
            } else {
                out.append(character)
                index++
            }
        }
        return out.toString()
    }

    private fun fieldTypeFrom(parameters: List<String>, default: ContactFieldType): ContactFieldType {
        val joined = parameters.joinToString(";").uppercase()
        return when {
            joined.contains("CELL") || joined.contains("MOBILE") -> ContactFieldType.Mobile
            joined.contains("WORK") -> ContactFieldType.Work
            joined.contains("HOME") -> ContactFieldType.Home
            joined.contains("TYPE=") -> ContactFieldType.Other
            else -> default
        }
    }

    /** Minimal quoted-printable decoding, as emitted by vCard 2.1 exporters. */
    private fun decodeQuotedPrintable(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '=' && index + 2 < value.length -> {
                    val hex = value.substring(index + 1, index + 3)
                    val parsed = hex.toIntOrNull(16)
                    if (parsed == null) {
                        bytes += character.code.toByte()
                        index++
                    } else {
                        bytes += parsed.toByte()
                        index += 3
                    }
                }
                character == '=' -> index++ // soft line break at end
                else -> {
                    bytes += character.code.toByte()
                    index++
                }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
