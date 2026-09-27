package com.home.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen picture setup like XGIMI's: a test grid fills the output, so the keystone applied by
 * the projector shows as the grid's outline. OK steps through the corners, shift, size and tilt;
 * the arrows adjust the current one right away; Back leaves.
 */
class KeystoneActivity : ComponentActivity() {
    private val step = mutableIntStateOf(0)
    private val zoom = mutableIntStateOf(0)
    private val message = mutableStateOf<String?>(null)
    private var corners: List<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        corners = Keystone.corners()
        zoom.intValue = Keystone.savedZoom(this)
        if (Sensors.realtimeKeystone() == true) {
            message.value = getString(R.string.keystone_realtime_on)
        }
        setContent { Screen(STEPS[step.intValue], zoom.intValue, message.value) }
    }

    override fun onResume() {
        super.onResume()
        // Android TV handles volume keys in the window manager before any app; Beam's
        // accessibility service sees them first and hands them over while this screen is up.
        // The firmware still changes the volume as well, before anyone can stop it; the level is
        // put back right after, and whenever it changes while this screen is up.
        val audio = getSystemService(AudioManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        keptVolume = volume
        volumeKeys = { up ->
            changeZoom(if (up) -1 else 1, announce = STEPS[step.intValue] != Step.SIZE)
            restoreVolume()
        }
        registerReceiver(volumeChanged, IntentFilter(VOLUME_CHANGED))
    }

    override fun onPause() {
        volumeKeys = null
        runCatching { unregisterReceiver(volumeChanged) }
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var keptVolume = -1

    private val volumeChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM, -1) == AudioManager.STREAM_MUSIC) restoreVolume()
        }
    }

    private fun restoreVolume() {
        val audio = getSystemService(AudioManager::class.java)
        for (delay in longArrayOf(0, 150, 400)) {
            handler.postDelayed({
                if (keptVolume >= 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) != keptVolume) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, keptVolume, 0)
                }
            }, delay)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val current = STEPS[step.intValue]
        val fast = event.repeatCount > 0
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) {
                    step.intValue = (step.intValue + 1) % STEPS.size
                    Sounds.navigate()
                }
            }
            // Volume keys resize the picture on any step (and leave the sound alone).
            KeyEvent.KEYCODE_VOLUME_UP -> changeZoom(-1, announce = current != Step.SIZE)
            KeyEvent.KEYCODE_VOLUME_DOWN -> changeZoom(1, announce = current != Step.SIZE)
            KeyEvent.KEYCODE_DPAD_LEFT -> adjust(current, -1, 0, fast)
            KeyEvent.KEYCODE_DPAD_RIGHT -> adjust(current, 1, 0, fast)
            KeyEvent.KEYCODE_DPAD_UP -> adjust(current, 0, -1, fast)
            KeyEvent.KEYCODE_DPAD_DOWN -> adjust(current, 0, 1, fast)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun adjust(target: Step, dx: Int, dy: Int, fast: Boolean) {
        val pixels = if (fast) 12 else 4
        when (target) {
            // Right or up makes the picture bigger.
            Step.SIZE -> changeZoom(if (dx > 0 || dy < 0) -1 else 1, announce = false)
            Step.TILT -> if (dx != 0) Projection.tilt(clockwise = dx > 0).also { corners = Keystone.corners() ?: corners }
            else -> {
                val values = corners?.toMutableList() ?: return
                if (target == Step.SHIFT) {
                    val moved = values.mapIndexed { i, v -> v + if (i % 2 == 0) dx * pixels else dy * pixels }
                    if (moved.chunked(2).any { (x, y) -> x !in 0 until Keystone.WIDTH || y !in 0 until Keystone.HEIGHT }) {
                        message.value = getString(R.string.keystone_no_room)
                        return
                    }
                    apply(moved)
                } else {
                    val i = target.corner
                    values[i * 2] = (values[i * 2] + dx * pixels).coerceIn(0, Keystone.WIDTH - 1)
                    values[i * 2 + 1] = (values[i * 2 + 1] + dy * pixels).coerceIn(0, Keystone.HEIGHT - 1)
                    apply(values)
                }
            }
        }
    }

    /** [delta] in shrink steps: -1 bigger, +1 smaller. */
    private fun changeZoom(delta: Int, announce: Boolean) {
        val next = (zoom.intValue + delta).coerceIn(0, Keystone.MAX_ZOOM)
        if (next != zoom.intValue && Keystone.setZoom(next)) {
            zoom.intValue = next
            Keystone.saveZoom(this, next)
            corners = Keystone.corners() ?: corners
        }
        if (announce) message.value = getString(R.string.keystone_size_percent, sizePercent(zoom.intValue))
    }

    private fun apply(values: List<Int>) {
        if (Keystone.setCorners(values)) {
            corners = Keystone.corners() ?: values
            message.value = null
        }
    }

    /** [title] and [hint] are string resources. */
    enum class Step(val title: Int, val hint: Int, val corner: Int = -1) {
        TOP_LEFT(R.string.keystone_top_left, R.string.keystone_hint_corner, 0),
        TOP_RIGHT(R.string.keystone_top_right, R.string.keystone_hint_corner, 1),
        BOTTOM_RIGHT(R.string.keystone_bottom_right, R.string.keystone_hint_corner, 3),
        BOTTOM_LEFT(R.string.keystone_bottom_left, R.string.keystone_hint_corner, 2),
        SHIFT(R.string.keystone_shift, R.string.keystone_hint_shift),
        SIZE(R.string.keystone_size, R.string.keystone_hint_size),
        TILT(R.string.keystone_tilt, R.string.keystone_hint_tilt),
    }

    companion object {
        private val STEPS = Step.entries

        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        /** Set while the screen is in front: volume up (true) or down resizes the picture. */
        @Volatile
        var volumeKeys: ((up: Boolean) -> Unit)? = null
    }
}

