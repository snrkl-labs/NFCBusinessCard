package org.snrkl.nfcbusinesscard.ndef

/**
 * Type Name Format — the three low bits of an NDEF record header, telling the
 * reader how to interpret the TYPE field.
 *
 * Only the two values this app emits are modelled. The others exist in the spec
 * but adding them would be speculative: this app serves exactly one record, of
 * exactly one of these two kinds.
 */
internal enum class TypeNameFormat(val value: Int) {
    /** TYPE is an NFC Forum Well Known Type, e.g. `U` for a URI record. */
    WellKnown(0x01),

    /** TYPE is an RFC 2046 media type, e.g. `text/vcard`. */
    MimeMedia(0x02),
}

/**
 * A single NDEF record.
 *
 * This is a deliberately small value type with no Android dependency, so the
 * byte-level encoding can be exercised in plain JVM unit tests. The emulator
 * cannot test the NFC radio, so this is where correctness actually gets
 * established.
 */
internal data class NdefRecord(
    val tnf: TypeNameFormat,
    val type: ByteArray,
    val payload: ByteArray,
) {
    /**
     * Encodes this record.
     *
     * [messageBegin] and [messageEnd] set the MB and ME header flags. This app
     * only ever emits single-record messages — multi-record NDEF is explicitly
     * out of scope, because iOS reads only the first record and Android's tag
     * dispatch acts on one, so a second record rides along unread — which means
     * in practice both flags are always true. They are parameters rather than
     * hardcoded so the encoding stays honest and testable.
     */
    fun toByteArray(messageBegin: Boolean = true, messageEnd: Boolean = true): ByteArray {
        // The short-record form packs payload length into one byte. It is only
        // legal below 256 bytes; above that the header flag clears and the
        // length widens to four big-endian bytes.
        val shortRecord = payload.size < 256

        var header = tnf.value
        if (messageBegin) header = header or 0x80
        if (messageEnd) header = header or 0x40
        if (shortRecord) header = header or 0x10
        // The IL flag and ID field are never set: this app's records carry no ID,
        // and an empty ID field is not the same as an absent one.

        val out = ArrayList<Byte>(payload.size + type.size + 8)
        out.add(header.toByte())
        out.add(type.size.toByte())

        if (shortRecord) {
            out.add(payload.size.toByte())
        } else {
            out.add((payload.size ushr 24 and 0xFF).toByte())
            out.add((payload.size ushr 16 and 0xFF).toByte())
            out.add((payload.size ushr 8 and 0xFF).toByte())
            out.add((payload.size and 0xFF).toByte())
        }

        type.forEach { out.add(it) }
        payload.forEach { out.add(it) }
        return out.toByteArray()
    }

    // ByteArray uses identity equality, which would make every record instance
    // unequal to every other and quietly break test assertions. Data classes
    // holding arrays have to spell this out.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NdefRecord) return false
        return tnf == other.tnf &&
            type.contentEquals(other.type) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = tnf.hashCode()
        result = 31 * result + type.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
