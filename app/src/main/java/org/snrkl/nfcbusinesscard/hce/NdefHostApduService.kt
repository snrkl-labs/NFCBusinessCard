package org.snrkl.nfcbusinesscard.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import org.snrkl.nfcbusinesscard.data.ActivePayloadCache

/**
 * The Android card-emulation entry point.
 *
 * Deliberately thin. Everything that could be wrong about the Type 4 protocol
 * lives in [Type4TagResponder], which is pure Kotlin and covered by unit tests,
 * because no emulator has an NFC radio and this class can only ever be exercised
 * by a human holding two phones together. What is left here is the platform
 * plumbing, which is short enough to verify by reading.
 *
 * The platform instantiates this by name from the manifest, so it must survive
 * minification — see proguard-rules.pro.
 */
class NdefHostApduService : HostApduService() {

    private val responder = Type4TagResponder(
        onNdefFullyRead = TapEvents::reportSuccessfulRead,
        ndefFileSupplier = { ActivePayloadCache.ndefFileContent() },
    )

    /**
     * Answers one command APDU.
     *
     * Runs on the main thread and must return promptly — the reader is holding
     * the RF field open and waiting. The payload is already encoded in
     * [ActivePayloadCache], so this does no I/O.
     */
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return StatusWord.WRONG_LENGTH
        return responder.processCommand(commandApdu)
    }

    /**
     * Called when the RF field drops or the reader selects another application.
     *
     * Resetting matters: without it, the next reader would find a file already
     * selected from the previous tap and could read the NDEF file without ever
     * selecting the application, which is not a sequence this tag should honour.
     */
    override fun onDeactivated(reason: Int) {
        responder.onDeactivated()
    }
}
