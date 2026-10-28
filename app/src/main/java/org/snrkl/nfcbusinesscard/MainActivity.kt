package org.snrkl.nfcbusinesscard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.hce.SendModeController
import org.snrkl.nfcbusinesscard.ui.common.viewModelFactory
import org.snrkl.nfcbusinesscard.ui.main.MainViewModel
import org.snrkl.nfcbusinesscard.ui.nav.Destinations
import org.snrkl.nfcbusinesscard.ui.nav.NfcBusinessCardNavHost
import org.snrkl.nfcbusinesscard.widget.CardWidgetProvider
import org.snrkl.nfcbusinesscard.ui.theme.NfcBusinessCardTheme

/**
 * The single activity.
 *
 * Send Mode lives here rather than in a view model, and cannot live anywhere
 * else: `CardEmulation.setPreferredService` takes an `Activity` and requires it
 * to be resumed. The view model owns the *intent* to be in Send Mode; this class
 * owns the claim itself and keeps the two honest by clearing the intent whenever
 * the platform takes the claim away.
 */
class MainActivity : ComponentActivity() {

    private val sendModeController by lazy { SendModeController(this) }

    private val mainViewModel: MainViewModel by viewModels {
        viewModelFactory {
            MainViewModel(
                itemRepository = appContainer.itemRepository,
                preferencesRepository = appContainer.preferencesRepository,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NfcBusinessCardTheme {
                NfcBusinessCardNavHost(
                    mainViewModel = mainViewModel,
                    startRoute = widgetStartRoute,
                )
            }
        }

        observeSendModeRequests()
        handleSharedCard(intent)
        handleWidgetAction(intent)
    }

    /**
     * Where a widget tap should land, consumed once by the nav host.
     *
     * Held as state rather than navigated imperatively so the destination is
     * part of the initial composition — navigating after the fact would show the
     * main screen for a frame first, which on a widget tap looks like a stutter.
     */
    private var widgetStartRoute by mutableStateOf<String?>(null)

    private fun handleWidgetAction(intent: Intent?) {
        widgetStartRoute = when (intent?.action) {
            CardWidgetProvider.ACTION_OPEN_CARD -> Destinations.FULL_CARD
            CardWidgetProvider.ACTION_SHOW_QR -> Destinations.QR
            CardWidgetProvider.ACTION_SEND_MODE -> {
                // The NFC button means "start sending", so the request is made
                // here and the activity's resumed-state observer performs the
                // actual claim, exactly as it does for the in-app toggle.
                mainViewModel.setSendMode(true)
                Destinations.FULL_CARD
            }
            else -> null
        }
    }

    /**
     * The activity is `singleTask`, so a share arriving while it is already
     * running is delivered here rather than through onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedCard(intent)
        handleWidgetAction(intent)
    }

    /**
     * Imports a vCard shared from another app, or a `.vcf` opened from a file
     * manager.
     *
     * Runs on the application scope rather than this activity's, because the
     * import must complete even if the activity is recreated — a rotation
     * part-way through would otherwise lose the card silently.
     */
    private fun handleSharedCard(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return

        val importer = appContainer.sharedCardImporter
        (application as NfcBusinessCardApplication).importSharedCard(this, importer, uri)
    }

    override fun onResume() {
        super.onResume()
        // NFC can be switched off while the app is backgrounded, so availability
        // is re-read on every resume rather than cached at construction.
        mainViewModel.onNfcAvailabilityChanged(sendModeController.availability())
    }

    override fun onPause() {
        // The platform drops the preferred-service claim when the activity stops
        // being resumed. Releasing it explicitly and telling the view model keeps
        // the toggle from showing an "on" state that no longer exists.
        sendModeController.disable(this)
        mainViewModel.onLostForeground()
        super.onPause()
    }

    /**
     * Applies Send Mode requests, but only while resumed.
     *
     * `repeatOnLifecycle(RESUMED)` is doing real work here, not ceremony: a
     * claim attempted outside the resumed state is rejected by the platform, and
     * the rejection is indistinguishable from another app holding the routing.
     */
    private fun observeSendModeRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.sendModeRequested.collect { requested ->
                    if (requested) {
                        val claimed = sendModeController.enable(this@MainActivity)
                        mainViewModel.onSendModeClaimResult(claimed)
                    } else {
                        sendModeController.disable(this@MainActivity)
                    }
                }
            }
        }
    }
}
