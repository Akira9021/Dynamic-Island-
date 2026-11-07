package com.akira.island.core.system

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the pure logic of the system Clock bridge.
 * These test only framework-free helpers: day normalization and clock app resolution.
 */
class ClockBridgeTest {

    @Test
    fun `alarmDaysFor - null means one-shot alarm`() {
        assertNull(alarmDaysFor(null))
    }

    @Test
    fun `alarmDaysFor - empty mask means one-shot alarm`() {
        assertNull(alarmDaysFor(0))
    }

    @Test
    fun `alarmDaysFor - monday only maps to calendar day 2`() {
        // bit 0 = lunedì → Calendar: lunedì = 2
        assertEquals(listOf(2), alarmDaysFor(1))
    }

    @Test
    fun `alarmDaysFor - sunday only maps to calendar day 1`() {
        // bit 6 = domenica → Calendar: domenica = 1
        assertEquals(listOf(1), alarmDaysFor(64))
    }

    @Test
    fun `alarmDaysFor - full week maps to all seven alarm days`() {
        // bit 0..6 = lunedì..domenica → valori Calendar 2..7 con domenica = 1
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 1), alarmDaysFor(0b1111111))
    }

    @Test
    fun `pickClockPackage - excludes own package`() {
        assertEquals(
            "com.google.android.deskclock",
            pickClockPackage(null, listOf("com.akira.island", "com.google.android.deskclock"), "com.akira.island"),
        )
    }

    @Test
    fun `pickClockPackage - prefers next alarm owner`() {
        assertEquals(
            "com.sec.android.app.clockpackage",
            pickClockPackage("com.sec.android.app.clockpackage", listOf("com.google.android.deskclock"), "com.akira.island"),
        )
    }

    @Test
    fun `pickClockPackage - empty candidates fall back to null`() {
        assertNull(pickClockPackage(null, emptyList(), "com.akira.island"))
    }

    @Test
    fun `isSnoozeLabel - english and russian snooze labels`() {
        assertTrue(isSnoozeLabel("Snooze"))
        assertTrue(isSnoozeLabel("Snooze 10 min"))
        assertTrue(isSnoozeLabel("Отложить"))
        assertTrue(isSnoozeLabel("Postpone"))
        assertTrue(isSnoozeLabel("Delay"))
    }

    @Test
    fun `isSnoozeLabel - dismiss labels are not snooze`() {
        assertFalse(isSnoozeLabel("Dismiss"))
        assertFalse(isSnoozeLabel("Выключить"))
        assertFalse(isSnoozeLabel(null))
    }

    @Test
    fun `isDismissLabel - english and russian dismiss labels`() {
        assertTrue(isDismissLabel("Dismiss"))
        assertTrue(isDismissLabel("Stop"))
        assertTrue(isDismissLabel("Cancel alarm"))
        assertTrue(isDismissLabel("Отклонить"))
        assertTrue(isDismissLabel("Отменить"))
        assertTrue(isDismissLabel("Удалить"))
        assertTrue(isDismissLabel("Остановить"))
        assertTrue(isDismissLabel("Выключить"))
    }

    @Test
    fun `isDismissLabel - snooze labels are not dismiss`() {
        assertFalse(isDismissLabel("Snooze"))
        assertFalse(isDismissLabel("Отложить"))
        assertFalse(isDismissLabel(""))
    }

    @Test
    fun `snoozeDurationMs - ten minutes`() {
        assertEquals(600_000L, snoozeDurationMs())
    }

    // 5 января 2026 — понедельник
    private fun utc(day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(2026, java.util.Calendar.JANUARY, day, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val utcZone = java.util.TimeZone.getTimeZone("UTC")

    @Test
    fun `nextAlarmAt - one-shot later today stays today`() {
        assertEquals(utc(5, 18, 30), nextAlarmAt(18, 30, null, utc(5, 10, 0), utcZone))
    }

    @Test
    fun `nextAlarmAt - one-shot time already passed goes to tomorrow`() {
        assertEquals(utc(6, 7, 30), nextAlarmAt(7, 30, null, utc(5, 10, 0), utcZone))
    }

    @Test
    fun `nextAlarmAt - weekday mask moves to the next matching day`() {
        // понедельник, 07:30 уже прошло → следующий понедельник
        assertEquals(utc(12, 7, 30), nextAlarmAt(7, 30, 1, utc(5, 10, 0), utcZone))
    }

    @Test
    fun `nextAlarmAt - full week mask picks tomorrow`() {
        assertEquals(utc(6, 7, 30), nextAlarmAt(7, 30, 0b1111111, utc(5, 10, 0), utcZone))
    }
}
