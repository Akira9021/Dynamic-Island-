package com.akira.island.island

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.BatteryInfo
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.MainActivity
import com.akira.island.ui.IslandTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BackdropGeometryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun geometryUsesTheActualCornerAndStopsReportingWhenDisabled() {
        val saved = SettingsRepo.current
        val s = mutableStateOf(IslandSettings(liquidGlass = true, backdropBlur = false, expandedCornerRadius = 28, reduceEffects = true))
        var radius = 0f; var eligible = false
        val reports = AtomicInteger()
        try {
            compose.runOnUiThread { compose.activity.setContent {
                IslandTheme { Box(Modifier.width(300.dp)) {
                    IslandBody(Act.Idle, true, s.value, null, BatteryInfo(62), false, {},
                        onBackdropShape = { r, ok -> radius = r; eligible = ok; reports.incrementAndGet() })
                } }
            } }
            compose.waitForIdle()
            compose.onRoot().captureToImage()
            compose.waitUntil(10000) { reports.get() > 0 }
            val density = compose.activity.resources.displayMetrics.density
            compose.runOnIdle { assertEquals(28 * density, radius, 0.1f); assertTrue(eligible) }
            compose.runOnIdle { s.value = s.value.copy(expandedCornerRadius = 12) }
            compose.waitForIdle()
            compose.onRoot().captureToImage()
            compose.waitUntil(10000) { kotlin.math.abs(radius - 12 * density) < 0.1f }
            compose.runOnIdle { s.value = s.value.copy(notch = true) }
            compose.waitForIdle()
            compose.onRoot().captureToImage()
            compose.waitUntil(10000) { !eligible }
            compose.runOnIdle { s.value = s.value.copy(liquidGlass = false) }
            compose.waitForIdle()
            val count = reports.get()
            compose.runOnIdle { s.value = s.value.copy(expandedCornerRadius = 36) }
            compose.waitForIdle()
            assertEquals(count, reports.get())
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { SettingsRepo.update { saved } }
        }
    }
}
