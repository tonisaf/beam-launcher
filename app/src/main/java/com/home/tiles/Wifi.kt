package com.home.tiles

import android.content.Context
import android.net.wifi.WifiManager

/** How strong a signal is, in the words the panel uses. */
internal enum class Signal { EXCELLENT, GOOD, FAIR, WEAK }

internal enum class Security { OPEN, WEP, WPA, WPA2, WPA3 }

internal fun signalOf(rssiDbm: Int): Signal = when {
    rssiDbm >= -55 -> Signal.EXCELLENT
    rssiDbm >= -67 -> Signal.GOOD
    rssiDbm >= -75 -> Signal.FAIR
    else -> Signal.WEAK
}

/** The band a channel frequency (MHz) belongs to, as "2.4", "5" or "6" GHz; null for anything else. */
internal fun bandOf(frequencyMhz: Int): String? = when (frequencyMhz) {
    in 2400..2500 -> "2.4"
    in 4900..5899 -> "5"
    in 5925..7125 -> "6"
    else -> null
}

/** The strongest protection a scan result's capabilities string ("[WPA2-PSK-CCMP][ESS]") advertises. */
internal fun securityOf(capabilities: String): Security {
    val c = capabilities.uppercase()
    return when {
        "SAE" in c || "WPA3" in c -> Security.WPA3
        "WPA2" in c || "RSN" in c -> Security.WPA2
        "WPA" in c -> Security.WPA
        "WEP" in c -> Security.WEP
        else -> Security.OPEN
    }
}

internal data class WifiNetwork(val ssid: String, val rssi: Int, val frequency: Int, val security: Security)

/**
 * One entry per network name, from its strongest access point (a router usually shows up once per
 * band), strongest first. Hidden networks have no name and are left out.
 */
internal fun listNetworks(scan: List<WifiNetwork>): List<WifiNetwork> =
    scan.filter { it.ssid.isNotBlank() }
        .groupBy { it.ssid }
        .map { (_, accessPoints) -> accessPoints.maxByOrNull { it.rssi }!! }
        .sortedByDescending { it.rssi }

/** What the Wi-Fi page shows: the current connection and what else is in range. */
internal data class WifiStatus(
    val enabled: Boolean,
    val ssid: String?,
    val rssi: Int?,
    val frequency: Int?,
    val speedMbps: Int?,
    val ip: String?,
    val networks: List<WifiNetwork>,
)

/**
 * Read-only: from Android 11 an app can't join networks, and the firmware has no Wi-Fi API of its
 * own, so connecting stays on XGIMI's settings page. Needs ACCESS_FINE_LOCATION for the network
 * name and the scan list, which Beam already holds for Bluetooth search.
 */
internal object WifiReader {
    private fun manager(context: Context) = context.applicationContext.getSystemService(WifiManager::class.java)

    /** Blocking system calls: off the main thread. Null if the device has no Wi-Fi. */
    @Suppress("DEPRECATION")
    fun read(context: Context): WifiStatus? = runCatching {
        val wifi = manager(context) ?: return null
        if (!wifi.isWifiEnabled) return WifiStatus(false, null, null, null, null, null, emptyList())
        val info = wifi.connectionInfo
        // Without location access the system answers "<unknown ssid>": no name rather than that.
        val connected = info != null && info.networkId != -1
        val name = info?.ssid?.trim('"')?.takeIf { connected && it != WifiManager.UNKNOWN_SSID }
        val scan = wifi.scanResults.map { WifiNetwork(it.SSID.orEmpty(), it.level, it.frequency, securityOf(it.capabilities)) }
        WifiStatus(
            enabled = true,
            ssid = name,
            rssi = info?.rssi?.takeIf { connected },
            frequency = info?.frequency?.takeIf { connected && it > 0 },
            speedMbps = info?.linkSpeed?.takeIf { connected && it > 0 },
            ip = if (connected) ipv4Address() else null,
            networks = listNetworks(scan),
        )
    }.getOrNull()

    /** Asks for a fresh scan. The system limits how often it is honoured; the old list stays then. */
    @Suppress("DEPRECATION")
    fun scan(context: Context): Boolean = runCatching { manager(context)?.startScan() == true }.getOrDefault(false)
}
