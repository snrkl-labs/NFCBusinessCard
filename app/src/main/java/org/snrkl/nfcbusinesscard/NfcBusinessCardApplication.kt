package org.snrkl.nfcbusinesscard

import android.app.Application
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.data.ActivePayloadCache
import org.snrkl.nfcbusinesscard.data.ShareableItemRepository
import org.snrkl.nfcbusinesscard.data.contacts.SharedCardImporter
import org.snrkl.nfcbusinesscard.data.db.NfcBusinessCardDatabase
import org.snrkl.nfcbusinesscard.data.prefs.AppPreferencesRepository
import org.snrkl.nfcbusinesscard.widget.CardWidgetProvider

/**
 * Application-scoped wiring.
 *
 * A hand-rolled container rather than a DI framework. The graph is five objects
 * deep and entirely static; Hilt would add an annotation processor, a build-time
 * cost on every iteration of the build loop, and a layer of indirection for a
 * dependency graph that fits on one screen.
 */
internal class AppContainer(context: Context) {

    private val database = NfcBusinessCardDatabase.get(context)

    val itemRepository = ShareableItemRepository(database.shareableItemDao())
    val preferencesRepository = AppPreferencesRepository(context)
    val sharedCardImporter = SharedCardImporter(itemRepository, preferencesRepository)
}

class NfcBusinessCardApplication : Application() {

    internal lateinit var container: AppContainer
        private set

    /**
     * Outlives every screen, because the payload cache has to stay current even
     * when no activity exists — the card-emulation service can be bound by the
     * platform while the app has no UI at all.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        keepActivePayloadCurrent()
        keepWidgetCardFaceCurrent()
    }

    /**
     * Redraws the home screen widget when the Card Face setting changes.
     *
     * The widget draws the chosen card image and shape, not just the active
     * item, so a new image or shape has to reach it too. Without this the widget
     * kept the old card until the active item next changed.
     */
    private fun keepWidgetCardFaceCurrent() {
        applicationScope.launch {
            container.preferencesRepository.preferences
                .map { it.cardFaceUri to it.cardAspectRatio }
                .distinctUntilChanged()
                .drop(1)
                .collect { CardWidgetProvider.refreshAll(this@NfcBusinessCardApplication) }
        }
    }

    /**
     * Keeps [ActivePayloadCache] in step with the Active Item.
     *
     * Watches both the selection and the selected item's own contents, so
     * editing the active item's URL updates what the next tap serves without the
     * operator having to reselect it.
     */
    @Suppress("OPT_IN_USAGE")
    private fun keepActivePayloadCurrent() {
        applicationScope.launch {
            container.preferencesRepository.preferences
                .map { it.activeItemId }
                .distinctUntilChanged()
                .flatMapLatest { activeId ->
                    if (activeId == null) {
                        flowOf(null)
                    } else {
                        container.itemRepository.observeItem(activeId)
                    }
                }
                .collect { item ->
                    ActivePayloadCache.update(item)
                    // The home screen widget draws the same active item, so it
                    // is redrawn from the same signal. Without this it would
                    // keep showing a card the operator had switched away from,
                    // and there is no polling to catch up — updatePeriodMillis
                    // is zero by design.
                    CardWidgetProvider.refreshAll(this@NfcBusinessCardApplication)
                }
        }
    }

    /**
     * Runs a shared-card import on the application scope.
     *
     * Deliberately not the activity's scope: the import must finish even if the
     * activity is recreated mid-flight, and a card lost to a rotation would
     * vanish with no error and no card.
     */
    internal fun importSharedCard(
        context: Context,
        importer: SharedCardImporter,
        uri: Uri,
    ) {
        applicationScope.launch { importer.importFrom(context.applicationContext, uri) }
    }
}

/** Reaches the container from a composable or an activity. */
internal val Context.appContainer: AppContainer
    get() = (applicationContext as NfcBusinessCardApplication).container
