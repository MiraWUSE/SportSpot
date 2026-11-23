package com.mirawuse.sportspot.presentation.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapConnectivityTest {
    @Test
    fun `connection can move online offline and online again`() {
        var connection = MapConnection()
        assertEquals(MapNetworkStatus.NO_NETWORK, connection.status)
        connection = connection.available(10)
        assertEquals(MapNetworkStatus.UNVERIFIED, connection.status)
        connection = connection.capabilities(10, internet = true, validated = true)
        assertEquals(MapNetworkStatus.AVAILABLE, connection.status)
        connection = connection.lost(10)
        assertEquals(MapNetworkStatus.NO_NETWORK, connection.status)
        connection = connection.available(20).capabilities(20, internet = true, validated = true)
        assertEquals(MapNetworkStatus.AVAILABLE, connection.status)
    }

    @Test
    fun `missing validation means uncertainty rather than a proven offline connection`() {
        assertEquals(MapNetworkStatus.UNVERIFIED, mapNetworkStatus(null, validated = false))
        assertEquals(MapNetworkStatus.NO_NETWORK, mapNetworkStatus(false, validated = false))
        val available = MapConnection().available(10).capabilities(10, internet = true, validated = true)
        val unverified = available.capabilities(10, internet = true, validated = false)
        assertEquals(MapNetworkStatus.UNVERIFIED, unverified.status)
        assertEquals(MapNetworkStatus.AVAILABLE, unverified.capabilities(10, internet = true, validated = true).status)
    }

    @Test
    fun `late loss and capabilities of the old transport cannot override the new network`() {
        val old = MapConnection().available(10).capabilities(10, internet = true, validated = true)
        val switching = old.available(20)
        assertEquals(switching, switching.lost(10))
        assertEquals(switching, switching.capabilities(10, internet = false, validated = false))
        val new = switching.capabilities(20, internet = true, validated = false)
        assertEquals(MapNetworkStatus.UNVERIFIED, new.status)
        assertEquals(new, new.lost(10))
        val online = new.capabilities(20, internet = true, validated = true)
        assertEquals(online, online.capabilities(10, internet = false, validated = false))
        assertEquals(MapNetworkStatus.AVAILABLE, online.status)
    }
}
