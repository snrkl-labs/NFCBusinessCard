package org.snrkl.nfcbusinesscard.hce

/**
 * ISO 7816-4 status words and command-parsing helpers.
 *
 * Pure Kotlin with no Android dependency, so the APDU layer is unit-testable
 * without a device. This matters more than usual here: an emulator has no NFC
 * radio and no HCE path, so if this logic is not proven in plain JVM tests it is
 * not proven at all until it is in someone's hand at a conference.
 */
internal object StatusWord {
    /** Normal completion. */
    val OK = byteArrayOf(0x90.toByte(), 0x00)

    /** File or application not found — the AID or file id was not one of ours. */
    val FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())

    /** P1/P2 carried a value this command does not accept. */
    val WRONG_P1P2 = byteArrayOf(0x6A, 0x86.toByte())

    /** Offset lies outside the currently selected file. */
    val WRONG_OFFSET = byteArrayOf(0x6B, 0x00)

    /** Lc/Le inconsistent with the command body. */
    val WRONG_LENGTH = byteArrayOf(0x67, 0x00)

    /** Command not allowed — nothing is selected, so there is nothing to read. */
    val COMMAND_NOT_ALLOWED = byteArrayOf(0x69.toByte(), 0x86.toByte())

    /** Instruction byte not supported. */
    val INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)

    /** Class byte not supported. */
    val CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
}

/**
 * A parsed command APDU.
 *
 * Only the short-form encoding is handled. Extended-length APDUs exist, but a
 * Type 4 tag advertising a sub-256-byte MLe will not be addressed with them by
 * any conformant reader, and pretending to support a form that is never
 * exercised would be untested code on the most failure-prone path in the app.
 */
internal data class CommandApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val data: ByteArray,
    /** Expected response length. Null when the command carried no Le byte. */
    val le: Int?,
) {
    val p1p2: Int get() = (p1 shl 8) or p2

    companion object {
        /**
         * Parses a command APDU, returning null if it is too short or internally
         * inconsistent.
         *
         * Readers in the wild send all three legal shapes — header only, header
         * plus Le, and header plus Lc plus data plus optional Le — so all three
         * are accepted rather than assuming the shape Android's own sample emits.
         */
        fun parse(raw: ByteArray): CommandApdu? {
            if (raw.size < 4) return null

            val cla = raw[0].toInt() and 0xFF
            val ins = raw[1].toInt() and 0xFF
            val p1 = raw[2].toInt() and 0xFF
            val p2 = raw[3].toInt() and 0xFF

            // Header only: no body, no expected response.
            if (raw.size == 4) {
                return CommandApdu(cla, ins, p1, p2, ByteArray(0), null)
            }

            // Header + single trailing byte: that byte is Le, not Lc.
            if (raw.size == 5) {
                val le = raw[4].toInt() and 0xFF
                // Le of zero means 256 in the short encoding, not "expect nothing".
                return CommandApdu(cla, ins, p1, p2, ByteArray(0), if (le == 0) 256 else le)
            }

            val lc = raw[4].toInt() and 0xFF
            if (lc == 0) return null
            val dataEnd = 5 + lc
            if (raw.size < dataEnd) return null

            val data = raw.copyOfRange(5, dataEnd)
            val le = when (raw.size) {
                dataEnd -> null
                dataEnd + 1 -> (raw[dataEnd].toInt() and 0xFF).let { if (it == 0) 256 else it }
                // Anything longer than data-plus-one-Le-byte is malformed short form.
                else -> return null
            }
            return CommandApdu(cla, ins, p1, p2, data, le)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommandApdu) return false
        return cla == other.cla && ins == other.ins && p1 == other.p1 &&
            p2 == other.p2 && data.contentEquals(other.data) && le == other.le
    }

    override fun hashCode(): Int {
        var result = cla
        result = 31 * result + ins
        result = 31 * result + p1
        result = 31 * result + p2
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (le ?: 0)
        return result
    }
}

/** Appends a two-byte status word to a response body. */
internal fun ByteArray.withStatus(status: ByteArray): ByteArray {
    val out = ByteArray(size + status.size)
    copyInto(out)
    status.copyInto(out, destinationOffset = size)
    return out
}
