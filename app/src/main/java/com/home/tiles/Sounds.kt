package com.home.tiles

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** UISFX Minimal UI sounds: hover, select and reaction (CC0 audio). */
object Sounds {
    private var pool: SoundPool? = null
    // UISFX master gain (70%) multiplies each cue's own default volume.
    private const val MASTER_VOLUME = 0.7f
    private class Cue(val volume: Float, val cooldownMs: Long = 0) {
        var sound = 0
        var stream = 0
        var lastStartedAt = -cooldownMs
    }
    private val nav = Cue(0.12f, cooldownMs = 60)
    private val activate = Cue(0.20f)
    private val popup = Cue(0.17f)
    private val cues = listOf(nav, activate, popup)
    private var lastArrowAt = -300L

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build().also {
            nav.sound = it.load(context, R.raw.nav, 1)
            activate.sound = it.load(context, R.raw.activate, 1)
            popup.sound = it.load(context, R.raw.popup, 1)
        }
    }

    fun release() {
        pool?.release()
        pool = null
        cues.forEach { it.sound = 0; it.stream = 0; it.lastStartedAt = -it.cooldownMs }
        lastArrowAt = -300L
    }

    /** Called on arrow presses so only user-driven focus moves click, not focus restored on resume. */
    fun markArrow() {
        lastArrowAt = SystemClock.uptimeMillis()
    }

    fun navigate() {
        if (SystemClock.uptimeMillis() - lastArrowAt < 300) play(nav)
    }

    fun activate() = play(activate)

    fun popup() = play(popup)

    private fun play(cue: Cue) {
        val player = pool ?: return
        if (cue.sound == 0 || !LauncherSettings.sounds) return
        val now = SystemClock.uptimeMillis()
        if (now - cue.lastStartedAt < cue.cooldownMs) return
        // Restart the same cue rather than stacking copies on repeated focus events.
        if (cue.stream != 0) player.stop(cue.stream)
        val volume = MASTER_VOLUME * cue.volume
        cue.stream = player.play(cue.sound, volume, volume, 1, 0, 1f)
        if (cue.stream != 0) cue.lastStartedAt = now
    }
}

/** Put on the root of each window so arrow presses arm the navigation sound. */
fun Modifier.arrowSoundTracker(): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.nativeKeyEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            -> Sounds.markArrow()
        }
    }
    false
}
