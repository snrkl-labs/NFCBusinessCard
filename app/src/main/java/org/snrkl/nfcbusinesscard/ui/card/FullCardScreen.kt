package org.snrkl.nfcbusinesscard.ui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.data.model.VCardData
import org.snrkl.nfcbusinesscard.data.prefs.CardAspectRatio
import org.snrkl.nfcbusinesscard.ui.main.CardFaceImage
import org.snrkl.nfcbusinesscard.ui.main.toGenerated

/**
 * Full Card Mode.
 *
 * The card turns, the phone does not. The operator keeps holding the device
 * normally — controls upright under their thumb — while the card itself is
 * rotated a quarter turn so the person standing beside them can read it. There
 * is deliberately **no orientation change**: rotating the activity would swing
 * the buttons out from under the operator's hand at exactly the moment they want
 * to press one, and would trigger a relayout in the middle of a conversation.
 *
 * Tapping the card flips it between the two quarter turns, so it can be aimed
 * left or right depending on which side the other person is standing. Back
 * returns to the main screen through the ordinary system gesture — this is a
 * normal navigation destination, so that comes for free.
 */
@Composable
internal fun FullCardScreen(
    cardAspectRatio: CardAspectRatio,
    cardFaceUri: String?,
    vCard: VCardData?,
    activeItem: ShareableItem?,
    sendModeActive: Boolean,
    sendModeEnabled: Boolean,
    onSendModeChange: (Boolean) -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val geometry = cardAspectRatio.toGenerated()
    val showsQrFace = activeItem?.type == ShareableItemType.Url && cardFaceUri == null

    // 1 = a quarter turn clockwise, 3 = anticlockwise. Stored as quarter turns
    // rather than degrees so the two states cannot drift out of square.
    //
    // Opens anticlockwise to match the mockup: that puts the top of the card to
    // the operator's left, which is the orientation for showing it to someone
    // standing on their right — the common case for a right-handed person
    // holding the phone out. Tapping flips it for the other side.
    var quarterTurns by remember { mutableIntStateOf(3) }
    val rotation by animateFloatAsState(
        targetValue = quarterTurns * 90f,
        animationSpec = tween(durationMillis = 260),
        label = "cardRotation",
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // safeDrawing covers the status and navigation bars *and* the
                // display cutout, which is what keeps the card clear of the
                // punch-hole camera.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // The card is laid out unrotated and then turned, so its own
                // width must fit the available *height* and vice versa. Sizing
                // it the other way round would leave it a fraction of the space.
                val cardHeight = min(maxWidth, maxHeight / geometry.ratio)
                val cardWidth = cardHeight * geometry.ratio

                CardFaceImage(
                    geometry = geometry,
                    cardFaceUri = cardFaceUri,
                    vCard = vCard,
                    activeItem = activeItem,
                    modifier = Modifier
                        // requiredSize, not size: the unrotated card is wider
                        // than the parent, and ordinary constraints would clamp
                        // it before the rotation ever gets a chance to fit it.
                        .requiredSize(width = cardWidth, height = cardHeight)
                        // A QR code has no orientation, so a URL item's face is
                        // left upright. Turning it would buy nothing and make
                        // the flip hint below a lie.
                        .rotate(if (showsQrFace) 0f else rotation)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.card_flip_label),
                        ) {
                            // Flip between the two quarter turns so the card can
                            // be aimed at whichever side the other person is on.
                            quarterTurns = if (quarterTurns == 1) 3 else 1
                        },
                )
            }

            Text(
                text = stringResource(
                    if (showsQrFace) R.string.card_qr_face_hint else R.string.card_flip_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlyphButton(
                    selected = sendModeActive,
                    enabled = sendModeEnabled,
                    label = stringResource(R.string.card_button_nfc),
                    contentDescription = stringResource(
                        if (sendModeActive) R.string.card_nfc_on else R.string.card_nfc_off,
                    ),
                    onClick = { onSendModeChange(!sendModeActive) },
                    modifier = Modifier.weight(1f),
                ) { tint -> GlyphIcon(R.drawable.ic_nfc, tint) }

                GlyphButton(
                    selected = false,
                    enabled = true,
                    label = stringResource(R.string.card_button_qr),
                    contentDescription = stringResource(R.string.card_button_qr),
                    onClick = onShowQr,
                    modifier = Modifier.weight(1f),
                ) { tint -> GlyphIcon(R.drawable.ic_qr, tint) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * A button glyph, drawn from the operator's own artwork.
 *
 * Rasterised from the SVG sources in `assets/icons` at every density bucket by
 * `tools/generate-icon-pngs.sh` and committed, so the build needs no SVG
 * toolchain and the app ships no SVG decoder. Tinted rather than recoloured at
 * export time, so one asset serves the selected and unselected states.
 */
@Composable
private fun GlyphIcon(
    resId: Int,
    tint: androidx.compose.ui.graphics.Color,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(56.dp),
    )
}

/**
 * One of the two large controls beneath the card.
 *
 * Deliberately big: they are pressed one-handed, often without looking, while
 * the operator's attention is on the person they are talking to.
 *
 * [selected] drives a filled rather than outlined treatment, which is what makes
 * the NFC control bistable — its state has to be readable at a glance from the
 * button itself, not from a label somewhere else.
 */
@Composable
private fun GlyphButton(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = if (selected || !enabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(container)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) { glyph(content) }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}
