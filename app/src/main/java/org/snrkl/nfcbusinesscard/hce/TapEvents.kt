package org.snrkl.nfcbusinesscard.hce

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Announces that a reader successfully read the card.
 *
 * The card-emulation service is started by the platform, not by the app, so it
 * cannot hold a reference to whatever screen happens to be showing. A
 * process-wide flow is the connection between the two.
 *
 * `extraBufferCapacity` with `DROP_OLDEST` matters: emission happens from the
 * service's callback on the main thread and must not suspend. A tap that cannot
 * be delivered — because nothing is listening — is dropped rather than queued.
 * Feedback for a tap that finished several seconds ago would be worse than none.
 */
internal object TapEvents {

    private val _successfulReads = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits once each time a reader reads the whole payload. */
    val successfulReads: SharedFlow<Unit> = _successfulReads.asSharedFlow()

    /** Called from the card-emulation service. Never suspends. */
    fun reportSuccessfulRead() {
        _successfulReads.tryEmit(Unit)
    }
}
