package org.snrkl.nfcbusinesscard.ui.nav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.snrkl.nfcbusinesscard.R
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.appContainer
import org.snrkl.nfcbusinesscard.data.contacts.ContactImporter
import org.snrkl.nfcbusinesscard.data.contacts.ImportResult
import org.snrkl.nfcbusinesscard.data.contacts.SharedImportOutcome
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.ui.common.viewModelFactory
import org.snrkl.nfcbusinesscard.ui.items.ItemEditorScreen
import org.snrkl.nfcbusinesscard.ui.items.ItemEditorViewModel
import org.snrkl.nfcbusinesscard.ui.items.ImportFailureReason
import org.snrkl.nfcbusinesscard.ui.items.ItemListEvent
import org.snrkl.nfcbusinesscard.ui.items.ItemListScreen
import org.snrkl.nfcbusinesscard.ui.items.ItemListViewModel
import org.snrkl.nfcbusinesscard.ui.card.FullCardScreen
import org.snrkl.nfcbusinesscard.ui.items.VCardField
import org.snrkl.nfcbusinesscard.ui.main.MainScreen
import org.snrkl.nfcbusinesscard.ui.main.MainViewModel
import org.snrkl.nfcbusinesscard.ui.qr.QrScreen
import org.snrkl.nfcbusinesscard.ui.qr.QrViewModel
import org.snrkl.nfcbusinesscard.ui.settings.ChangelogDialog
import org.snrkl.nfcbusinesscard.ui.settings.LicencesDialog
import org.snrkl.nfcbusinesscard.ui.settings.SettingsScreen
import org.snrkl.nfcbusinesscard.ui.settings.SettingsViewModel

/**
 * The navigation graph.
 *
 * View models are created here with explicit factories rather than injected,
 * matching the hand-wired container the rest of the app uses.
 */
