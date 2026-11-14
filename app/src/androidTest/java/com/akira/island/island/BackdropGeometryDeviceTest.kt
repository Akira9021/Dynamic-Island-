package com.akira.island.island

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.BatteryInfo
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
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

    @Test fun springFramesReportTheirVisibleContourWithoutDisablingBlur() {
        val expanded = mutableStateOf(false)
        val latest = java.util.concurrent.atomic.AtomicReference<BackdropGeometry?>()
        var sample: (() -> Unit)? = null
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { compose.activity.setContent {
                IslandTheme { Box(Modifier.width(340.dp)) {
                    IslandBody(Act.Idle, expanded.value,
                        IslandSettings(liquidGlass=true, reduceEffects=false, autoUpdateCheck=false,
                            cornerRadius=20, expandedCornerRadius=20,
                            expandStyle=com.akira.island.data.ExpandStyle.FRUIT,
                            elasticity=com.akira.island.data.Elasticity.HIGH),
                        null, BatteryInfo(62), false, {}, onBackdropShape={ latest.set(it) }, onBackdropSampler={ sample=it })
                } }
            } }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onRoot().captureToImage()
            val compact=requireNotNull(latest.get())
            compose.runOnIdle { expanded.value=true }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(64)
            compose.onRoot().captureToImage()
            compose.runOnIdle { requireNotNull(sample).invoke() }
            val spring=requireNotNull(latest.get())
            assertTrue("The material remains eligible during the spring",spring.usable)
            assertTrue(spring.area.width>compact.area.width)
            assertTrue(spring.bottomRadius>0f)
        } finally { compose.mainClock.autoAdvance=true }
    }

    @Test fun geometryUsesTheActualCornerAndStopsReportingWhenDisabled() {
        val saved = SettingsRepo.current
        val s = mutableStateOf(IslandSettings(liquidGlass = true, backdropBlur = false, expandedCornerRadius = 28, reduceEffects = true))
        val expanded = mutableStateOf(true)
        val activity = mutableStateOf<Act>(Act.Idle)
        var card = false
        var radius = 0f; var eligible = false
        val reports = AtomicInteger()
        try {
            compose.runOnUiThread { compose.activity.setContent {
                IslandTheme { Box(Modifier.width(300.dp)) {
                    IslandBody(activity.value, expanded.value, s.value, null, BatteryInfo(62), false, {},
                        onBackdropShape = { g -> card = g.card; radius = g.bottomRadius; eligible = g.usable; reports.incrementAndGet() })
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
            compose.runOnIdle { assertTrue("Notch remains eligible", eligible) }
            compose.runOnIdle { expanded.value = false; s.value = s.value.copy(cornerRadius = 14) }
            compose.waitForIdle(); compose.onRoot().captureToImage()
            compose.runOnIdle { assertTrue(eligible); assertFalse(card); assertEquals(14*density, radius, 0.1f) }
            for (style in listOf(NoticeStyle.WIDE, NoticeStyle.MESSAGE, NoticeStyle.PILL, NoticeStyle.ALARM)) {
                compose.runOnIdle { activity.value = Act.NoticeA(Notice("Example", icon=Icons.Rounded.Alarm, tint=Tints.Orange, style=style)) }
                compose.waitForIdle(); compose.onRoot().captureToImage()
                compose.runOnIdle { assertTrue("Notification/event $style", eligible) }
            }
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
