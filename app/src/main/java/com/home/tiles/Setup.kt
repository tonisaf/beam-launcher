package com.home.tiles

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * What Beam needs from the system that only adb can give (a launcher can't ask for these in a
 * dialog). Without them it still runs, but a feature quietly does nothing, so a new user installing
 * just the APK sees what is missing and the command for each. tools/restore.ps1 does it all.
 */
object Setup {
    /** One missing thing: what it is, what doesn't work without it, and the adb command that fixes it. */
    class Item(val id: String, val title: String, val effect: String, val command: String)

    private const val PKG = "com.home.tiles"
    private const val SEEN = "seen"

    /** The items that aren't in place right now; empty when Beam is fully set up. */
    fun missing(context: Context): List<Item> = buildList {
        fun grant(id: String, permission: String, title: Int, effect: Int) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                add(Item(id, tr(title), tr(effect), "adb shell pm grant $PKG $permission"))
            }
        }
        grant("secure", Manifest.permission.WRITE_SECURE_SETTINGS, R.string.setup_secure, R.string.setup_secure_effect)
        if (PKG !in NotificationManagerCompat.getEnabledListenerPackages(context)) {
            add(Item("listener", tr(R.string.setup_listener), tr(R.string.setup_listener_effect), "adb shell cmd notification allow_listener $PKG/$PKG.MediaListener"))
        }
        if (!usageAllowed(context)) {
            add(Item("usage", tr(R.string.setup_usage), tr(R.string.setup_usage_effect), "adb shell appops set $PKG GET_USAGE_STATS allow"))
        }
        grant("tv", "android.permission.READ_TV_LISTINGS", R.string.setup_tv, R.string.setup_tv_effect)
        grant("location", Manifest.permission.ACCESS_FINE_LOCATION, R.string.setup_location, R.string.setup_location_effect)
        if (LauncherSettings.voiceAssistantEnabled)
            grant("mic", Manifest.permission.RECORD_AUDIO, R.string.setup_mic, R.string.setup_mic_effect)
        if (!Settings.System.canWrite(context)) {
            add(Item("write", tr(R.string.setup_write), tr(R.string.setup_write_effect), "adb shell appops set $PKG WRITE_SETTINGS allow"))
        }
        // Null when the system doesn't show us the default home (package visibility): say nothing then.
        val home = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (home != null && home != PKG) {
            add(Item("home", tr(R.string.setup_home), tr(R.string.setup_home_effect), "adb shell cmd package set-home-activity $PKG/.MainActivity"))
        }
    }

    private fun usageAllowed(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), PKG)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), PKG)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * True once per distinct set of missing items: the dialog opens by itself the first time (and
     * again when something else goes missing), while the top bar button is always there for it.
     */
    fun shouldAutoShow(context: Context, items: List<Item>): Boolean {
        val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)
        val now = items.joinToString(",") { it.id }
        if (items.isEmpty() || prefs.getString(SEEN, null) == now) return false
        prefs.edit().putString(SEEN, now).apply()
        return true
    }
}

/** The projector's own IPv4 address (the one to give `adb connect`), or null off the network. */
internal fun ipv4Address(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress
}.getOrNull()

/** The list of what is missing, with the commands to run on a computer with adb. */
@Composable
internal fun SetupDialog(items: List<Setup.Item>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val ip = remember { ipv4Address() ?: "<IP>" }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(900.dp)
                .arrowSoundTracker()
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .background(Colors.Surface, RoundedCornerShape(18.dp))
                .padding(28.dp),
        ) {
            T(tr(R.string.setup_title), 26.sp, weight = androidx.compose.ui.text.font.FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Paragraph(tr(R.string.setup_intro, ip), 16.sp, Colors.TextDim)
            Spacer(Modifier.height(16.dp))
            items.forEach { item ->
                T(item.title, 19.sp)
                T(item.effect, 15.sp, color = Colors.TextDim)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Colors.Button, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                (listOf("adb connect $ip:5555") + items.map { it.command }).forEach { line ->
                    BasicText(
                        line,
                        style = TextStyle(color = Colors.Text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(if (focused) Colors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .focusRequester(first)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) Sounds.navigate()
                    }
                    .clickable(remember { MutableInteractionSource() }, null) {
                        Sounds.activate()
                        onDismiss()
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                T(tr(R.string.setup_close), 21.sp, color = if (focused) Color.White else Colors.Text)
            }
        }
    }
}

@Composable
private fun Paragraph(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color) {
    BasicText(text, Modifier.padding(end = 4.dp), style = TextStyle(color = color, fontSize = size))
}
