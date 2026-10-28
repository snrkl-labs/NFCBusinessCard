package org.snrkl.nfcbusinesscard.hce

import org.snrkl.nfcbusinesscard.ndef.NdefEncoder

/**
 * The NFC Forum Type 4 Tag read sequence, as a pure-Kotlin state machine.
 *
 * A reader walks a fixed path to get an NDEF message off a Type 4 tag:
 *
 *  1. SELECT by name, naming the NDEF application AID `D2760000850101`
 *  2. SELECT the Capability Container file, id `E103`
 *  3. READ BINARY the CC, learning the NDEF file id and size limits
 *  4. SELECT the NDEF file, id `E104`
 *  5. READ BINARY the NDEF file — first two bytes for NLEN, then the message
 *
 * Every step of that is here, deliberately free of Android imports. No emulator
 * has an NFC radio, so [NdefHostApduService] is kept to a shim around this class
 * and the actual protocol is proven in plain JVM tests.
 */
internal class Type4TagResponder(
    /**
     * Called once per transaction, when the reader has read the whole NDEF file.
     *
     * This is as close to "the tap worked" as a card-emulation service can get.
     * There is no acknowledgement in the protocol — the reader simply stops
     * talking and the field drops — so completion is inferred from the reader
     * having asked for every byte the file claims to hold. That is a strong
     * signal: a reader that got what it came for has no reason to read further,
     * and one that gave up part way will not have reached the end.
     */
    private val onNdefFullyRead: () -> Unit = {},

    /**
     * Supplies the current NDEF file content — NLEN followed by the message.
     *
     * Called once per transaction rather than once per APDU. A reader issues
     * several READ BINARYs against one logical file, and if the operator changed
     * the active item between two of them the reader would splice halves of two
     * different messages together and get a checksum-clean nonsense payload.
     * Snapshotting at SELECT keeps every read within a transaction consistent,
     * while still picking up item changes on the next tap — which is the actual
     * requirement: switching the active item takes effect without a reinstall.
     */
    private val ndefFileSupplier: () -> ByteArray,
) {

    /** Which file the reader currently has selected. */
    private enum class SelectedFile { NONE, CAPABILITY_CONTAINER, NDEF }

    private var selectedFile = SelectedFile.NONE

    /** The NDEF file content for the transaction in progress. */
    private var ndefFileSnapshot: ByteArray = NdefEncoder.emptyNdefFileContent()

    /**
     * Highest byte offset the reader has read up to in the NDEF file this
     * transaction. Readers chunk their reads, so completion is the high-water
     * mark reaching the end of the file, not any single READ BINARY.
     */
    private var ndefBytesRead: Int = 0

    /** Guards against reporting the same transaction twice. */
    private var reportedThisTransaction: Boolean = false

    /**
     * Handles one command APDU and returns the response, status word included.
     *
     * Never throws. A thrown exception inside a `HostApduService` callback means
     * the platform tears the connection down and the reader sees a dead tag,
     * which is indistinguishable from bad hardware. Every failure path here
     * returns a status word instead.
     */
    fun processCommand(commandBytes: ByteArray): ByteArray {
        val command = CommandApdu.parse(commandBytes) ?: return StatusWord.WRONG_LENGTH

        if (command.cla != CLA_ISO7816) return StatusWord.CLA_NOT_SUPPORTED

        return when (command.ins) {
            INS_SELECT -> handleSelect(command)
            INS_READ_BINARY -> handleReadBinary(command)
            else -> StatusWord.INS_NOT_SUPPORTED
        }
    }

    /** Resets to the deselected state. Called when the RF field drops. */
    fun onDeactivated() {
        // The field dropping is the end of the transaction, and the only moment
        // at which "did the reader get everything?" can be answered.
        if (!reportedThisTransaction && ndefFileWasFullyRead()) {
            reportedThisTransaction = true
            runCatching { onNdefFullyRead() }
        }
        selectedFile = SelectedFile.NONE
        ndefFileSnapshot = NdefEncoder.emptyNdefFileContent()
        ndefBytesRead = 0
        reportedThisTransaction = false
    }

    /**
     * Whether the reader read through to the end of a non-empty NDEF file.
     *
     * An empty tag is excluded deliberately: a reader dutifully reading two
     * bytes of NLEN and finding zero has not received a business card, and
     * telling the operator their card was shared would be a lie.
     */
    private fun ndefFileWasFullyRead(): Boolean =
        ndefFileSnapshot.size > 2 && ndefBytesRead >= ndefFileSnapshot.size

    private fun handleSelect(command: CommandApdu): ByteArray = when {
        // SELECT by name (P1=04): selecting the NDEF application by AID.
        command.p1 == P1_SELECT_BY_NAME -> {
            if (command.data.contentEquals(NDEF_APPLICATION_AID)) {
                // Start of a transaction. Take the payload snapshot now, so every
                // read that follows describes one coherent item.
                ndefFileSnapshot = readSnapshot()
                ndefBytesRead = 0
                reportedThisTransaction = false
                selectedFile = SelectedFile.NONE
                StatusWord.OK
            } else {
                StatusWord.FILE_NOT_FOUND
            }
        }

        // SELECT by file id (P1=00, P2=0C): picking CC or NDEF within the app.
        command.p1 == P1_SELECT_BY_FILE_ID && command.p2 == P2_SELECT_FIRST_OR_ONLY -> {
            if (command.data.size != 2) {
                StatusWord.WRONG_LENGTH
            } else {
                val fileId = ((command.data[0].toInt() and 0xFF) shl 8) or
                    (command.data[1].toInt() and 0xFF)
                when (fileId) {
                    CAPABILITY_CONTAINER_FILE_ID -> {
                        selectedFile = SelectedFile.CAPABILITY_CONTAINER
                        StatusWord.OK
                    }
                    NDEF_FILE_ID -> {
                        selectedFile = SelectedFile.NDEF
                        StatusWord.OK
                    }
                    else -> StatusWord.FILE_NOT_FOUND
                }
            }
        }

        else -> StatusWord.WRONG_P1P2
    }

    private fun handleReadBinary(command: CommandApdu): ByteArray {
        val file = when (selectedFile) {
            SelectedFile.CAPABILITY_CONTAINER -> CAPABILITY_CONTAINER
            SelectedFile.NDEF -> ndefFileSnapshot
            // A READ BINARY with nothing selected is a reader bug, but answering
            // with an arbitrary file would be worse than saying no.
            SelectedFile.NONE -> return StatusWord.COMMAND_NOT_ALLOWED
        }

        // In short-EF-id addressing the top bit of P1 flags a different mode this
        // app does not implement; offset is the remaining 15 bits.
        if (command.p1 and 0x80 != 0) return StatusWord.WRONG_P1P2
        val offset = command.p1p2

        // An offset exactly at end-of-file reads zero bytes, which is legal.
        // Past it is not.
        if (offset > file.size) return StatusWord.WRONG_OFFSET

        val requested = command.le ?: 0
        val available = file.size - offset
        // Returning less than Le when less is available is correct: the reader
        // learns the real length from NLEN and asks again if it needs more.
        val length = minOf(requested, available)

        if (selectedFile == SelectedFile.NDEF) {
            ndefBytesRead = maxOf(ndefBytesRead, offset + length)
        }

        return file.copyOfRange(offset, offset + length).withStatus(StatusWord.OK)
    }

    private fun readSnapshot(): ByteArray = try {
        ndefFileSupplier()
    } catch (t: Throwable) {
        // The supplier reaches into the item store. If that fails mid-tap, an
        // empty-but-valid tag is a far better answer than a dropped connection:
        // the reader reports an empty tag rather than a hardware fault.
        NdefEncoder.emptyNdefFileContent()
    }

    internal companion object {
        const val CLA_ISO7816 = 0x00
        const val INS_SELECT = 0xA4
        const val INS_READ_BINARY = 0xB0

        const val P1_SELECT_BY_NAME = 0x04
        const val P1_SELECT_BY_FILE_ID = 0x00
        const val P2_SELECT_FIRST_OR_ONLY = 0x0C

        /**
         * The NFC Forum NDEF application identifier. Readers select this by name
         * to find an NDEF-bearing application, and it is what `apduservice.xml`
         * registers.
         */
        val NDEF_APPLICATION_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )

        const val CAPABILITY_CONTAINER_FILE_ID = 0xE103
        const val NDEF_FILE_ID = 0xE104

        /**
         * Largest response body this tag will return, in bytes.
         *
         * Kept under 256 so a response plus its two-byte status word fits the
         * short APDU form comfortably. Readers honour this and chunk their reads.
         */
        const val MAX_LE = 0x00F6

        /** Largest command body this tag accepts. */
        const val MAX_LC = 0x00FF

        /**
         * The Capability Container.
         *
         * Fixed 15 bytes, read by every reader before it touches the NDEF file.
         * It is what tells the reader the NDEF file's id and how large it may be,
         * and it declares the tag read-only — this app presents a card, it does
         * not accept writes.
         */
        val CAPABILITY_CONTAINER: ByteArray = byteArrayOf(
            0x00, 0x0F,                                   // CCLEN — 15 bytes
            0x20,                                         // Mapping version 2.0
            (MAX_LE shr 8).toByte(), (MAX_LE and 0xFF).toByte(),   // MLe
            (MAX_LC shr 8).toByte(), (MAX_LC and 0xFF).toByte(),   // MLc
            0x04,                                         // NDEF File Control TLV
            0x06,                                         // ...length 6
            (NDEF_FILE_ID shr 8).toByte(),                // NDEF file id, high
            (NDEF_FILE_ID and 0xFF).toByte(),             // NDEF file id, low
            (NdefEncoder.MAX_NDEF_FILE_SIZE shr 8).toByte(),
            (NdefEncoder.MAX_NDEF_FILE_SIZE and 0xFF).toByte(),
            0x00,                                         // Read access: granted
            0xFF.toByte(),                                // Write access: denied
        )
    }
}
