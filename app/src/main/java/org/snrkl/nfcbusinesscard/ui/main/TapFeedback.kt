package org.snrkl.nfcbusinesscard.ui.main

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Confirms to the operator that a reader actually took the card.
 *
 * Worth having because the operator usually cannot see what happened: during a
 * tap the phone is turned away from them and pressed against someone else's
 * device. Without this the only evidence a share succeeded is the other person
 * saying so.
 *
 * All three channels are independent, because which one reaches the operator
 * depends on the situation — a flash is facing the other person, a sound is lost
 * in a busy room, and a vibration is felt but tells bystanders nothing.
 */

/** How many times the screen inverts. Two full cycles reads as deliberate. */
private const val FLASH_COUNT = 3

/** Each half-cycle. Long enough to register, short enough not to feel like a bug. */
private const val FLASH_HALF_CYCLE_MS = 90L

/**
 * Drives the screen-inversion flash.
 *
 * Returns an overlay colour to draw over the whole screen, or [Color] with zero
 * alpha when idle. Inverting via an overlay rather than swapping the theme keeps
 * the effect from rebuilding every composable on screen mid-tap.
 */
@Composable
internal fun rememberTapFlashOverlay(trigger: Int, enabled: Boolean): Color {
    var flashing by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        // Skip the initial composition: trigger starts at zero and no tap has
        // happened yet.
        if (trigger == 0 || !enabled) return@LaunchedEffect
        repeat(FLASH_COUNT) {
            flashing = true
            delay(FLASH_HALF_CYCLE_MS)
            flashing = false
            delay(FLASH_HALF_CYCLE_MS)
        }
    }

    val target = if (flashing) MaterialTheme.colorScheme.inverseSurface else Color.Transparent
    val colour by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = FLASH_HALF_CYCLE_MS.toInt() / 2),
        label = "tapFlash",
    )
    return colour
}

/** Draws the flash overlay. Inert when the colour is transparent. */
@Composable
internal fun TapFlashOverlay(colour: Color, modifier: Modifier = Modifier) {
    if (colour.alpha <= 0f) return
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(colour),
    )
}

/**
 * Plays the sound and vibration channels.
 *
 * Deliberately not a composable side effect of drawing — these fire once per
 * tap, driven by the same trigger as the flash.
 */
@Composable
internal fun TapAudioHapticFeedback(
    trigger: Int,
    soundEnabled: Boolean,
    vibrateEnabled: Boolean,
) {
    val context = LocalContext.current

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        if (soundEnabled) playTapSound(context)
        if (vibrateEnabled) vibrateForTap(context)
    }
}

/**
 * Plays the system notification sound.
 *
 * Android's own NFC chime lives at a private path under `/system/media` and is
 * not a public resource, so the notification tone is the closest thing available
 * without shipping an audio asset. `USAGE_NOTIFICATION_EVENT` means it respects
 * the notification volume and Do Not Disturb rather than blaring in a meeting.
 */
private fun playTapSound(context: Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }
}

/** Two short pulses — distinguishable from an incoming notification. */
private fun vibrateForTap(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return

        val pattern = longArrayOf(0, 40, 80, 40)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
