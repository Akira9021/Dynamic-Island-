package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class SystemEventObservationTest {
    @Test fun initialAndUnavailableSnapshotsNeverGenerateChanges() {
        assertFalse(eventChanged(null, true))
        assertFalse(eventChanged(false, null))
        assertFalse(eventChanged(true, false, initial = true))
    }
    @Test fun everyRealTransitionWorksInBothDirectionsWithoutRepeats() {
        assertTrue(eventChanged(false, true))
        assertTrue(eventChanged(true, false))
        assertFalse(eventChanged(true, true))
        assertFalse(eventChanged(false, false))
    }
    @Test fun unsupportedReadingsStayUnknownRatherThanOff() {
        assertNull(SystemEventReadings(observing = true).hotspot)
        assertNull(SystemEventReadings().wirelessDebug)
    }
}
