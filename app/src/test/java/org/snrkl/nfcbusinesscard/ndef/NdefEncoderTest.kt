package org.snrkl.nfcbusinesscard.ndef

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Byte-level assertions on the records this app puts on the wire. */
class NdefEncoderTest {

    @Test
    fun `uri record has the expected header, type and prefixed payload`() {
        val bytes = NdefEncoder.message(NdefEncoder.uriRecord("https://example.com"))

        // MB | ME | SR | TNF=WellKnown = 0x80|0x40|0x10|0x01
        assertThat(bytes[0]).isEqualTo(0xD1.toByte())
        // TYPE LENGTH — one byte, 'U'
        assertThat(bytes[1]).isEqualTo(0x01.toByte())
        // PAYLOAD LENGTH — prefix byte plus "example.com"
        assertThat(bytes[2]).isEqualTo((1 + "example.com".length).toByte())
        // TYPE
        assertThat(bytes[3]).isEqualTo('U'.code.toByte())
        // Prefix byte for https://
        assertThat(bytes[4]).isEqualTo(0x04.toByte())
        assertThat(String(bytes, 5, bytes.size - 5, Charsets.UTF_8)).isEqualTo("example.com")
    }

    @Test
    fun `mime record carries the media type in the TYPE field`() {
        val bytes = NdefEncoder.message(NdefEncoder.vCardRecord("BEGIN:VCARD"))

        // TNF=MimeMedia = 0x02, with MB|ME|SR set.
        assertThat(bytes[0]).isEqualTo(0xD2.toByte())
        assertThat(bytes[1]).isEqualTo("text/vcard".length.toByte())
        assertThat(bytes[2]).isEqualTo("BEGIN:VCARD".length.toByte())
        assertThat(String(bytes, 3, "text/vcard".length, Charsets.US_ASCII))
            .isEqualTo("text/vcard")
    }

    @Test
    fun `payloads of 255 bytes or fewer use the short record form`() {
        // 255 payload bytes = 254 characters plus the prefix byte.
        val uri = "https://e.com/" + "a".repeat(254 - "e.com/".length)
        val record = NdefEncoder.uriRecord(uri)
        assertThat(record.payload).hasLength(255)

        val bytes = record.toByteArray()
        // SR flag set, single-byte length.
        assertThat(bytes[0].toInt() and 0x10).isEqualTo(0x10)
        assertThat(bytes[2]).isEqualTo(0xFF.toByte())
    }

    @Test
    fun `payloads of 256 bytes or more widen the length field to four bytes`() {
        val uri = "https://e.com/" + "a".repeat(300)
        val record = NdefEncoder.uriRecord(uri)
        val bytes = record.toByteArray()

        // SR flag cleared.
        assertThat(bytes[0].toInt() and 0x10).isEqualTo(0)
        // Four-byte big-endian payload length.
        val declared = ((bytes[2].toInt() and 0xFF) shl 24) or
            ((bytes[3].toInt() and 0xFF) shl 16) or
            ((bytes[4].toInt() and 0xFF) shl 8) or
            (bytes[5].toInt() and 0xFF)
        assertThat(declared).isEqualTo(record.payload.size)
    }

    @Test
    fun `ndef file content is prefixed with a big-endian NLEN`() {
        val record = NdefEncoder.uriRecord("https://example.com")
        val message = NdefEncoder.message(record)
        val file = NdefEncoder.ndefFileContent(record)

        val nlen = ((file[0].toInt() and 0xFF) shl 8) or (file[1].toInt() and 0xFF)
        assertThat(nlen).isEqualTo(message.size)
        assertThat(file.size).isEqualTo(message.size + 2)
        assertThat(file.copyOfRange(2, file.size)).isEqualTo(message)
    }

    @Test
    fun `an empty ndef file is a valid zero-length tag, not a refusal`() {
        // A reader can report "empty tag". It cannot distinguish silence from
        // broken hardware, which is why this state exists.
        assertThat(NdefEncoder.emptyNdefFileContent()).isEqualTo(byteArrayOf(0x00, 0x00))
    }

    @Test
    fun `a payload larger than the advertised file size is rejected, not truncated`() {
        val oversized = "https://e.com/" + "a".repeat(NdefEncoder.MAX_NDEF_MESSAGE_SIZE)
        val record = NdefEncoder.uriRecord(oversized)

        val failure = runCatching { NdefEncoder.ndefFileContent(record) }.exceptionOrNull()

        // Truncating would produce a record a reader parses into nonsense; the
        // caller needs to know so it can serve an empty tag instead.
        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an empty uri is rejected`() {
        assertThat(runCatching { NdefEncoder.uriRecord("") }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `non-ascii uri characters are encoded as UTF-8`() {
        val record = NdefEncoder.uriRecord("https://exämple.com")
        // "ä" is two bytes in UTF-8, so the payload is one byte longer than the
        // character count would suggest.
        assertThat(record.payload.size).isEqualTo(1 + "exämple.com".toByteArray(Charsets.UTF_8).size)
    }
}
