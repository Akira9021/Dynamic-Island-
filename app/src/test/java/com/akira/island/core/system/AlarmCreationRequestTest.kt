package com.akira.island.core.system

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AlarmCreationRequestTest {
    @Test fun onceAndRepeatingAlarmHaveDifferentDayLists() {
        assertTrue(AlarmCreationRequest(6, 30).calendarDays.isEmpty())
        val request = AlarmCreationRequest(7, 15, setOf(AlarmWeekday.SUNDAY, AlarmWeekday.MONDAY, AlarmWeekday.FRIDAY))
        assertEquals(listOf(Calendar.MONDAY, Calendar.FRIDAY, Calendar.SUNDAY), request.calendarDays)
    }

    @Test fun labelsAreTrimmedAndBoundedWithoutChangingTheTime() {
        val request = AlarmCreationRequest(23, 59, label = "  " + "a".repeat(100) + "  ")
        assertEquals("a".repeat(80), request.cleanLabel)
        assertEquals(23, request.hour); assertEquals(59, request.minute)
        assertEquals("", AlarmCreationRequest(0, 0, label = "  ").cleanLabel)
    }

    @Test fun invalidTimeIsRejectedInsteadOfSilentlySchedulingAnotherAlarm() {
        for ((hour, minute) in listOf(-1 to 0, 24 to 0, 0 to -1, 0 to 60)) {
            try { AlarmCreationRequest(hour, minute); fail("Invalid time must not be dispatched") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
