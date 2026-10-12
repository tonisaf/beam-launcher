package com.home.tiles

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

/** Receives the voice key and shows non-focusable speech captions. */
class PanelOverlay : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    // One WindowManager for adding and removing the overlay windows.
    private val windows by lazy { getSystemService(WindowManager::class.java) }

    // Holding the voice key speaks a command; a short press has no settings action.
    private var voice: VoiceSession? = null
    private var listening = false
    private var bubble: TextView? = null
    private val startListening = Runnable {
        if (!LauncherSettings.voiceAssistantEnabled || voice == null) return@Runnable
        listening = true
        voice?.loadModel()
        Sounds.popup()
        showBubble(tr(R.string.listening))
    }
    private val hideBubbleTask = Runnable { hideBubble() }

    override fun onServiceConnected() {
        instance = this
        LauncherSettings.init(this)
        Sounds.init(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val firstDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        // The four shortcut keys never get here: the firmware consumes them before accessibility
        // services see them (see the stub module).
        if (event.keyCode != KeyEvent.KEYCODE_F5) return false
        if (!LauncherSettings.voiceAssistantEnabled) return false
        // Answer at once and do the work afterwards: if this call takes too long (~0.5 s, e.g.
        // while the launcher in this same process is busy), the system also hands the key to the
        // foreground app as well.
        when {
            firstDown -> handler.post(::voiceKeyDown)
            event.action == KeyEvent.ACTION_UP -> handler.post(::voiceKeyUp)
        }
        return true
    }

    /**
     * The remote streams its microphone while this key is held, so recording starts right away;
     * it only counts as a voice command once the key has been held past [HOLD_MS].
     */
    private fun voiceKeyDown() {
        if (!LauncherSettings.voiceAssistantEnabled) return
        // Another app is waiting for dictation: the key is its push-to-talk.
        VoiceRequests.onVoiceKey?.let {
            it(true)
            return
        }
        voice?.cancel()
        voice = if (VoiceModel.installed(this)) VoiceSession(this).also { it.start(loadModel = false) } else null
        listening = false
        if (voice != null) handler.postDelayed(startListening, HOLD_MS)
    }

    private fun voiceKeyUp() {
        VoiceRequests.onVoiceKey?.takeIf { LauncherSettings.voiceAssistantEnabled }?.let {
            it(false)
            return
        }
        handler.removeCallbacks(startListening)
        val session = voice
        voice = null
        if (!listening) {
            session?.cancel()
            return
        }
        listening = false
        val generation = VoiceRequests.generation
        showBubble("…")
        Thread {
            val text = session?.finish().orEmpty()
            // Recognition is slow and stays here; the command itself touches windows and toasts,
            // which need the main thread.
            handler.post {
                if (!LauncherSettings.voiceAssistantEnabled || generation != VoiceRequests.generation || instance !== this) return@post
                val result = when {
                    text == VoiceSession.NO_MODEL -> tr(R.string.voice_model_missing)
                    text.isBlank() -> tr(R.string.didnt_catch)
                    else -> VoiceCommands.run(this, text) ?: tr(R.string.didnt_understand, text)
                }
                showBubble(result)
                handler.postDelayed(hideBubbleTask, 2500)
            }
        }.start()
    }

    /** Small caption at the bottom of the screen for the voice key. */
    private fun showBubble(text: String) {
        handler.removeCallbacks(hideBubbleTask)
        bubble?.let {
            it.text = text
            return
        }
        val density = resources.displayMetrics.density
        val label = TextView(this).apply {
            this.text = text
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding((28 * density).toInt(), (14 * density).toInt(), (28 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 28 * density
                setColor(0xE6202328.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
        }
        runCatching { windows.addView(label, params) }
            .onSuccess { bubble = label }
            .onFailure { Log.w("PanelOverlay", "Caption window failed", it) }
    }

    private fun hideBubble() {
        val label = bubble ?: return
        bubble = null
        runCatching { windows.removeViewImmediate(label) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // The system is tearing the service down (e.g. AccessibilityGuard restarting it): close the
    // captions while the accessibility window token is still valid.
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideBubble()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        voice?.cancel()
        handler.removeCallbacksAndMessages(null)
        hideBubble()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** Holding the voice key this long turns the press into a voice command. */
        private const val HOLD_MS = 1000L


        @Volatile
        private var instance: PanelOverlay? = null

        /** Shows [text] at the bottom of the screen (null hides it), optionally hiding it later. */
        fun caption(text: String?, hideAfterMs: Long = 0) {
            val service = instance ?: return
            service.handler.post {
                if (text == null) service.hideBubble() else {
                    service.showBubble(text)
                    if (hideAfterMs > 0) service.handler.postDelayed(service.hideBubbleTask, hideAfterMs)
                }
            }
        }

        /** One of the system's global actions (lock screen, power dialog, ...); false when the service isn't running. */
        fun globalAction(action: Int): Boolean = instance?.performGlobalAction(action) == true

        /** Whether the service is connected, so it handles the voice key. */
        val running get() = instance != null

        /** Apply the voice preference without disconnecting accessibility. */
        fun featuresChanged() {
            val service = instance ?: return
            val apply = Runnable {

                if (!LauncherSettings.voiceAssistantEnabled) {
                    service.handler.removeCallbacks(service.startListening)
                    service.voice?.cancel()
                    service.voice = null
                    service.listening = false
                    service.hideBubble()
                    VoiceModel.releaseLater()
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else service.handler.post(apply)
        }

    }
}
