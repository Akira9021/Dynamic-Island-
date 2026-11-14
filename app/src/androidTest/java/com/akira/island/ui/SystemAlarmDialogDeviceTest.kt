package com.akira.island.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akira.island.core.system.AlarmCreationRequest
import com.akira.island.core.system.AlarmWeekday
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemAlarmDialogDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectedDaysAndLabelAreOnlyForwardedWhenConfirmed() {
        var request: AlarmCreationRequest? = null
        compose.runOnUiThread { compose.activity.setContent { IslandTheme {
            SystemAlarmDialog({}, { request = it }, initialHour = 6, initialMinute = 25)
        } } }
        compose.onNodeWithTag("alarm-day-0").performClick()
        compose.onNodeWithTag("alarm-day-4").performClick()
        compose.onNodeWithTag("alarm-label").performTextInput("Work")
        compose.runOnIdle { assertNull(request) }
        compose.onNodeWithTag("alarm-open-clock").performClick()
        compose.runOnIdle {
            assertEquals(AlarmCreationRequest(6, 25, setOf(AlarmWeekday.MONDAY, AlarmWeekday.FRIDAY), "Work"), request)
        }
    }
}
