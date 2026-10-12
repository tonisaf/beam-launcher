package com.home.tiles

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Small maintenance hooks for adb, guarded by the DUMP permission (held by the shell, not by apps):
 *   adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es background XMB --ez bg_animation true
 */
class AdbCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LauncherSettings.init(context)
        // Appearance, for testing without the remote: --es background XMB, --ez bg_animation false
        intent.getStringExtra("background")?.let { name ->
            val index = Backgrounds.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (index >= 0) LauncherSettings.background = index
            resultData = if (index >= 0) "ok" else "unknown background"
        }
        // --es home_layout switch|googletv
        intent.getStringExtra("home_layout")?.takeIf { it == HOME_SWITCH || it == HOME_GOOGLE_TV }?.let { LauncherSettings.homeLayout = it }
        if (intent.hasExtra("bg_animation")) {
            LauncherSettings.bgAnimation = intent.getBooleanExtra("bg_animation", true)
            resultData = "ok"
        }
        // Voice model: --ez voice_install true unzips the pushed zip (see VoiceModelProvider),
        // --es voice_url HTTPS_URL [--es voice_sha256 HEX] downloads it instead.
        // Both run in the background (goAsync keeps the receiver alive); watch tag Voice.
        if (intent.hasExtra("voice_install") || intent.hasExtra("voice_url")) {
            val url = intent.getStringExtra("voice_url")
            val sha256 = intent.getStringExtra("voice_sha256")
            val app = context.applicationContext
            val pending = goAsync()
            Thread {
                try {
                    if (url != null) VoiceModel.download(app, url, sha256) else VoiceModel.installFrom(VoiceModelProvider.pushedZip(app), app)
                } finally {
                    pending.finish()
                }
            }.start()
            resultData = "installing"
        }
        if (intent.hasExtra("voice_status")) resultData = "installed=${VoiceModel.installed(context)}"
        // Voice commands that open apps: --es voice_app "Name|package1,package2|phrase one;phrase two"
        // adds one, --es voice_app_remove Name removes it, --ez voice_apps true lists the added ones.
        intent.getStringExtra("voice_app")?.let { spec ->
            val app = parseVoiceApps(spec).firstOrNull()
            resultData = if (app == null) "error: expected Name|package1,package2|phrase one;phrase two" else {
                VoiceApps.add(context, app)
                "ok ${app.name}: ${app.phrases}"
            }
        }
        intent.getStringExtra("voice_app_remove")?.let { name ->
            resultData = if (VoiceApps.remove(context, name)) "ok" else "no such app"
        }
        if (intent.hasExtra("voice_apps")) resultData = formatVoiceApps(VoiceApps.custom(context)).ifEmpty { "none" }
        // --es voice_test "фраза": run a command as if it had been spoken.
        intent.getStringExtra("voice_test")?.let { resultData = VoiceCommands.run(context, it) ?: "no match" }
        // --es global lock|power_dialog: an accessibility global action through Beam's service
        intent.getStringExtra("global")?.let { name ->
            val action = when (name) {
                "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                "power_dialog" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
                else -> null
            }
            resultData = if (action == null) "error: expected lock or power_dialog" else "performed=${PanelOverlay.globalAction(action)}"
        }
    }
}
