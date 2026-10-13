package com.home.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiModelTest {
    @Test
    fun signalIsGradedByStrength() {
        assertEquals(Signal.EXCELLENT, signalOf(-40))
        assertEquals(Signal.EXCELLENT, signalOf(-55))
        assertEquals(Signal.GOOD, signalOf(-56))
        assertEquals(Signal.GOOD, signalOf(-67))
        assertEquals(Signal.FAIR, signalOf(-70))
        assertEquals(Signal.WEAK, signalOf(-76))
        assertEquals(Signal.WEAK, signalOf(-95))
    }

    @Test
    fun bandComesFromTheChannelFrequency() {
        assertEquals("2.4", bandOf(2437))
        assertEquals("5", bandOf(5805))
        assertEquals("5", bandOf(5180))
        assertEquals("6", bandOf(6115))
        assertNull(bandOf(0))
        assertNull(bandOf(900))
    }

    @Test
    fun securityIsTheStrongestAdvertised() {
        assertEquals(Security.OPEN, securityOf("[ESS]"))
        assertEquals(Security.WEP, securityOf("[WEP][ESS]"))
        assertEquals(Security.WPA, securityOf("[WPA-PSK-TKIP][ESS]"))
        assertEquals(Security.WPA2, securityOf("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(Security.WPA2, securityOf("[RSN-PSK-CCMP][ESS]"))
        assertEquals(Security.WPA3, securityOf("[RSN-SAE-CCMP][ESS]"))
        // A WPA2/WPA3 router is reported by its best mode.
        assertEquals(Security.WPA3, securityOf("[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"))
    }

    @Test
    fun routerSeenOnTwoBandsIsListedOnceByItsStrongest() {
        val scan = listOf(
            WifiNetwork("Home", -70, 2437, Security.WPA2),
            WifiNetwork("Home", -48, 5805, Security.WPA2),
            WifiNetwork("Cafe", -60, 2412, Security.OPEN),
        )
        val list = listNetworks(scan)
        assertEquals(listOf("Home", "Cafe"), list.map { it.ssid })
        assertEquals(-48, list.first().rssi)
        assertEquals(5805, list.first().frequency)
    }

    @Test
    fun hiddenNetworksAreLeftOut() {
        val scan = listOf(WifiNetwork("", -50, 2412, Security.WPA2), WifiNetwork("  ", -52, 2412, Security.WPA2))
        assertEquals(emptyList<WifiNetwork>(), listNetworks(scan))
    }
}
