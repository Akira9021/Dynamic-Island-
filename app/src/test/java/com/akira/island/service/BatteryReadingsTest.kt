package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class BatteryReadingsTest {
    @Test fun percentSupportsDifferentScalesAndRejectsMissingReadings() {
        assertEquals(80, BatteryReadings.percent(160, 200))
        assertEquals(-1, BatteryReadings.percent(-1, 100))
        assertEquals(-1, BatteryReadings.percent(80, 0))
        assertEquals(100, BatteryReadings.percent(Int.MAX_VALUE, 1))
    }
    @Test fun unknownOrDischargingCurrentDoesNotBecomePower() {
        assertNull(BatteryReadings.currentMa(Int.MIN_VALUE, true))
        assertNull(BatteryReadings.currentMa(-1200000, true))
        assertNull(BatteryReadings.currentMa(1200000, false))
        assertEquals(1200f, BatteryReadings.currentMa(1200000, true)!!, 0.001f)
    }
    @Test fun voltageAndTimeOnlyExposeSupportedValues() {
        assertEquals(4.2f, BatteryReadings.voltageV(4200)!!, 0.001f)
        assertNull(BatteryReadings.voltageV(-1))
        assertNull(BatteryReadings.remainingMs(-1, true))
        assertNull(BatteryReadings.remainingMs(60000, false))
        assertEquals(60000L, BatteryReadings.remainingMs(60000, true))
    }
}
