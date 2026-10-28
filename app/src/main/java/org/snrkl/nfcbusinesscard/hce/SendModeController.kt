package org.snrkl.nfcbusinesscard.hce

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation

/** What the device can currently do with NFC. */
internal enum class NfcAvailability {
    /** No NFC hardware. Send mode is impossible; the QR path still works. */
    NoHardware,

    /** Hardware present but NFC is switched off in system settings. */
    Disabled,

    /** Ready. */
    Available,
}

/**
 * Claims and releases foreground card-emulation routing — Send Mode.
 *
 * `setPreferredService` guarantees a reader reaches *this* app's service rather
 * than any other HCE app the user has installed. It requires a **resumed
 * activity**, which is an Android constraint and not a design choice: Send Mode
 * cannot survive the app going to the background, and the UI says so plainly
 * rather than showing a toggle that appears to stick and does not.
 */
internal class SendModeController(private val context: Context) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    private val cardEmulation: CardEmulation? =
        adapter?.let { runCatching { CardEmulation.getInstance(it) }.getOrNull() }

    private val serviceComponent = ComponentName(context, NdefHostApduService::class.java)

    fun availability(): NfcAvailability = when {
        adapter == null -> NfcAvailability.NoHardware
        !adapter.isEnabled -> NfcAvailability.Disabled
        else -> NfcAvailability.Available
    }

    /**
     * Claims foreground routing for [activity].
     *
     * Returns whether the claim succeeded. A false return is worth surfacing:
     * it means a tap may be answered by some other installed card-emulation app,
     * which looks to the operator like the wrong card being shared.
     */
    fun enable(activity: Activity): Boolean {
        val emulation = cardEmulation ?: return false
        return runCatching {
            emulation.setPreferredService(activity, serviceComponent)
        }.getOrDefault(false)
    }

    /**
     * Releases the claim, returning the phone to normal tag polling — Receive
     * Mode.
     *
     * Safe to call when nothing was claimed, which matters because it runs from
     * the activity's pause path where the previous state is not always known.
     */
    fun disable(activity: Activity) {
        val emulation = cardEmulation ?: return
        runCatching { emulation.unsetPreferredService(activity) }
    }

    /**
     * Whether this app's service is the default handler for the NDEF AID.
     *
     * Only meaningful as diagnostics — foreground preference overrides the
     * default while it is held, so a false here is not a fault.
     */
    fun isDefaultForNdefAid(): Boolean {
        val emulation = cardEmulation ?: return false
        return runCatching {
            emulation.isDefaultServiceForAid(serviceComponent, NDEF_AID_HEX)
        }.getOrDefault(false)
    }

    private companion object {
        const val NDEF_AID_HEX = "D2760000850101"
    }
}
