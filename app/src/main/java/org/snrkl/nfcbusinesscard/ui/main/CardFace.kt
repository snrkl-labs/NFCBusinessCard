package org.snrkl.nfcbusinesscard.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.card.GeneratedCardRenderer
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.link.displayUrl
import org.snrkl.nfcbusinesscard.ui.qr.QrBitmapRenderer
import org.snrkl.nfcbusinesscard.ui.theme.QrBackgroundColor
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio

/**
 * The Card Face — the business-card-shaped region on the main screen.
 *
 * Someone has to be able to read a phone number off this, so the priority
 * throughout is that the bitmap reaches the screen unmolested:
 *
 *  - width is capped at [GeneratedCardAspect.CARD_WIDTH_PX] **pixels**, converted
 *    to dp at the current density, so one source pixel lands on one screen pixel
 *    at any density rather than only at the target device's 3.0;
 *  - [FilterQuality.None] disables bilinear filtering, which is what softens
 *    small type when a bitmap is scaled even slightly;
 *  - [ContentScale.Fit] never crops. A cropped card is a card with someone's
 *    email address missing off the edge.
 *
 * On a narrower device the card scales down, which is a real trade but an
 * unavoidable one — and downscaling costs far less legibility than upscaling.
 *
 * **Why the cap is in pixels, not dp.** Capping at 414dp gives exactly 1242px
 * only at density 3.0. On a 560dpi device 414dp is 1449px, and the region would
 * ask the 1242px bitmap to stretch — upscaling precisely the small type this
 * view exists to keep legible. Capping in pixels makes the 1:1 mapping a
 * property of the code rather than a coincidence of the target hardware.
 */
@Composable
internal fun CardFace(
    aspectRatio: CardAspectRatio,
    cardFaceUri: String?,
    modifier: Modifier = Modifier,
    /**
     * The active item, if there is one.
     *
     * A URL item has no details to draw a card from, so it shows its QR code
     * instead — a link's useful representation is the scannable thing, not a
     * card with a URL written on it.
     */
    activeItem: ShareableItem? = null,
    /**
     * The active item's contact details, used to draw a card when no artwork has
     * been supplied. Null for a Link item, which has no details to draw.
     */
    vCard: VCardData? = null,
) {
    val geometry = aspectRatio.toGenerated()

    // The bitmap's own width, expressed in dp for this density. At 3.0 this is
    // the design's 414dp; at 3.5 it is 354.9dp — smaller in dp, identical in
    // pixels, which is the dimension that actually governs resampling.
    val naturalWidth = with(LocalDensity.current) { geometry.widthPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = naturalWidth)
            .aspectRatio(geometry.ratio)
            .clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        CardFaceImage(
            geometry = geometry,
            cardFaceUri = cardFaceUri,
            vCard = vCard,
            activeItem = activeItem,
        )
    }
}

/**
 * Draws the card bitmap and nothing else.
 *
 * Sizing is the caller's business: the main screen constrains it to the card
 * region, while Full Card Mode sizes it to fill a rotated area. Keeping the
 * drawing in one place means the filtering and scaling rules that protect small
 * type cannot drift between the two.
 */
@Composable
internal fun CardFaceImage(
    geometry: GeneratedCardAspect,
    cardFaceUri: String?,
    modifier: Modifier = Modifier,
    vCard: VCardData? = null,
    activeItem: ShareableItem? = null,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    // With no supplied artwork, draw a card from the operator's own details
    // rather than showing a stand-in that carries nobody's information.
    // Remembered on the inputs that actually affect the output, so it is not
    // redrawn on every recomposition.
    val generated = remember(vCard, geometry, darkTheme) {
        vCard?.takeIf { it.formattedName.isNotBlank() }?.let {
            runCatching { GeneratedCardRenderer.render(context, it, geometry, darkTheme) }.getOrNull()
        }
    }

    // A link's useful face is its QR code. Showing a specimen card for a URL
    // item was the "pretend card" complaint: it looked like somebody's card
    // while carrying nobody's information.
    val urlPayload = activeItem
        ?.takeIf { it.type == ShareableItemType.Url && cardFaceUri == null }
        ?.url
        ?.takeIf { it.isNotBlank() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (urlPayload != null) {
            InlineQrCode(payload = urlPayload)
        } else if (cardFaceUri == null && generated != null) {
            Image(
                bitmap = generated,
                contentDescription = stringResource(R.string.main_card_face_description),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (cardFaceUri == null) {
            // Loaded as an ImageBitmap rather than a Painter: filterQuality is
            // only available on the bitmap overload, and disabling filtering is
            // the entire reason this drawable is sized the way it is.
            Image(
                bitmap = ImageBitmap.imageResource(geometry.placeholderRes),
                contentDescription = stringResource(R.string.main_card_face_description),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                // fillMaxSize, not fillMaxWidth. With only the width constrained,
                // the Image node keeps the bitmap's intrinsic pixel height while
                // its width stretches to the parent, so the node ends up a
                // different shape from the card region around it.
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cardFaceUri)
                    // Coil downsamples to the view bounds by default, which would
                    // resample artwork exported at exactly the right size. The
                    // whole point of publishing those dimensions is that this
                    // does not happen.
                    .allowHardware(true)
                    .build(),
                contentDescription = stringResource(R.string.main_card_face_description),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A link's face: its code, with the link itself beside it.
 *
 * A QR is square and the card region is landscape, so a centred code alone
 * leaves two thirds of the card empty. Putting the link next to it fills that
 * space with the one thing worth knowing — *which* link this is — so the card
 * says what it will share rather than only that it has something to share.
 *
 * The code is never stretched to the region's proportions. A distorted QR does
 * not scan, and scanning is the only thing it is for.
 */
@Composable
private fun InlineQrCode(payload: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight)
        val sizePx = with(LocalDensity.current) { side.roundToPx() }
        val bitmap: ImageBitmap? = remember(payload, sizePx) {
            if (sizePx > 0) QrBitmapRenderer.render(payload, sizePx) else null
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Surface(
                    color = QrBackgroundColor,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(side),
                ) {
                    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.qr_code_description),
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            Text(
                text = displayUrl(payload),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

/** Maps the stored preference onto the generated geometry. */
internal fun CardAspectRatio.toGenerated(): GeneratedCardAspect = when (this) {
    CardAspectRatio.Australian -> GeneratedCardAspect.Australian
    CardAspectRatio.Iso -> GeneratedCardAspect.Iso
    CardAspectRatio.Us -> GeneratedCardAspect.Us
}

/**
 * The default card drawable for a given shape.
 *
 * The Australian default ships the operator's sample card, which is authored at
 * exactly 1242 x 759 and so displays 1:1 with no resampling. The other two
 * shapes fall back to the generated diagnostic placeholder — border, corner-to-
 * corner diagonals and its own pixel dimensions printed on it — because no
 * sample artwork exists at those ratios and a card that silently letterboxes
 * would be worse than one that announces its geometry.
 */
internal val GeneratedCardAspect.placeholderRes: Int
    get() = when (this) {
        GeneratedCardAspect.Australian -> R.drawable.card_face_sample_au
        GeneratedCardAspect.Iso -> R.drawable.card_face_placeholder_iso
        GeneratedCardAspect.Us -> R.drawable.card_face_placeholder_us
    }
