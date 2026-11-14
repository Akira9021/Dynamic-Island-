package com.akira.island.core.system

import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Inspect synthetic intents only; never launch Clock or create an actual alarm during a test. */
@RunWith(AndroidJUnit4::class)
class AlarmCreationIntentDeviceTest {
    @Test fun clockReceivesExactTimeAndConfirmationIsNotSkipped() {
        val intent = alarmCreationIntent(AlarmCreationRequest(6, 25, setOf(AlarmWeekday.MONDAY), "  Work  "))
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(6, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(25, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals(listOf(Calendar.MONDAY), intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS))
        assertEquals("Work", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }
    @Test fun onceWithoutLabelDoesNotIntroduceRepeatDaysOrEmptyLabels() {
        val intent = alarmCreationIntent(AlarmCreationRequest(0, 0))
        assertFalse(intent.hasExtra(AlarmClock.EXTRA_DAYS))
        assertFalse(intent.hasExtra(AlarmClock.EXTRA_MESSAGE))
    }
}