/** Rough size for a zoom step: 32 steps take the picture down to about half. */
private fun sizePercent(zoom: Int) = 100 - zoom * 50 / Keystone.MAX_ZOOM

private val Blue = Color(0xFF2469D6)
private val Line = Color(0x66FFFFFF)
private val Mark = Color(0xFFFFC72C)

@Composable
private fun Screen(step: KeystoneActivity.Step, zoom: Int, message: String?) {
    Box(Modifier.fillMaxSize().background(Blue)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Grid of 8x8 cells, a border and centre lines: distortions show at a glance.
            for (i in 1 until 8) {
                drawLine(Line, Offset(w * i / 8, 0f), Offset(w * i / 8, h), 2f)
                drawLine(Line, Offset(0f, h * i / 8), Offset(w, h * i / 8), 2f)
            }
            drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), 3f)
            drawLine(Color.White, Offset(0f, h / 2), Offset(w, h / 2), 3f)
            drawRect(Color.White, style = Stroke(10f))
            drawCircle(Color.White, h / 6, Offset(w / 2, h / 2), style = Stroke(3f))
            // Corner handles; the one being moved is large and yellow.
            val points = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h))
            points.forEachIndexed { i, p ->
                val selected = step.corner == i
                drawCircle(if (selected) Mark else Color.White, if (selected) 46f else 22f, p)
            }
            if (step == KeystoneActivity.Step.SHIFT) drawRect(Mark, style = Stroke(14f))
        }
        Column(
            Modifier.align(Alignment.Center).background(Color(0xCC0B1D36), RoundedCornerShape(24.dp))
                .padding(horizontal = 36.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            T(stringResource(step.title), 30.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            T(stringResource(step.hint), 18.sp, color = Color(0xFFD3E3FD))
            if (step == KeystoneActivity.Step.SIZE) {
                Spacer(Modifier.height(6.dp))
                T("${sizePercent(zoom)}%", 22.sp, color = Mark)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            message?.let {
                T(it, 16.sp, color = Mark)
                Spacer(Modifier.height(8.dp))
            }
            T(stringResource(R.string.keystone_keys), 18.sp, color = Color.White)
        }
    }
}
