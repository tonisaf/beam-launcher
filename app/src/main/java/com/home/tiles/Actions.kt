package com.home.tiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

fun Context.launchApp(entry: AppEntry) = start(
    Intent(Intent.ACTION_MAIN)
        .setComponent(entry.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
)

fun Context.launchPackage(pkg: String) {
    val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
        ?: packageManager.getLaunchIntentForPackage(pkg)
    if (intent == null) toast(tr(R.string.app_not_installed)) else start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.isInstalled(pkg: String) = runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess

fun Context.openSettings() {
    if (tryActivity(Intent(Settings.ACTION_SETTINGS))) return
    // Some TV firmware exposes its stock settings only as an exported service.
    if (tryStockSettingsService("com.xgimi.settings.SETTINGS")) return
    for (pkg in listOf("com.android.tv.settings", "com.google.android.tv.settings")) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null && tryActivity(intent)) return
    }
    toast(tr(R.string.cant_open))
}

fun Context.openQuickPanel() {
    if (!tryStockSettingsService("com.xgimi.misckey.MISCKEY")) openSettings()
}

private fun Context.tryActivity(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) { false }

/** Opens the manufacturer's own UI; does not control hardware or replace its settings. */
private fun Context.tryStockSettingsService(action: String): Boolean = try {
    val intent = Intent(action).setPackage("com.android.newsettings")
    val service = packageManager.resolveService(intent, 0)?.serviceInfo
    if (service?.exported == true && service.enabled) startService(intent) != null else false
} catch (_: Exception) { false }

fun Context.openAppInfo(pkg: String) = start(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

@Suppress("DEPRECATION")
fun Context.uninstall(pkg: String) = start(
    Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

private fun Context.start(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        toast(tr(R.string.cant_open))
    }
}

private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
