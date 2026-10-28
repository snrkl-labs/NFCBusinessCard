package org.snrkl.nfcbusinesscard.ui.qr

import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.ui.common.findActivity
import org.snrkl.nfcbusinesscard.ui.theme.QrBackgroundColor
import org.snrkl.nfcbusinesscard.ui.theme.QrModuleColor
import org.snrkl.nfcbusinesscard.vcard.SharedField
import org.snrkl.nfcbusinesscard.vcard.SharedFieldKind
import org.snrkl.nfcbusinesscard.vcard.TierFieldSelector
import org.snrkl.nfcbusinesscard.vcard.DensityTier

/**
 * The QR View.
 *
 * Full-screen presentation of the Active Item, with swipeable density tiers and
 * a brightness slider. Everything here exists to make a code scan under bad
 * conditions: a struggling camera, poor light, someone holding their phone at
 * arm's length across a table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrScreen(
    state: QrUiState,
    bitmapProvider: (DensityTier, Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    onSelectItem: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOnAndBright(useMaxBrightness = state.maxBrightness)

    val scope = rememberCoroutineScope()
    val tiers = state.availableTiers
    val pagerState = rememberPagerState(
        initialPage = tiers.indexOf(state.initialTier).coerceAtLeast(0),
        pageCount = { tiers.size.coerceAtLeast(1) },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.qr_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.qr_close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val item = state.item
            if (item == null || tiers.isEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.qr_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    // fill = true, so the pager takes the space left over after
                    // the panel below has had its share. With fill = false it
                    // sized itself from its width alone and the square code
                    // overflowed the column — clipping the bottom rows, which
                    // makes the code unscannable rather than merely untidy.
                    .weight(1f),
                pageSpacing = 16.dp,
            ) { page ->
                QrPage(
                    tier = tiers[page],
                    bitmapProvider = bitmapProvider,
                )
            }

            // What the visible code actually carries. A QR is opaque, so without
            // this the operator is handing a stranger a payload they cannot
            // themselves see — and the denser tiers carry progressively more.
            //
            // Swiping this panel cycles between items, which is a second axis on
            // top of the tier pager above. They do not fight: the two pagers own
            // separate areas of the screen, so a swipe belongs to whichever one
            // it started in.
            if (state.items.size > 1) {
                val itemIndex = state.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                val itemPager = rememberPagerState(
                    initialPage = itemIndex,
                    pageCount = { state.items.size },
                )

                // Follow an item change that came from elsewhere, so the panel
                // does not sit on a stale page.
                LaunchedEffect(itemIndex) {
                    if (itemPager.currentPage != itemIndex) itemPager.scrollToPage(itemIndex)
                }
                LaunchedEffect(itemPager.settledPage) {
                    state.items.getOrNull(itemPager.settledPage)
                        ?.takeIf { it.id != item.id }
                        ?.let { onSelectItem(it.id) }
                }

                HorizontalPager(
                    state = itemPager,
                    modifier = Modifier.fillMaxWidth(),
                    pageSpacing = 12.dp,
                ) { page ->
                    val pageItem = state.items[page]
                    SharedFieldsSummary(
                        label = pageItem.label,
                        fields = TierFieldSelector.describeShared(
                            pageItem,
                            if (pageItem.id == item.id) tiers[pagerState.currentPage] else state.initialTier,
                        ),
                    )
                }
            } else {
                SharedFieldsSummary(
                    label = item.label,
                    fields = TierFieldSelector.describeShared(item, tiers[pagerState.currentPage]),
                )
            }

            if (state.showTierControl) {
                TierSelector(
                    tiers = tiers,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index ->
                        // scrollToPage rather than animateScrollToPage: tapping a
                        // chip is a direct request for that tier, and animating
                        // through two intermediate codes on the way looks like a
                        // glitch rather than a transition.
                        scope.launch { pagerState.scrollToPage(index) }
                    },
                )
                Text(
                    text = stringResource(R.string.qr_swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            BrightnessControl()

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Lists the fields the currently visible code carries.
 *
 * Re-derived from the pager's current page, so swiping to a denser tier
 * immediately shows the extra fields that tier adds. Values are shown, not just
 * field names: "Phone" does not tell you *which* number is about to be handed
 * over.
 */
