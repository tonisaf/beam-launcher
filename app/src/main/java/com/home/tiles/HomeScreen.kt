package com.home.tiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp


// The row is centred in the space under the top bar; this lifts it to just above the screen's middle.
private val RowLift = 150.dp

/** One tile in the home row. Special tiles (drive) come before the apps. */
internal sealed class RowItem(val key: String, val title: String, val subtitle: String = "") {
    class App(val entry: AppEntry) : RowItem(entry.pkg, entry.label)
    class Usb(val drive: UsbDrive) : RowItem("usb:${drive.key}", tr(R.string.usb_drive), drive.label)
    class Hdmi(val input: HdmiInput) : RowItem("hdmi:${input.id}", input.label)
    class All(count: Int) : RowItem("__all__", tr(R.string.all_apps), tr(R.string.apps_count, count))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    repo: AppRepository,
    apps: List<AppEntry>,
    resumeTick: Int,
    onOpenAll: () -> Unit,
    onOpenPanel: () -> Unit,
    onOptions: (AppEntry) -> Unit,
    onSetup: (() -> Unit)? = null,
    moving: HomeMove? = null,
    focusApp: String? = null,
) {
    val context = LocalContext.current
    val channels by rememberTvChannels(resumeTick)
    val placements = channelPlacements(channels, LauncherSettings.channelRules, LauncherSettings.secondRow)
    val showContinue = placements.any { it.position != ChannelPosition.HIDDEN && it.channel.items.isNotEmpty() }
    LaunchedEffect(resumeTick) { context.requestChannelRefresh() }
    val drives by rememberUsbDrives()
    val inputs by rememberHdmiInputs(resumeTick)
    val items = buildList {
        if (LauncherSettings.hdmiTile) inputs.forEach { add(RowItem.Hdmi(it)) }
        if (LauncherSettings.usbTile) drives.forEach { add(RowItem.Usb(it)) }
        apps.filter { !it.hidden }.forEach { add(RowItem.App(it)) }
        add(RowItem.All(apps.size))
    }
    val keys = items.map { it.key }
    fun clickFor(item: RowItem): () -> Unit = if (moving != null) ({}) else when (item) {
        is RowItem.App -> { { context.launchApp(item.entry) } }
        is RowItem.Usb -> { { context.openUsb(item.drive) } }
        is RowItem.Hdmi -> { { context.openHdmi(item.input) } }
        is RowItem.All -> onOpenAll
    }
    fun longClickFor(item: RowItem): () -> Unit = { if (moving == null && item is RowItem.App) onOptions(item.entry) }
    BackHandler {}

    if (LauncherSettings.homeLayout == HOME_GOOGLE_TV) {
        GoogleTvHome(
            repo = repo,
            items = items.filter { it !is RowItem.All },
            apps = apps,
            channels = placements,
            resumeTick = resumeTick,
            onOpenPanel = onOpenPanel,
            onSetup = onSetup,
            clickFor = ::clickFor,
            longClickFor = ::longClickFor,
            onOptions = onOptions,
            moving = moving,
            focusApp = focusApp,
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        // With a channel row below there is no spare height to lift into.
        val lift = if (showContinue) 0.dp else RowLift
        TopBar(onOpenAll, onOpenPanel, onSetup)
        // The Switch layout: equal tiles in a normally scrolling row.
        ClassicHome(repo, items, resumeTick, placements, Modifier.weight(1f).padding(bottom = lift), ::clickFor, ::longClickFor, moving, focusApp)
    }
}

@Composable
internal fun RowItemArt(repo: AppRepository, item: RowItem) {
    when (item) {
        is RowItem.App -> {
            val art by rememberArt(repo, item.entry)
            AppArt(art, item.entry.label)
        }
        is RowItem.Usb -> UsbArt()
        is RowItem.Hdmi -> HdmiArt()
        is RowItem.All -> AllAppsArt()
    }
}

@Composable
private fun TopBar(onOpenAll: () -> Unit, onOpenPanel: () -> Unit, onSetup: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 60.dp, top = 30.dp)
            .height(100.dp),
    ) {
        ActionButtons(onOpenAll, onOpenPanel, Modifier.padding(top = 2.dp), onSetup)
        Spacer(Modifier.weight(1f))
        val nowPlaying by rememberNowPlaying()
        nowPlaying?.takeIf { LauncherSettings.nowPlaying }?.let {
            NowPlayingBar(it)
            Spacer(Modifier.width(32.dp))
        }
        StatusCluster()
    }
}

/** Clock, network and battery; they share one text size, icon height and spacing. */
@Composable
internal fun StatusCluster() {
    Row(Modifier.height(66.dp), verticalAlignment = Alignment.CenterVertically) {
        Clock(StatusTextSize)
        Spacer(Modifier.width(StatusGap))
        NetworkIcon()
        Spacer(Modifier.width(StatusGap))
        BatteryIndicator()
    }
}

/** The round shortcut buttons in the top bar. */
@Composable
internal fun ActionButtons(onOpenAll: () -> Unit, onOpenPanel: () -> Unit, modifier: Modifier = Modifier, onSetup: (() -> Unit)? = null) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = modifier) {
        RoundButton(Icons.Rounded.Apps, Color(0xFF1E88E5), tr(R.string.all_apps), onOpenAll)
        RoundButton(Icons.Rounded.Tune, Color(0xFF2EB85C), tr(R.string.quick_settings)) { context.openQuickPanel() }
        RoundButton(Icons.Rounded.Settings, Color(0xFF8A8A8A), tr(R.string.settings)) { context.openSettings() }
        RoundButton(Icons.Rounded.Palette, Color(0xFF8E44AD), tr(R.string.appearance), onOpenPanel)
        // Only while something Beam needs from adb is missing.
        onSetup?.let { RoundButton(Icons.Rounded.Warning, Color(0xFFF5A623), tr(R.string.setup_button), it) }
    }
}
