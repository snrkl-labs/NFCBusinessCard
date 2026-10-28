package org.snrkl.nfcbusinesscard.hce

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.snrkl.nfcbusinesscard.ndef.NdefEncoder

/**
 * The Type 4 read sequence.
 *
 * No emulator has an NFC radio and there is no HCE path in an AVD, so this is
 * the only place the protocol gets exercised before it is in someone's hand.
 * The tests walk the exact sequence a reader performs, and then the ways a
 * reader can get it wrong.
 */
class Type4TagResponderTest {

    private val payload = NdefEncoder.ndefFileContent(
        NdefEncoder.uriRecord("https://example.com/card"),
    )

    private fun responder(content: ByteArray = payload) = Type4TagResponder { content }

    private fun selectApplication() = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
        0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        0x00,
    )

    private fun selectFile(fileId: Int) = byteArrayOf(
        0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02,
        (fileId shr 8).toByte(), (fileId and 0xFF).toByte(),
    )

    private fun readBinary(offset: Int, length: Int) = byteArrayOf(
        0x00, 0xB0.toByte(),
        (offset shr 8).toByte(), (offset and 0xFF).toByte(),
        length.toByte(),
    )

    private fun ByteArray.statusWord(): ByteArray = copyOfRange(size - 2, size)
    private fun ByteArray.body(): ByteArray = copyOfRange(0, size - 2)

    @Test
    fun `the full reader sequence returns the ndef message`() {
        val responder = responder()

        assertThat(responder.processCommand(selectApplication())).isEqualTo(StatusWord.OK)
        assertThat(responder.processCommand(selectFile(0xE103))).isEqualTo(StatusWord.OK)

        val cc = responder.processCommand(readBinary(0, 15))
        assertThat(cc.statusWord()).isEqualTo(StatusWord.OK)
        assertThat(cc.body()).hasLength(15)

        assertThat(responder.processCommand(selectFile(0xE104))).isEqualTo(StatusWord.OK)

        val nlenRead = responder.processCommand(readBinary(0, 2))
        assertThat(nlenRead.statusWord()).isEqualTo(StatusWord.OK)
        val nlen = ((nlenRead[0].toInt() and 0xFF) shl 8) or (nlenRead[1].toInt() and 0xFF)
        assertThat(nlen).isEqualTo(payload.size - 2)

        val message = responder.processCommand(readBinary(2, nlen))
        assertThat(message.statusWord()).isEqualTo(StatusWord.OK)
        assertThat(message.body()).isEqualTo(payload.copyOfRange(2, payload.size))
    }

    @Test
    fun `the capability container advertises the ndef file id and read-only access`() {
        val cc = Type4TagResponder.CAPABILITY_CONTAINER

        assertThat(cc).hasLength(15)
        // CCLEN
        assertThat(((cc[0].toInt() and 0xFF) shl 8) or (cc[1].toInt() and 0xFF)).isEqualTo(15)
        // Mapping version 2.0
        assertThat(cc[2]).isEqualTo(0x20.toByte())
        // NDEF File Control TLV, tag 0x04, length 0x06
        assertThat(cc[7]).isEqualTo(0x04.toByte())
        assertThat(cc[8]).isEqualTo(0x06.toByte())
        // NDEF file id E104
        assertThat(cc[9]).isEqualTo(0xE1.toByte())
        assertThat(cc[10]).isEqualTo(0x04.toByte())
        // Read access granted, write access denied — this app presents a card,
        // it does not accept writes.
        assertThat(cc[13]).isEqualTo(0x00.toByte())
        assertThat(cc[14]).isEqualTo(0xFF.toByte())
    }

    @Test
    fun `selecting an unknown aid is refused`() {
        val wrongAid = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        )
        assertThat(responder().processCommand(wrongAid)).isEqualTo(StatusWord.FILE_NOT_FOUND)
    }

    @Test
    fun `selecting an unknown file id is refused`() {
        val responder = responder()
        responder.processCommand(selectApplication())
        assertThat(responder.processCommand(selectFile(0xE105)))
            .isEqualTo(StatusWord.FILE_NOT_FOUND)
    }

    @Test
    fun `reading with nothing selected is refused rather than answered arbitrarily`() {
        val responder = responder()
        responder.processCommand(selectApplication())
        assertThat(responder.processCommand(readBinary(0, 2)))
            .isEqualTo(StatusWord.COMMAND_NOT_ALLOWED)
    }

    @Test
    fun `an offset past the end of the file is refused`() {
        val responder = responder()
        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))
        assertThat(responder.processCommand(readBinary(payload.size + 1, 2)))
            .isEqualTo(StatusWord.WRONG_OFFSET)
    }

    @Test
    fun `an offset exactly at end of file reads zero bytes and succeeds`() {
        // Legal per the spec, and readers do it. Refusing would look like a fault.
        val responder = responder()
        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))
        val response = responder.processCommand(readBinary(payload.size, 4))
        assertThat(response).isEqualTo(StatusWord.OK)
    }

    @Test
    fun `a read asking for more than remains returns only what is there`() {
        val responder = responder()
        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))
        val response = responder.processCommand(readBinary(0, 0xF6))
        assertThat(response.statusWord()).isEqualTo(StatusWord.OK)
        assertThat(response.body()).hasLength(payload.size)
    }

    @Test
    fun `a chunked read reassembles into the whole file`() {
        val responder = responder()
        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))

        val chunkSize = 8
        val assembled = ArrayList<Byte>()
        var offset = 0
        while (offset < payload.size) {
            val response = responder.processCommand(readBinary(offset, chunkSize))
            assertThat(response.statusWord()).isEqualTo(StatusWord.OK)
            val body = response.body()
            if (body.isEmpty()) break
            assembled.addAll(body.toList())
            offset += body.size
        }
        assertThat(assembled.toByteArray()).isEqualTo(payload)
    }

    @Test
    fun `an unsupported instruction returns INS not supported`() {
        val update = byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, 0x01, 0x00)
        assertThat(responder().processCommand(update)).isEqualTo(StatusWord.INS_NOT_SUPPORTED)
    }

    @Test
    fun `an unsupported class byte returns CLA not supported`() {
        val proprietary = byteArrayOf(0x80.toByte(), 0xA4.toByte(), 0x04, 0x00, 0x00)
        assertThat(responder().processCommand(proprietary))
            .isEqualTo(StatusWord.CLA_NOT_SUPPORTED)
    }

    @Test
    fun `a truncated command is rejected without throwing`() {
        // A HostApduService that throws tears down the connection, and the
        // reader sees a dead tag rather than an error.
        assertThat(responder().processCommand(byteArrayOf(0x00, 0xA4.toByte())))
            .isEqualTo(StatusWord.WRONG_LENGTH)
    }

    @Test
    fun `deactivation clears the selection so the next reader must select again`() {
        val responder = responder()
        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))
        responder.onDeactivated()

        // Without the reset, this read would succeed against a file the new
        // reader never selected.
        assertThat(responder.processCommand(readBinary(0, 2)))
            .isEqualTo(StatusWord.COMMAND_NOT_ALLOWED)
    }

    @Test
    fun `the payload is snapshotted per transaction, not per apdu`() {
        // A reader issues several reads against one logical file. If the active
        // item changed between two of them, the reader would splice halves of
        // two different messages together and get clean-looking nonsense.
        var current = payload
        val responder = Type4TagResponder { current }

        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))

        val firstHalf = responder.processCommand(readBinary(0, 4)).body()

        current = NdefEncoder.ndefFileContent(
            NdefEncoder.uriRecord("https://completely-different.example/other"),
        )

        val secondHalf = responder.processCommand(readBinary(4, 4)).body()

        assertThat(firstHalf).isEqualTo(payload.copyOfRange(0, 4))
        assertThat(secondHalf).isEqualTo(payload.copyOfRange(4, 8))
    }

    @Test
    fun `a new transaction picks up a changed payload`() {
        // The flip side: switching the active item must take effect on the next
        // tap, without a reinstall.
        var current = payload
        val responder = Type4TagResponder { current }

        responder.processCommand(selectApplication())
        responder.onDeactivated()

        val replacement = NdefEncoder.ndefFileContent(
            NdefEncoder.uriRecord("https://example.com/updated"),
        )
        current = replacement

        responder.processCommand(selectApplication())
        responder.processCommand(selectFile(0xE104))
        val response = responder.processCommand(readBinary(0, 0xF6))
        assertThat(response.body()).isEqualTo(replacement)
    }

    @Test
    fun `a failing payload supplier yields an empty tag rather than a dropped connection`() {
        val responder = Type4TagResponder { error("store unavailable") }

        assertThat(responder.processCommand(selectApplication())).isEqualTo(StatusWord.OK)
        responder.processCommand(selectFile(0xE104))
        val response = responder.processCommand(readBinary(0, 2))

        assertThat(response.statusWord()).isEqualTo(StatusWord.OK)
        assertThat(response.body()).isEqualTo(byteArrayOf(0x00, 0x00))
    }

    @Test
    fun `select by name without a trailing Le byte is accepted`() {
        // Both shapes appear in the wild; rejecting the shorter one would fail
        // against readers that omit Le.
        val noLe = selectApplication().copyOfRange(0, 12)
        assertThat(responder().processCommand(noLe)).isEqualTo(StatusWord.OK)
    }
}
