package com.akira.island.island

import com.akira.island.core.AlarmInfo
import org.junit.Assert.*
import org.junit.Test

class PendingAlarmPresentationTest {
    private val alarm = Act.AlarmA(AlarmInfo(123456L, null, null))
    private val other = Act.InboxA(emptyList())

    @Test fun alarmAloneLeavesTheCapsuleIdleAndUsesTheBubble() {
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(alarm), false, null))
        assertEquals(alarm, secondaryWithPendingAlarm(listOf(alarm), Act.Idle))
    }
    @Test fun explicitAlarmSelectionOpensItsCardUntilCollapse() {
        assertEquals(alarm, primaryWithPendingAlarm(listOf(alarm), true, "alarm"))
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(alarm), false, "alarm"))
        assertEquals(Act.Idle, primaryWithPendingAlarm(listOf(alarm), true, "idle"))
    }
    @Test fun otherActivitiesKeepTheirPriorityAndAreNotDuplicated() {
        assertEquals(other, primaryWithPendingAlarm(listOf(alarm, other), false, null))
        assertEquals(alarm, secondaryWithPendingAlarm(listOf(alarm, other), other))
        assertNull(secondaryWithPendingAlarm(listOf(other), other))
    }
    @Test fun noAlarmPreservesTheExistingFallback() {
        assertEquals(Act.Idle, primaryWithPendingAlarm(emptyList(), false, null))
        assertEquals(other, primaryWithPendingAlarm(listOf(other), false, null))
        assertEquals(other, secondaryWithPendingAlarm(listOf(alarm, other), alarm))
    }
}
