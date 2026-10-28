package org.snrkl.nfcbusinesscard.ndef

/**
 * Builds the NDEF records and Type 4 file content this app serves.
 *
 * Pure Kotlin by design — no Android imports anywhere in this package. The NFC
 * radio cannot be exercised in an emulator, so the encoding is pushed down here
 * where it can be tested without a device, and the Android layer above stays
 * thin enough to be obviously correct by inspection.
 */
internal object NdefEncoder {

    /** TYPE field for a Well Known URI record: the single character `U`. */
    private val URI_RECORD_TYPE = byteArrayOf('U'.code.toByte())

    /**
     * The largest NDEF file this app will serve, including the two-byte NLEN.
     *
     * Type 4 tags advertise a maximum file size in the Capability Container and
     * readers trust it. 2 KiB is far beyond anything this app produces — the
     * densest vCard tier is around 450 bytes — while staying small enough that a
     * reader allocating the advertised size does not care.
     */
    const val MAX_NDEF_FILE_SIZE: Int = 0x0800

    /** Room left for the message itself once NLEN is accounted for. */
    const val MAX_NDEF_MESSAGE_SIZE: Int = MAX_NDEF_FILE_SIZE - 2

    /**
     * Builds a Well Known URI record, abbreviating the scheme per the NFC Forum
     * URI RTD.
     */
    fun uriRecord(uri: String): NdefRecord {
        require(uri.isNotEmpty()) { "URI must not be empty" }
        val abbreviated = abbreviateUri(uri)
        val remainderBytes = abbreviated.remainder.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(remainderBytes.size + 1)
        payload[0] = abbreviated.prefixByte
        remainderBytes.copyInto(payload, destinationOffset = 1)
        return NdefRecord(
            tnf = TypeNameFormat.WellKnown,
            type = URI_RECORD_TYPE,
            payload = payload,
        )
    }

    /**
     * Builds a MIME media record.
     *
     * Used for `text/vcard`. Note that iOS ignores a raw vCard record entirely,
     * which is why the URL type is the primary path and this one is the
     * secondary: a URL pointing at a hosted `.vcf` reaches iOS readers too.
     */
    fun mimeRecord(mimeType: String, content: ByteArray): NdefRecord {
        require(mimeType.isNotEmpty()) { "MIME type must not be empty" }
        return NdefRecord(
            tnf = TypeNameFormat.MimeMedia,
            type = mimeType.toByteArray(Charsets.US_ASCII),
            payload = content,
        )
    }

    /** Convenience for the `text/vcard` case, which is the only MIME type used. */
    fun vCardRecord(vCardText: String): NdefRecord =
        mimeRecord("text/vcard", vCardText.toByteArray(Charsets.UTF_8))

    /**
     * Wraps a single record as a complete NDEF message.
     *
     * Single-record only, deliberately — see [NdefRecord.toByteArray].
     */
    fun message(record: NdefRecord): ByteArray =
        record.toByteArray(messageBegin = true, messageEnd = true)

    /**
     * Produces the contents of the Type 4 NDEF file: a two-byte big-endian
     * length (NLEN) followed by the message itself.
     *
     * A reader reads NLEN first to learn how much to ask for, so a mismatch here
     * shows up as a truncated or over-long read rather than a clean failure.
     */
    fun ndefFileContent(record: NdefRecord): ByteArray {
        val message = message(record)
        require(message.size <= MAX_NDEF_MESSAGE_SIZE) {
            "NDEF message is ${message.size} bytes, exceeding the " +
                "$MAX_NDEF_MESSAGE_SIZE byte limit advertised in the Capability Container"
        }
        val file = ByteArray(message.size + 2)
        file[0] = (message.size ushr 8 and 0xFF).toByte()
        file[1] = (message.size and 0xFF).toByte()
        message.copyInto(file, destinationOffset = 2)
        return file
    }

    /**
     * The file content served when there is no active item.
     *
     * An NLEN of zero is the spec's way of saying "this tag is formatted but
     * empty". It is a valid, readable state — better than refusing to answer,
     * which a reader cannot distinguish from broken hardware.
     */
    fun emptyNdefFileContent(): ByteArray = byteArrayOf(0x00, 0x00)
}
