package com.akira.island.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HsvColorPickerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun slidersStayLocalUntilConfirmedAndPreserveAlpha() {
        var chosen: Long? = null
        compose.runOnUiThread { compose.activity.setContent {
            IslandTheme { HsvColorPickerDialog(0x40FF0000L, {}, { chosen = it }) }
        } }
        compose.onNodeWithTag("hsv-hue").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.onNodeWithTag("hsv-saturation").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("hsv-value").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertNull(chosen) }
        compose.onNodeWithTag("hsv-apply").performClick()
        compose.runOnIdle { assertEquals(0x4000FF00L, chosen) }
    }

    @Test fun cancelDiscardsSliderChanges() {
        var confirmed = false
        var dismissed = false
        compose.runOnUiThread { compose.activity.setContent {
            IslandTheme {
                var visible by remember { mutableStateOf(true) }
                if (visible) HsvColorPickerDialog(0xFF000000L,
                    { dismissed = true; visible = false }, { confirmed = true })
            }
        } }
        compose.onNodeWithTag("hsv-value").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("hsv-cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(confirmed) }
        compose.onNodeWithTag("hsv-color-picker").assertDoesNotExist()
    }
}
