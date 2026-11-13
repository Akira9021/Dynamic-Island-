package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class HeadsetBatteryTrackerTest {
    @Test fun integerBatteryReadingsSurviveTheExistingRingRenderer() {
        for(level in 0..100) assertEquals(level,(HeadsetBatteryTracker.ring(level)!!*100).toInt())
        assertNull(HeadsetBatteryTracker.ring(null));assertNull(HeadsetBatteryTracker.ring(101))
    }
    @Test fun lateBatteryUpdatesTheExistingNoticeIdentity() {
        val t=HeadsetBatteryTracker();assertNull(t.connect("a",11));t.battery("a",75)
        assertEquals(true to 75,t.levelForNotice(11));assertEquals(false to null,t.levelForNotice(12))
    }
    @Test fun batteryBeforeConnectIsAvailableAndZeroIsValid() {
        val t=HeadsetBatteryTracker();t.battery("a",0);assertEquals(0,t.connect("a",1))
        t.battery("a",100);assertEquals(true to 100,t.levelForNotice(1))
    }
    @Test fun unknownAndInvalidLevelsClearAStaleValue() {
        val t=HeadsetBatteryTracker();t.connect("a",1)
        for(level in listOf(-1,101,Int.MAX_VALUE,-5)) {
            t.battery("a",60);t.battery("a",level);assertEquals(true to null,t.levelForNotice(1))
        }
    }
    @Test fun devicesAreIndependentEvenWhenTheirUiNamesMatch() {
        val t=HeadsetBatteryTracker();t.connect("a",1);t.connect("b",2);t.battery("a",20);t.battery("b",80)
        assertEquals(true to 20,t.levelForNotice(1));assertEquals(true to 80,t.levelForNotice(2))
        t.disconnect("a");assertEquals(true to null,t.levelForNotice(1));assertEquals(true to 80,t.levelForNotice(2))
    }
    @Test fun reconnectDoesNotReviveAnOldQueuedPercentage() {
        val t=HeadsetBatteryTracker();t.battery("a",80);t.connect("a",1);t.disconnect("a");assertNull(t.connect("a",2));t.battery("a",30)
        assertEquals(true to null,t.levelForNotice(1));assertEquals(true to 30,t.levelForNotice(2))
    }
    @Test fun revokeAndStopDropValuesWithoutInventingZero() {
        val t=HeadsetBatteryTracker();t.connect("a",1);t.battery("a",70);t.invalidateAll()
        assertEquals(true to null,t.levelForNotice(1));t.clear();assertEquals(false to null,t.levelForNotice(1))
    }
    @Test fun cacheIsBounded() {
        val t=HeadsetBatteryTracker()
        repeat(150){t.connect("device$it",it.toLong());t.battery("device$it",50)}
        assertEquals(false to null,t.levelForNotice(0));assertEquals(true to 50,t.levelForNotice(149))
    }
}
