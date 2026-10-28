package org.snrkl.nfcbusinesscard.ndef

/**
 * The NFC Forum URI Record Type Definition abbreviation table.
 *
 * An NDEF URI record does not store the scheme as text. It stores a single
 * identifier byte naming one of these prefixes, followed by whatever is left of
 * the URI. Getting that byte wrong is the classic reason a tag reads back as
 * garbage — a reader that sees `0x03` will happily reconstruct `http://` in
 * front of a payload that was meant to be served over TLS.
 *
 * Index into this table *is* the identifier byte, so the ordering is load-bearing
 * and must not be sorted, deduplicated, or otherwise tidied.
 */
internal val URI_PREFIXES: List<String> = listOf(
    "",                            // 0x00 — no abbreviation, URI written in full
    "http://www.",                 // 0x01
    "https://www.",                // 0x02
    "http://",                     // 0x03
    "https://",                    // 0x04
    "tel:",                        // 0x05
    "mailto:",                     // 0x06
    "ftp://anonymous:anonymous@",  // 0x07
    "ftp://ftp.",                  // 0x08
    "ftps://",                     // 0x09
    "sftp://",                     // 0x0A
    "smb://",                      // 0x0B
    "nfs://",                      // 0x0C
    "ftp://",                      // 0x0D
    "dav://",                      // 0x0E
    "news:",                       // 0x0F
    "telnet://",                   // 0x10
    "imap:",                       // 0x11
    "rtsp://",                     // 0x12
    "urn:",                        // 0x13
    "pop:",                        // 0x14
    "sip:",                        // 0x15
    "sips:",                       // 0x16
    "tftp:",                       // 0x17
    "btspp://",                    // 0x18
    "btl2cap://",                  // 0x19
    "btgoep://",                   // 0x1A
    "tcpobex://",                  // 0x1B
    "irdaobex://",                 // 0x1C
    "file://",                     // 0x1D
    "urn:epc:id:",                 // 0x1E
    "urn:epc:tag:",                // 0x1F
    "urn:epc:pat:",                // 0x20
    "urn:epc:raw:",                // 0x21
    "urn:epc:",                    // 0x22
    "urn:nfc:",                    // 0x23
)

/**
 * The result of splitting a URI into an abbreviation byte and the remaining text.
 */
internal data class AbbreviatedUri(
    val prefixByte: Byte,
    val remainder: String,
) {
    /** Reconstructs the full URI, as a reader would. Used to prove round-tripping. */
    fun expand(): String = URI_PREFIXES[prefixByte.toInt() and 0xFF] + remainder
}

/**
 * Splits [uri] into the *longest* matching abbreviation and its remainder.
 *
 * Longest-match is the whole game here. `https://www.example.com` matches both
 * `https://` (0x04) and `https://www.` (0x02); picking the shorter one is not
 * wrong on the wire, but it wastes four bytes of a budget that matters, and it
 * disagrees with what every other implementation emits. Scanning the table in
 * index order and taking the first hit would silently pick `0x02` over `0x04`
 * only by luck of the ordering, so length is compared explicitly.
 *
 * A URI matching no prefix falls back to `0x00` and is written out in full,
 * which is valid and is what a `geo:` or custom-scheme URI will do.
 */
internal fun abbreviateUri(uri: String): AbbreviatedUri {
    var bestIndex = 0
    var bestLength = 0
    // Index 0 is the empty prefix and would match everything, so it is skipped
    // and left as the fallback.
    for (index in 1 until URI_PREFIXES.size) {
        val prefix = URI_PREFIXES[index]
        if (uri.startsWith(prefix) && prefix.length > bestLength) {
            bestIndex = index
            bestLength = prefix.length
        }
    }
    return AbbreviatedUri(
        prefixByte = bestIndex.toByte(),
        remainder = uri.substring(bestLength),
    )
}
