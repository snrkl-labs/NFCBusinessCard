package org.snrkl.nfcbusinesscard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.snrkl.nfcbusinesscard.MainActivity
import org.snrkl.nfcbusinesscard.R
import org.snrkl.nfcbusinesscard.appContainer
import org.snrkl.nfcbusinesscard.card.GeneratedCardRenderer
import org.snrkl.nfcbusinesscard.data.model.ShareableItem
import org.snrkl.nfcbusinesscard.data.model.ShareableItemType
import org.snrkl.nfcbusinesscard.link.displayUrl
import org.snrkl.nfcbusinesscard.ui.main.GeneratedCardAspect
import org.snrkl.nfcbusinesscard.ui.main.placeholderRes
import org.snrkl.nfcbusinesscard.ui.qr.QrBitmapRenderer
import org.snrkl.nfcbusinesscard.ui.main.toGenerated
import kotlinx.coroutines.flow.first

/**
 * The home screen widget.
 *
 * Shows the active card in landscape, with two controls beside it when there is
 * room. Unlike Full Card Mode in the app — where the phone stays upright and the
 * card turns — the widget is already landscape, so nothing is rotated.
 *
 * Three separate tap targets, because the point of a widget is to skip steps:
 * the card opens Full Card Mode, the NFC button opens it with send mode already
 * on, and the QR button goes straight to the code.
 *
 * **One widget, two shapes.** It is placed at 5x2 and can be dragged in to 3x2.
 * Below [BUTTONS_MIN_WIDTH_DP] the button column is hidden and the card has the
 * tile to itself: at three columns two 48dp controls beside the card would leave
 * neither readable, and everything they do is one tap further in from the card.
 * That is a visibility change on one layout rather than a second provider — two
 * providers would mean two entries in the picker, and resizing one could never
 * turn it into the other, which is the opposite of what was asked for.
 */
class CardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    /**
     * Redraws one widget after the operator resizes it.
     *
     * Which layout the widget wants depends on how wide it now is, and nothing
     * else tells us it changed — [onUpdate] is not called for a resize.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        // The active item comes from the store, so the widget is read on a
        // background scope and pushed back when it resolves. A widget update has
        // roughly ten seconds before the system gives up, which is ample.
        scope.launch {
            val container = context.appContainer
            val preferences = container.preferencesRepository.preferences.first()
            val items = container.itemRepository.items.first()
            val active = items.firstOrNull { it.id == preferences.activeItemId }
                ?: items.firstOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_card)
            val aspect = preferences.cardAspectRatio.toGenerated()

            cardBitmap(context, active, aspect, preferences.cardFaceUri, items.isEmpty())
                ?.let(::downscaleForWidget)
                ?.let { views.setImageViewBitmap(R.id.widget_card_image, it) }

            // A QR is square and the widget is landscape, so a code on its own
            // leaves the tile looking half empty. The link goes beside it —
            // which also means the widget says what it will share, not just
            // that it has something to share.
            val caption = active
                ?.takeIf { it.type == ShareableItemType.Url && preferences.cardFaceUri == null }
                ?.url
                ?.takeIf { it.isNotBlank() }
                ?.let(::displayUrl)

            if (caption == null) {
                views.setViewVisibility(R.id.widget_card_caption, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_card_caption, View.VISIBLE)
                views.setTextViewText(R.id.widget_card_caption, caption)
            }

            views.setOnClickPendingIntent(R.id.widget_card_caption, intentFor(context, ACTION_OPEN_CARD))

            if (showsButtons(manager, widgetId)) {
                views.setViewVisibility(R.id.widget_buttons, View.VISIBLE)
                views.setImageViewBitmap(R.id.widget_nfc_button, icon(context, R.drawable.ic_nfc))
                views.setImageViewBitmap(R.id.widget_qr_button, icon(context, R.drawable.ic_qr))
                views.setOnClickPendingIntent(R.id.widget_nfc_button, intentFor(context, ACTION_SEND_MODE))
                views.setOnClickPendingIntent(R.id.widget_qr_button, intentFor(context, ACTION_SHOW_QR))
            } else {
                // GONE, not INVISIBLE: the column must give its width back to
                // the card, and the two icon bitmaps are left out of the parcel
                // entirely rather than shipped for something nobody can see.
                views.setViewVisibility(R.id.widget_buttons, View.GONE)
            }

            views.setOnClickPendingIntent(R.id.widget_card_image, intentFor(context, ACTION_OPEN_CARD))

            manager.updateAppWidget(widgetId, views)
        }
    }

    /**
     * Whether this widget is wide enough to carry the buttons.
     *
     * `OPTION_APPWIDGET_MIN_WIDTH` is the narrowest the launcher will render
     * this instance at, which is the honest number to test: a widget that is
     * wide in landscape and narrow in portrait should not gain and lose its
     * buttons as the phone turns.
     *
     * A launcher that reports nothing gets the buttons. The options bundle is
     * empty until the launcher fills it, and a widget that has not been measured
     * yet is far more likely to be at its placed 5x2 size than dragged in.
     */
    private fun showsButtons(manager: AppWidgetManager, widgetId: Int): Boolean {
        val minWidthDp = manager.getAppWidgetOptions(widgetId)
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            ?: 0
        return widgetShowsButtons(minWidthDp)
    }

    private fun cardBitmap(
        context: Context,
        item: ShareableItem?,
        aspect: GeneratedCardAspect,
        cardFaceUri: String?,
        libraryEmpty: Boolean,
    ): Bitmap? {
        // Supplied artwork wins, exactly as in the app.
        if (cardFaceUri != null) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(cardFaceUri))?.use {
                    return BitmapFactory.decodeStream(it)
                }
            }
        }

        // A link's useful face is its code, not a card with a URL written on it.
        if (item?.type == ShareableItemType.Url) {
            val url = item.url?.takeIf { it.isNotBlank() } ?: return null
            return QrBitmapRenderer.render(url, WIDGET_CARD_MAX_WIDTH_PX)?.toAndroidBitmap()
        }

        // Nothing configured at all: the specimen card, so a freshly installed
        // widget looks like something rather than an empty rectangle. It only
        // ever appears when there is genuinely nothing else to draw, and with an
        // empty library nothing is active, so it cannot be mistaken for a card
        // that is about to be shared.
        if (libraryEmpty) {
            return runCatching {
                BitmapFactory.decodeResource(context.resources, aspect.placeholderRes)
            }.getOrNull()
        }

        val vCard = item?.vCard ?: return null
        return runCatching {
            GeneratedCardRenderer.render(context, vCard, aspect, darkTheme = false).toAndroidBitmap()
        }.getOrNull()
    }

    /** RemoteViews needs an Android bitmap, not a Compose [ImageBitmap]. */
    private fun ImageBitmap.toAndroidBitmap(): Bitmap {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Scales the card down to fit the RemoteViews budget.
     *
     * See [WIDGET_CARD_MAX_WIDTH_PX]. Filtered on the way down, unlike the card
     * inside the app where filtering is deliberately off — here the image is
     * being resampled regardless, so smooth beats aliased.
     */
    private fun downscaleForWidget(source: Bitmap): Bitmap {
        if (source.width <= WIDGET_CARD_MAX_WIDTH_PX) return source
        val height = (source.height.toFloat() / source.width * WIDGET_CARD_MAX_WIDTH_PX).toInt()
        return Bitmap.createScaledBitmap(source, WIDGET_CARD_MAX_WIDTH_PX, height, true)
    }

    /**
     * Loads and tints a button icon at widget size.
     *
     * Scaled down before it goes anywhere near the RemoteViews: the xxxhdpi
     * source is 224 square, 200 KB as ARGB, which is a lot of a ~1 MB budget for
     * a 48dp control. Tinted here because a widget cannot resolve theme
     * attributes the way the app can.
     */
    private fun icon(context: Context, resId: Int): Bitmap {
        val decoded = BitmapFactory.decodeResource(context.resources, resId)
        val scaled = if (decoded.width > WIDGET_ICON_PX) {
            Bitmap.createScaledBitmap(decoded, WIDGET_ICON_PX, WIDGET_ICON_PX, true)
        } else {
            decoded
        }
        val tinted = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(tinted).drawBitmap(
            scaled,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(
                    // Resolved from resources rather than a constant: the button
                    // ground follows the wallpaper palette on API 31+ and the
                    // night qualifier below that, and a mark tinted to neither
                    // would be the one element that ignores both.
                    context.getColor(R.color.widget_icon_tint),
                    PorterDuff.Mode.SRC_IN,
                )
            },
        )
        return tinted
    }

    private fun intentFor(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_OPEN_CARD = "org.snrkl.nfcbusinesscard.widget.OPEN_CARD"
        const val ACTION_SEND_MODE = "org.snrkl.nfcbusinesscard.widget.SEND_MODE"
        const val ACTION_SHOW_QR = "org.snrkl.nfcbusinesscard.widget.SHOW_QR"

        /**
         * Widest the card bitmap may be inside a RemoteViews, in pixels.
         *
         * RemoteViews cross a Binder transaction with roughly a 1 MB budget, and
         * everything in the update shares it. The card is authored at
         * 1242 x 759, which as ARGB_8888 is 1242 x 759 x 4 = 3.6 MB on its own —
         * three and a half times over, and the failure mode is a blank widget
         * with "!!! FAILED BINDER TRANSACTION !!!" in the log rather than
         * anything the code can catch.
         *
         * 480 x 293 is about 562 KB, leaving room for the two icons and the
         * parcel's own overhead. The card is upscaled slightly by the launcher
         * as a result, which is the right trade for a glanceable home-screen
         * tile: tapping it opens the crisp 1:1 version, which is where legibility
         * actually matters.
         */
        private const val WIDGET_CARD_MAX_WIDTH_PX = 480

        /**
         * Icon edge, in pixels.
         *
         * The xxxhdpi source is 224 square — 200 KB each as ARGB, so the pair
         * would eat 400 KB of the same budget for a 48dp control.
         */
        private const val WIDGET_ICON_PX = 96

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Redraws every placed widget.
         *
         * Called when the active item or its contents change, so the home screen
         * does not keep showing a card the operator has switched away from.
         */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CardWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, CardWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