@Composable
internal fun NfcBusinessCardNavHost(
    mainViewModel: MainViewModel,
    /** Where a widget tap wants to land, if the app was opened from one. */
    startRoute: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val container = context.appContainer

    // A widget tap navigates once, on arrival. Keyed on the route so a second
    // tap on the same button while the app is already open still works.
    LaunchedEffect(startRoute) {
        if (startRoute != null && startRoute != Destinations.MAIN) {
            navController.navigate(startRoute) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Destinations.MAIN) {

        composable(Destinations.MAIN) {
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()

            // A card shared in from Contacts or a file manager arrives while
            // this screen is being created, so the importer replays its last
            // outcome rather than emitting into nothing.
            val mainSnackbar = remember { SnackbarHostState() }
            val importedTemplate = stringResource(R.string.shared_import_done)
            val notACard = stringResource(R.string.shared_import_not_a_card)
            val unreadable = stringResource(R.string.shared_import_unreadable)

            LaunchedEffect(container) {
                container.sharedCardImporter.outcomes.collect { outcome ->
                    val message = when (outcome) {
                        is SharedImportOutcome.Imported ->
                            importedTemplate.format(outcome.label)
                        SharedImportOutcome.NotACard -> notACard
                        SharedImportOutcome.Unreadable -> unreadable
                    }
                    container.sharedCardImporter.consume()
                    mainSnackbar.showSnackbar(message)
                }
            }

            MainScreen(
                snackbarHostState = mainSnackbar,
                state = state,
                onSendModeChange = mainViewModel::setSendMode,
                onSetActiveItem = mainViewModel::setActiveItem,
                onShowQr = { navController.navigate(Destinations.QR) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onAddItem = {
                    navController.navigate(
                        Destinations.itemEditor(Destinations.NEW_ITEM_ID, ShareableItemType.Url.name),
                    )
                },
                onOpenFullCard = { navController.navigate(Destinations.FULL_CARD) },
                onOpenNfcSettings = {
                    // ACTION_NFC_SETTINGS is the documented way in; if a ROM has
                    // no such activity, failing quietly beats crashing the app
                    // over a convenience shortcut.
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    }
                },
            )
        }

        composable(Destinations.FULL_CARD) {
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()
            FullCardScreen(
                cardAspectRatio = state.cardAspectRatio,
                cardFaceUri = state.cardFaceUri,
                vCard = state.activeItem?.vCard,
                activeItem = state.activeItem,
                sendModeActive = state.sendModeActive,
                sendModeEnabled = state.canUseSendMode,
                onSendModeChange = mainViewModel::setSendMode,
                onShowQr = { navController.navigate(Destinations.QR) },
            )
        }

        composable(Destinations.QR) {
            val viewModel: QrViewModel = viewModel(
                factory = viewModelFactory {
                    QrViewModel(container.itemRepository, container.preferencesRepository)
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            QrScreen(
                state = state,
                bitmapProvider = { tier, sizePx ->
                    state.item?.let { viewModel.bitmapFor(it, tier, sizePx) }
                },
                onSelectItem = viewModel::setActiveItem,
                onClose = { navController.popBackStack() },
            )
        }

        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { SettingsViewModel(container.preferencesRepository) },
            )
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            var changelogVisible by remember { mutableStateOf(false) }
            var licencesVisible by remember { mutableStateOf(false) }

            // OpenDocument rather than GetContent: it returns a URI that can be
            // granted persistable permission, so the chosen card image still
            // loads after a reboot. GetContent's grant dies with the process.
            val pickCardFace = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    viewModel.setCardFaceUri(uri.toString())
                }
            }

            SettingsScreen(
                preferences = preferences,
                onBack = { navController.popBackStack() },
                onManageItems = { navController.navigate(Destinations.ITEMS) },
                onPickCardFace = { pickCardFace.launch(arrayOf("image/png")) },
                onClearCardFace = { viewModel.setCardFaceUri(null) },
                onDefaultTierChange = viewModel::setDefaultTier,
                onMaxBrightnessChange = viewModel::setMaxBrightness,
                onAspectRatioChange = viewModel::setCardAspectRatio,
                onShowChangelog = { changelogVisible = true },
                onShowLicences = { licencesVisible = true },
                onFlashOnTapChange = viewModel::setFlashOnTap,
                onSoundOnTapChange = viewModel::setSoundOnTap,
                onVibrateOnTapChange = viewModel::setVibrateOnTap,
            )

            if (changelogVisible) {
                ChangelogDialog(onDismiss = { changelogVisible = false })
            }

            if (licencesVisible) {
                LicencesDialog(onDismiss = { licencesVisible = false })
            }
        }

        composable(Destinations.ITEMS) {
            val viewModel: ItemListViewModel = viewModel(
                factory = viewModelFactory {
                    ItemListViewModel(container.itemRepository, container.preferencesRepository)
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHost = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            val importFailedFile = stringResource(R.string.items_import_failed)
            val importFailedContact = stringResource(R.string.items_import_contact_failed)
            val importEmpty = stringResource(R.string.items_import_empty)
            val importDenied = stringResource(R.string.items_import_denied)

            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    if (event is ItemListEvent.ImportFailed) {
                        snackbarHost.showSnackbar(
                            when (event.reason) {
                                ImportFailureReason.FileUnreadable -> importFailedFile
                                ImportFailureReason.ContactUnreadable -> importFailedContact
                                ImportFailureReason.PermissionDenied -> importDenied
                                ImportFailureReason.Empty -> importEmpty
                            },
                        )
                    }
                }
            }

            // A .vcf may be offered under several MIME types depending on the
            // app that wrote it, and some file managers report octet-stream for
            // anything they do not recognise. Accepting all three avoids the
            // file being greyed out in the picker.
            val importFile = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    when (val result = ContactImporter.fromVCardFile(context, uri)) {
                        is ImportResult.Success ->
                            viewModel.saveImported(result.data, fallbackLabel = "Imported card")
                        ImportResult.Empty ->
                            viewModel.reportImportFailure(ImportFailureReason.Empty)
                        ImportResult.PermissionDenied ->
                            viewModel.reportImportFailure(ImportFailureReason.PermissionDenied)
                        is ImportResult.Failed ->
                            viewModel.reportImportFailure(ImportFailureReason.FileUnreadable)
                    }
                }
            }

            // The picker's grant covers only the URI it returns, not the
            // /entities sub-path holding the phone numbers and emails, so
            // READ_CONTACTS is genuinely required to assemble a card. It is
            // requested here and nowhere else — never at launch.
            val importContact = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickContact(),
            ) { uri ->
                if (uri != null) {
                    when (val result = ContactImporter.fromPickedContact(context, uri)) {
                        is ImportResult.Success ->
                            viewModel.saveImported(result.data, fallbackLabel = "Imported contact")
                        ImportResult.Empty ->
                            viewModel.reportImportFailure(ImportFailureReason.Empty)
                        ImportResult.PermissionDenied ->
                            viewModel.reportImportFailure(ImportFailureReason.PermissionDenied)
                        is ImportResult.Failed ->
                            viewModel.reportImportFailure(ImportFailureReason.ContactUnreadable)
                    }
                }
            }

            val permissionRefused = stringResource(R.string.items_import_permission_refused)
            val permissionNeeded = stringResource(R.string.items_import_permission_needed)
            val requestContactsPermission = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    importContact.launch(null)
                } else {
                    scope.launch { snackbarHost.showSnackbar(permissionRefused) }
                }
            }

            ItemListScreen(
                state = state,
                snackbarHostState = snackbarHost,
                onBack = { navController.popBackStack() },
                onImportVCardFile = {
                    importFile.launch(
                        arrayOf("text/vcard", "text/x-vcard", "application/octet-stream"),
                    )
                },
                onImportFromContacts = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        importContact.launch(null)
                    } else {
                        // Say why before the system asks. A bare prompt for
                        // Contacts access, from an app about NFC, is a
                        // reasonable thing for someone to refuse on reflex.
                        scope.launch { snackbarHost.showSnackbar(permissionNeeded) }
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                onAdd = { type ->
                    navController.navigate(
                        Destinations.itemEditor(Destinations.NEW_ITEM_ID, type.name),
                    )
                },
                onEdit = { item ->
                    navController.navigate(Destinations.itemEditor(item.id, item.type.name))
                },
                onDelete = viewModel::delete,
                onSetActive = viewModel::setActive,
                onMove = viewModel::move,
            )
        }

        composable(
            route = Destinations.ITEM_EDITOR_ROUTE,
            arguments = listOf(
                navArgument(Destinations.ARG_ITEM_ID) { type = NavType.LongType },
                navArgument(Destinations.ARG_ITEM_TYPE) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong(Destinations.ARG_ITEM_ID)
                ?: Destinations.NEW_ITEM_ID
            val itemType = backStackEntry.arguments
                ?.getString(Destinations.ARG_ITEM_TYPE)
                ?.let { name -> ShareableItemType.entries.firstOrNull { it.name == name } }
                ?: ShareableItemType.Url

            val viewModel: ItemEditorViewModel = viewModel(
                factory = viewModelFactory {
                    ItemEditorViewModel(
                        itemRepository = container.itemRepository,
                        preferencesRepository = container.preferencesRepository,
                        itemId = itemId,
                        itemType = itemType,
                    )
                },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            val pickPhoto = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    viewModel.updatePhoto(uri.toString())
                }
            }

            ItemEditorScreen(
                state = state,
                onLabelChange = viewModel::updateLabel,
                onUrlChange = viewModel::updateUrl,
                onVCardFieldChange = { field, value ->
                    viewModel.updateVCard { data ->
                        when (field) {
                            VCardField.FormattedName -> data.copy(formattedName = value)
                            VCardField.GivenName -> data.copy(givenName = value)
                            VCardField.FamilyName -> data.copy(familyName = value)
                            VCardField.Organisation -> data.copy(organisation = value)
                            VCardField.JobTitle -> data.copy(jobTitle = value)
                            VCardField.Url -> data.copy(url = value)
                            VCardField.Address -> data.copy(address = value)
                            VCardField.Note -> data.copy(note = value)
                        }
                    }
                },
                onPhoneChange = { index, value -> viewModel.updatePhone(index, value = value) },
                onPhoneTypeChange = { index, type -> viewModel.updatePhone(index, type = type) },
                onAddPhone = viewModel::addPhone,
                onRemovePhone = viewModel::removePhone,
                onEmailChange = { index, value -> viewModel.updateEmail(index, value = value) },
                onEmailTypeChange = { index, type -> viewModel.updateEmail(index, type = type) },
                onAddEmail = viewModel::addEmail,
                onRemoveEmail = viewModel::removeEmail,
                onPickPhoto = { pickPhoto.launch(arrayOf("image/*")) },
                onRemovePhoto = { viewModel.updatePhoto(null) },
                onSave = viewModel::save,
                onCancel = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
