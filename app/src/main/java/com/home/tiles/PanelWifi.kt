package com.home.tiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often the page re-reads the connection (the signal moves). */
private const val WIFI_REFRESH_MS = 4_000L

internal fun signalName(signal: Signal): String = tr(
    when (signal) {
        Signal.EXCELLENT -> R.string.wifi_signal_excellent
        Signal.GOOD -> R.string.wifi_signal_good
        Signal.FAIR -> R.string.wifi_signal_fair
        Signal.WEAK -> R.string.wifi_signal_weak
    },
)

private fun securityName(security: Security): String = when (security) {
    Security.OPEN -> tr(R.string.wifi_open)
    Security.WEP -> "WEP"
    Security.WPA -> "WPA"
    Security.WPA2 -> "WPA2"
    Security.WPA3 -> "WPA3"
}

private fun bandName(frequency: Int): String? = bandOf(frequency)?.let { tr(R.string.wifi_ghz, it) }

/**
 * The connection and the networks in range. Joining a network stays on XGIMI's own page (an app
 * can't do it on Android 11), so every network row leads there.
 */
@Composable
internal fun WifiPage(onStockPage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<WifiStatus?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            status = withContext(Dispatchers.IO) { WifiReader.read(context) }
            loaded = true
            delay(WIFI_REFRESH_MS)
        }
    }
    val wifi = status
    if (wifi == null) {
        T(if (loaded) tr(R.string.wifi_unavailable) else tr(R.string.loading), 14.sp, color = PanelDim)
        return
    }
    if (!wifi.enabled) {
        T(tr(R.string.wifi_off), 14.sp, color = PanelDim)
        Section(tr(R.string.wifi_settings))
        ListRow(tr(R.string.wifi_xgimi_page), onStockPage)
        return
    }

    Section(tr(R.string.wifi_connection))
    if (wifi.ssid == null) {
        T(tr(R.string.wifi_not_connected), 15.sp, color = PanelText)
    } else {
        val signal = wifi.rssi?.let { "${signalName(signalOf(it))} · $it ${tr(R.string.wifi_dbm)}" }
        FactsCard(
            listOfNotNull(
                tr(R.string.wifi_network) to wifi.ssid,
                signal?.let { tr(R.string.wifi_signal) to it },
                wifi.frequency?.let { f -> bandName(f)?.let { tr(R.string.wifi_band) to it } },
                wifi.speedMbps?.let { tr(R.string.wifi_speed) to tr(R.string.wifi_mbps, it) },
                wifi.ip?.let { tr(R.string.ip_address) to it },
            ),
        )
    }

    Section(if (scanning) tr(R.string.wifi_searching) else tr(R.string.wifi_nearby))
    ListRow(tr(R.string.wifi_rescan)) {
        if (!scanning) {
            scanning = true
            scope.launch {
                withContext(Dispatchers.IO) { WifiReader.scan(context) }
                // A scan takes a few seconds; the periodic read then shows the new list.
                delay(WIFI_SCAN_MS)
                status = withContext(Dispatchers.IO) { WifiReader.read(context) }
                scanning = false
            }
        }
    }
    wifi.networks.forEach { net ->
        val note = listOfNotNull(
            "${signalName(signalOf(net.rssi))} · ${net.rssi}",
            bandName(net.frequency),
            securityName(net.security),
        ).joinToString(" · ")
        Chip(net.ssid, net.ssid == wifi.ssid, Modifier.fillMaxWidth().padding(bottom = 8.dp), note = note, onClick = onStockPage)
    }
    if (wifi.networks.isEmpty()) T(tr(R.string.wifi_no_networks), 14.sp, color = PanelDim)
    T(tr(R.string.wifi_connect_hint), 14.sp, color = PanelDim)

    Section(tr(R.string.wifi_settings))
    ListRow(tr(R.string.wifi_xgimi_page), onStockPage)
}

private const val WIFI_SCAN_MS = 4_000L
