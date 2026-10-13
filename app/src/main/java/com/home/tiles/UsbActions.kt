package com.home.tiles

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.widget.Toast

private const val USB_APP = "usbApp"

private fun Context.usbPrefs() = getSharedPreferences("settings", Context.MODE_PRIVATE)

private fun Context.launchUsbIntent(intent: Intent): Boolean = runCatching {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Select any installed launcher app, including file managers without a standard file category. */
internal fun Context.chooseUsbApp(onSelected: (() -> Unit)? = null) {
    val apps = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { category -> packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
        .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName != packageName }
        .distinctBy { it.activityInfo.packageName }
        .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    val labels = arrayOf(tr(R.string.usb_auto)) + apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle(tr(R.string.usb_choose))
        .setItems(labels) { _, index ->
            val component = apps.getOrNull(index - 1)?.activityInfo?.let { ComponentName(it.packageName, it.name).flattenToString() }
            usbPrefs().edit().putString(USB_APP, component).apply()
            onSelected?.invoke()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

internal fun Context.openUsb(drive: UsbDrive) {
    val volume = getSystemService(StorageManager::class.java)?.storageVolumes?.firstOrNull {
        it.isRemovable && it.state == Environment.MEDIA_MOUNTED &&
            (it.uuid ?: it.getDescription(this)) == drive.key
    }
    if (volume == null) {
        Toast.makeText(this, tr(R.string.usb_removed), Toast.LENGTH_SHORT).show()
        return
    }
    val selected = usbPrefs().getString(USB_APP, null)?.let(ComponentName::unflattenFromString)
    if (selected != null) {
        if (launchUsbIntent(Intent(Intent.ACTION_MAIN).setComponent(selected))) return
        usbPrefs().edit().remove(USB_APP).apply()
    }
    // Browse the root via the system document UI, rather than granting Beam access to all files.
    val root = volume.uuid?.let { DocumentsContract.buildRootUri("com.android.externalstorage.documents", it) }
    if (root != null && launchUsbIntent(Intent(Intent.ACTION_VIEW).setDataAndType(root, "vnd.android.document/root"))) return
    val files = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES)
    if (packageManager.resolveActivity(files, 0) != null && launchUsbIntent(files)) return
    // Compatibility fallback for firmware without DocumentsUI or a file-app category.
    val stock = packageManager.getLeanbackLaunchIntentForPackage("com.xgimi.filemanager")
        ?: packageManager.getLaunchIntentForPackage("com.xgimi.filemanager")
    if (stock != null && launchUsbIntent(stock)) return
    Toast.makeText(this, tr(R.string.usb_unavailable), Toast.LENGTH_LONG).show()
    chooseUsbApp { if (usbPrefs().getString(USB_APP, null) != null) openUsb(drive) }
}