@Composable
private fun SharedFieldsSummary(
    label: String,
    fields: List<SharedField>,
    modifier: Modifier = Modifier,
) {

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                // Capped and scrollable: at the Full tier this is ten rows, and
                // an uncapped panel would push the code off the screen on a
                // shorter device. The code keeps priority — it is the thing
                // being scanned.
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.qr_sharing_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Naming the item matters once the panel can be swiped: otherwise
            // there is nothing on screen saying which card is showing.
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (fields.isEmpty()) {
                Text(
                    text = stringResource(R.string.qr_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fields.forEach { field ->
                // Label above value, not beside it.
                //
                // A two-column row left the value about two-thirds of the width,
                // and an email address has no spaces to break at — so it wrapped
                // mid-string into something unreadable. Giving the value the
                // full row width fits any realistic address on one line, and the
                // label costs a line of small text rather than a third of every
                // row.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(field.kind.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = field.value,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Display names for the shared field kinds. */
private val SharedFieldKind.labelRes: Int
    get() = when (this) {
        SharedFieldKind.Link -> R.string.qr_field_link
        SharedFieldKind.Name -> R.string.qr_field_name
        SharedFieldKind.Phone -> R.string.qr_field_phone
        SharedFieldKind.Email -> R.string.qr_field_email
        SharedFieldKind.Organisation -> R.string.qr_field_organisation
        SharedFieldKind.JobTitle -> R.string.qr_field_job_title
        SharedFieldKind.Website -> R.string.qr_field_website
        SharedFieldKind.Address -> R.string.qr_field_address
        SharedFieldKind.Note -> R.string.qr_field_note
    }

/**
 * One tier's code.
 *
 * The bitmap is requested at the measured pixel size rather than a guessed one,
 * so the renderer can floor the module size to whole pixels against the space
 * actually available.
 */
@Composable
private fun QrPage(
    tier: DensityTier,
    bitmapProvider: (DensityTier, Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // A QR code is square, so it is bounded by whichever of the two
        // dimensions is smaller. Deriving the side from the width alone made
        // the code taller than the space available to it.
        val side = minOf(maxWidth, maxHeight)
        val sizePx = with(LocalDensity.current) { side.roundToPx() }
        val bitmap = bitmapProvider(tier, sizePx)

        // The white field is part of the code: a QR needs a quiet zone of at
        // least four modules, and the surrounding padding here provides it. Sat
        // directly on a tonal surface, the outer modules would have nothing to
        // contrast against.
        Surface(
            color = QrBackgroundColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(side),
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap == null) {
                    Text(
                        text = stringResource(R.string.qr_too_large),
                        style = MaterialTheme.typography.bodyMedium,
                        color = QrModuleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                } else {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.qr_code_description),
                        // None, not the default. Any smoothing turns the hard
                        // black-to-white module edges a decoder thresholds on
                        // into a gradient it has to guess at.
                        filterQuality = FilterQuality.None,
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun TierSelector(
    tiers: List<DensityTier>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        tiers.forEachIndexed { index, tier ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tiers.size),
            ) {
                Text(stringResource(tier.labelRes))
            }
        }
    }
}

/**
 * The brightness slider.
 *
 * Drives `WindowManager.LayoutParams.screenBrightness`, which is window-local
 * and needs no permission — `WRITE_SETTINGS` is for changing the *system*
 * brightness and is deliberately not requested.
 */
@Composable
private fun BrightnessControl(modifier: Modifier = Modifier) {
    val activity = LocalContext.current.findActivity()
    var brightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 1f)
    }

    LaunchedEffect(brightness) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Text markers rather than brightness icons: the icons live in
        // material-icons-extended, which is ~5000 icons and tens of megabytes in
        // an unshrunk debug build. Two characters carry the same meaning.
        Text(
            text = stringResource(R.string.qr_brightness_low),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val brightnessLabel = stringResource(R.string.qr_brightness_label)
        Slider(
            value = brightness.coerceIn(0f, 1f),
            onValueChange = { brightness = it },
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                // The two glyphs either side are decorative, so without this the
                // slider announces itself to TalkBack as an unnamed control.
                .semantics { contentDescription = brightnessLabel },
        )
        Text(
            text = stringResource(R.string.qr_brightness_high),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Holds the screen on and bright for as long as the QR View is showing, then
 * puts both back.
 *
 * Restoring matters: leaving `screenBrightness` pinned would keep the phone at
 * full brightness for every screen afterwards, and leaving `FLAG_KEEP_SCREEN_ON`
 * set would stop it sleeping at all. `BRIGHTNESS_OVERRIDE_NONE` is the value
 * that hands control back to the system rather than a literal brightness.
 */
@Composable
private fun KeepScreenOnAndBright(useMaxBrightness: Boolean) {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity, useMaxBrightness) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (useMaxBrightness) {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = previousBrightness
                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

/** The display name for a tier. */
internal val DensityTier.labelRes: Int
    get() = when (this) {
        DensityTier.LinkOnly -> R.string.qr_tier_link_only
        DensityTier.Minimal -> R.string.qr_tier_minimal
        DensityTier.Standard -> R.string.qr_tier_standard
        DensityTier.Full -> R.string.qr_tier_full
    }
