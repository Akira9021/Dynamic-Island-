package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class HotspotTest {
    private fun parse(vararg text: String, ongoing: Boolean = true, trusted: Boolean = true) =
        HotspotNotificationParser.parse(trusted, ongoing, text.toList())
    private fun snapshot(key: String, count: Int?, at: Long = 10) = HotspotSnapshot(key, HotspotEvidence(true, count), at)

    @Test fun untrustedAppsCannotCreateHotspotCards() {
        assertNull(parse("Hotspot is on", "2 devices connected", trusted = false))
    }
    @Test fun genericSettingsSuggestionIsNotAnActiveHotspot() {
        assertNull(parse("Configure your mobile hotspot", ongoing = false))
        assertNull(parse("Hotspot inactive", ongoing = false)?.takeIf { it.enabled })
    }
    @Test fun unrelatedSystemTrafficAndUsbTetheringDoNotBecomeWifiHotspots() {
        assertNull(parse("USB tethering active", "2 devices connected"))
        assertNull(parse("Downloading", "12 MB/s"))
        assertNull(parse("YouTube", "Playing"))
        assertNull(parse("Hotspot Shield", "VPN is active"))
        assertNull(parse("MyHotspotApp", "VPN is active"))
        assertNull(parse("Configure hotspot when Wi-Fi is active", ongoing = false))
    }
    @Test fun englishZeroOneAndTwoClientsAreParsed() {
        for (count in 0..2) assertEquals(HotspotEvidence(true, count), parse("Mobile hotspot", "$count devices connected"))
        assertEquals(1, parse("Hotspot", "1 connected device")?.clients)
        assertEquals(2, parse("Hotspot", "Connected devices: 2")?.clients)
    }
    @Test fun russianNotificationVariantsAreParsed() {
        for (text in listOf("Подключено устройств: 2", "Подключено 2 устройства", "2 подключённых устройства", "2 устройства подключено")) {
            assertEquals(text, HotspotEvidence(true, 2), parse("Точка доступа включена", text))
        }
        assertEquals(0, parse("Точка доступа", "Устройства не подключены")?.clients)
    }
    @Test fun supportedTranslatedClientPhrasesAreParsed() {
        for ((title, text) in listOf("Hotspot" to "2 perangkat terhubung", "Ponto de acesso" to "2 dispositivos conectados",
            "Punto de acceso" to "2 dispositivos conectados", "Point d’accès" to "2 appareils connectés", "Кіру нүктесі" to "2 құрылғы қосылған")) {
            assertEquals(text, 2, parse(title, text)?.clients)
        }
    }
    @Test fun unknownClientCountIsNotZeroOrTheSsidNumber() {
        assertEquals(HotspotEvidence(true), parse("Mobile hotspot", "Pixel 8 Pro · 2.4 GHz · 12 MB"))
        assertEquals(HotspotEvidence(true), parse("Точка доступа", "AndroidAP123"))
    }
    @Test fun contradictoryCountsAreUnknown() {
        assertNull(parse("Hotspot", "1 device connected", "2 devices connected")?.clients)
        assertNull(parse("Hotspot", "No devices connected", "2 devices connected")?.clients)
    }
    @Test fun explicitNoClientsIsARealZero() {
        assertEquals(0, parse("Hotspot", "No devices connected")?.clients)
    }
    @Test fun disabledHotspotIsNotConfusedWithADisconnectedClient() {
        assertEquals(HotspotEvidence(false), parse("Точка доступа выключена"))
        assertEquals(HotspotEvidence(false), parse("Mobile hotspot disabled"))
        assertEquals(HotspotEvidence(false), parse("Hotspot", "Disabled"))
        assertEquals(HotspotEvidence(true), parse("Точка доступа", "Устройство отключено"))
    }
    @Test fun longOrUnsupportedCountsStayUnknown() {
        assertNull(parse("Hotspot", "10000 devices connected")?.clients)
        assertNull(parse("Hotspot", "1,000 devices connected")?.clients)
        assertNull(parse("Hotspot", "Supports up to 10 devices")?.clients)
    }
    @Test fun initialStateAndSourceLossStayUnknown() {
        val t = HotspotTracker()
        assertEquals(HotspotStatus(), t.status())
        assertEquals(HotspotStatus(), t.broadcastUnavailable())
        assertEquals(HotspotStatus(), t.replace(emptyList()))
    }
    @Test fun broadcastOnlyCardPersistsWithoutAClientCount() {
        val t = HotspotTracker()
        assertEquals(HotspotStatus(true), t.broadcast(true, 10))
        repeat(10) { assertEquals(HotspotStatus(true), t.status()) }
        assertEquals(HotspotStatus(false), t.broadcast(false, 20))
    }
    @Test fun zeroOneTwoAndDisconnectUpdatesReplaceTheCount() {
        val t = HotspotTracker()
        t.broadcast(true, 1)
        for (n in listOf(0, 1, 2, 1, 0)) assertEquals(HotspotStatus(true, n), t.notification(snapshot("system", n)))
    }
    @Test fun removalDropsTheCountWithoutPretendingToSwitchOff() {
        val t = HotspotTracker()
        t.broadcast(true, 1)
        t.notification(snapshot("system", 2))
        assertEquals(HotspotStatus(true), t.remove("system"))
        t.broadcastUnavailable()
        t.notification(snapshot("system", 2))
        assertEquals(HotspotStatus(), t.remove("system"))
    }
    @Test fun reconnectUsesTheCurrentSnapshotAndDropsRemovedEntries() {
        val t = HotspotTracker()
        t.notification(snapshot("old", 1))
        assertEquals(HotspotStatus(true, 2), t.replace(listOf(snapshot("new", 2, 11))))
        assertEquals(HotspotStatus(), t.replace(emptyList()))
    }
    @Test fun broadcastOffFencesOutOldSnapshotsAndCanTurnOnAgain() {
        val t = HotspotTracker()
        t.notification(snapshot("old", 2))
        t.broadcast(false, 20)
        assertEquals(HotspotStatus(false), t.notification(snapshot("old", 2)))
        assertEquals(HotspotStatus(false), t.replace(listOf(snapshot("old", 2))))
        assertEquals(HotspotStatus(true), t.broadcast(true, 30))
        assertEquals(HotspotStatus(true, 1), t.notification(snapshot("new", 1, 31)))
    }
    @Test fun freshNotificationCanRecoverWhenAnOemMissesTheNextBroadcast() {
        val t = HotspotTracker()
        t.broadcast(false, 20)
        assertEquals(HotspotStatus(true, 1), t.notification(snapshot("new", 1, 30)))
    }
    @Test fun oldCountsAreNotResurrectedAfterANotificationSaysOff() {
        val t = HotspotTracker()
        t.broadcast(true, 1)
        t.notification(snapshot("old", 2))
        assertEquals(HotspotStatus(false), t.notification(HotspotSnapshot("off", HotspotEvidence(false), 20)))
        assertEquals(HotspotStatus(), t.remove("off"))
        assertEquals(HotspotStatus(), t.notification(snapshot("old", 2)))
    }
    @Test fun newestUnknownCountDoesNotReuseAnOldNumber() {
        val t = HotspotTracker()
        t.notification(snapshot("system", 2))
        assertEquals(HotspotStatus(true), t.notification(snapshot("system", null, 11)))
    }
    @Test fun restartingBroadcastSourceKeepsALiveNotificationSnapshot() {
        val t = HotspotTracker()
        t.broadcast(true, 1)
        t.notification(snapshot("system", 2))
        assertEquals(HotspotStatus(true, 2), t.broadcastUnavailable())
    }
    @Test fun outOfOrderSnapshotsDoNotRestoreAnOldClientCount() {
        val t = HotspotTracker()
        t.notification(snapshot("new", 2, 20))
        assertEquals(HotspotStatus(true, 2), t.notification(snapshot("old", 1, 10)))
        assertEquals(HotspotStatus(true, 2), t.replace(listOf(snapshot("new", 2, 20), snapshot("old", 1, 10))))
    }
}
