package com.home.tiles

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The XGIMI firmware resets the enabled accessibility services on boot, and a service whose process
 * got killed is marked crashed and never rebound. The launcher starts on boot and on every return
 * home, so it re-enables (or restarts) our [PanelOverlay] service. Needs WRITE_SECURE_SETTINGS,
 * granted over adb.
 *
 * It goes easy on the system: on this loaded projector the service can take several seconds to
 * connect, and rewriting the setting or losing the process while it connects crashed system_server
 * (Android 11: "Death link does not exist" in AccessibilityServiceConnection.binderDied, seen as a
 * reboot). So it waits for a connection that is merely slow, restarts the service only when it
 * really isn't coming, and not more often than every [MIN_GAP_MS].
 */
object AccessibilityGuard {
    private const val TAG = "AccessibilityGuard"

    /** How long a service that was just enabled gets to connect before it counts as stuck. */
    private const val CONNECT_MS = 15_000L

    /** Two writes in a row get coalesced, so the system must see the removal first. */
    private const val REMOVE_PAUSE_MS = 1_500L

    /** How long to wait for the system to unbind a service that was just removed. */
    private const val UNBIND_MS = 5_000L

    private const val POLL_MS = 500L
    private const val ATTEMPTS = 2

    /** Restoring is skipped within this time of the last one: the next return home tries again. */
    private const val MIN_GAP_MS = 30_000L

    private val overlay = ComponentName("com.home.tiles", "com.home.tiles.PanelOverlay")
    private val russifier = ComponentName("com.beam.russifier.boot", "com.beam.russifier.boot.StartupBridge")
    private val restarting = AtomicBoolean(false)

    @Volatile
    private var lastRestore = -MIN_GAP_MS

    fun ensure(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        val app = context.applicationContext
        val restoreRussifier = russifierAvailable(app) && !isEnabled(app, russifier)
        if (isEnabled(app) && isBound(app) && !restoreRussifier) return
        if (SystemClock.elapsedRealtime() - lastRestore < MIN_GAP_MS) return
        if (!restarting.compareAndSet(false, true)) return
        lastRestore = SystemClock.elapsedRealtime()
        // Off the caller's thread: restoring waits for the system to bind (or unbind) the service.
        Thread {
            runCatching {
                if (russifierAvailable(app) && !isEnabled(app, russifier)) {
                    write(app, services(app) + russifier.flattenToString())
                    Settings.Secure.putString(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                    Log.i(TAG, "Re-enabled russifier startup service")
                    waitFor(CONNECT_MS) { isBound(app, russifier) }
                }
                if (!isEnabled(app) || !isBound(app)) restore(app)
            }.onFailure { Log.w(TAG, "Could not restore accessibility services", it) }
            restarting.set(false)
        }.start()
    }

    private fun restore(app: Context) {
        // Enabled but not connected: usually just slow. Touching it now is what hurts.
        if (isEnabled(app) && waitFor(CONNECT_MS) { isBound(app) }) {
            Log.i(TAG, "Panel service connected on its own")
            return
        }
        // After a force stop the service is dropped *and* marked crashed; re-adding it doesn't
        // always clear that, so restart it (remove, wait for the unbind, add) until it's bound.
        for (attempt in 1..ATTEMPTS) {
            val wasEnabled = isEnabled(app)
            if (wasEnabled) {
                write(app, others(app))
                Thread.sleep(REMOVE_PAUSE_MS)
                waitFor(UNBIND_MS) { !isBound(app) }
            }
            write(app, others(app) + overlay.flattenToString())
            Settings.Secure.putString(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            Log.i(TAG, "${if (wasEnabled) "Restarted" else "Re-enabled"} panel service (attempt $attempt)")
            if (waitFor(CONNECT_MS) { isBound(app) }) return
            Log.w(TAG, "Panel service not bound after ${CONNECT_MS / 1000} s (attempt $attempt)")
        }
    }

    /** Polls [condition] until it holds; false if it still doesn't after [timeoutMs]. */
    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (condition()) return true
            if (SystemClock.elapsedRealtime() >= end) return false
            Thread.sleep(POLL_MS)
        }
    }

    private fun services(context: Context) =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() }

    private fun others(context: Context) = services(context).filter { ComponentName.unflattenFromString(it) != overlay }

    private fun russifierAvailable(context: Context): Boolean = runCatching {
        val service = context.packageManager.getServiceInfo(russifier, 0)
        service.enabled && service.applicationInfo.enabled
    }.getOrDefault(false)

    private fun isEnabled(context: Context, component: ComponentName = overlay) =
        services(context).any { ComponentName.unflattenFromString(it) == component }

    private fun isBound(context: Context, component: ComponentName = overlay) = context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { ComponentName.unflattenFromString(it.id) == component }

    private fun write(context: Context, entries: List<String>) {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, entries.joinToString(":"))
    }
}
