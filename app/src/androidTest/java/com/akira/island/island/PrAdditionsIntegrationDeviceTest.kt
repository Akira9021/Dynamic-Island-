package com.akira.island.island

import android.graphics.Color as AndroidColor
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.ui.MainActivity
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.screens.ColorsScreen
import com.akira.island.update.UpdateChecker
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrAdditionsIntegrationDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var savedSettings = IslandSettings()
    private var savedExtras = ExtraSettings()
    private var savedLocked = false
    private var savedExpanded = false
    private var savedFocus: String? = null
    private val reading = mutableStateOf(BatteryInfo(50))
    private val seed = object : ExternalResource() {
        override fun before() {
            val i = InstrumentationRegistry.getInstrumentation()
            i.runOnMainSync {
                SettingsRepo.init(i.targetContext); ExtrasRepo.init(i.targetContext)
                savedSettings = SettingsRepo.current; savedExtras = ExtrasRepo.state.value
                savedLocked = IslandBus.locked.value
                savedExpanded = IslandBus.expanded.value; savedFocus = IslandBus.focus.value
                SettingsRepo.update { IslandSettings(language = AppLanguage.EN, reduceEffects = true,
                    autoUpdateCheck = false, lastSeenVersion = UpdateChecker.currentVersion(i.targetContext),
                    quickActions = emptyList(), batteryPercentOnly = true, showPlayerBattery = true) }
                ExtrasRepo.update { ExtraSettings() }
                IslandBus.locked.value = false
            }
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.update { savedSettings }; ExtrasRepo.update { savedExtras }
                IslandBus.locked.value = savedLocked
                IslandBus.expanded.value = savedExpanded; IslandBus.focus.value = savedFocus
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun idle() {
        compose.runOnUiThread { compose.activity.setContent {
            IslandTheme {
                val settings by SettingsRepo.state.collectAsState()
                Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 24.dp)) {
                    ExpandedContent(Act.Idle, settings, null, reading.value, 320.dp)
                }
            }
        } }
    }

    @Test fun idleAndBriefIconsKeepActualFillEvenWhenCapsuleUsesPercentOnly() {
        idle()
        for (brief in listOf(false, true)) {
            compose.runOnUiThread { ExtrasRepo.update { copy(briefEnabled = brief) } }
            val tag = if (brief) "expanded-brief-battery-icon" else "expanded-idle-battery-icon"
            val fills = mutableListOf<Int>()
            for (level in listOf(0, 15, 50, 100)) {
                compose.runOnUiThread { reading.value = BatteryInfo(level) }
                compose.onNodeWithTag(tag).assertIsDisplayed()
                compose.onNodeWithText("$level%").assertIsDisplayed()
                val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                var bright = 0
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val pixel = image.getPixel(x, y)
                    if (maxOf(AndroidColor.red(pixel), AndroidColor.green(pixel), AndroidColor.blue(pixel)) * AndroidColor.alpha(pixel) / 255 > 180) bright++
                }
                fills += bright
            }
            assertEquals("Empty battery has no solid fill", 0, fills.first())
            assertTrue("The icon must follow 0/15/50/100, not always look full", fills.zipWithNext().all { (a,b) -> b > a })
            compose.runOnUiThread { reading.value = BatteryInfo(50, charging = true) }
            val charged = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            var green = 0
            for (y in 0 until charged.height) for (x in 0 until charged.width) {
                val p = charged.getPixel(x,y)
                if (AndroidColor.alpha(p) > 180 && AndroidColor.green(p) > 150 && AndroidColor.red(p) < 100) green++
            }
            assertTrue("Charging has a real green fill", green > 0)
            compose.runOnUiThread { reading.value = BatteryInfo(-1) }
            compose.onNodeWithTag(tag).assertDoesNotExist()
            compose.onNodeWithText("100%").assertDoesNotExist()
        }
    }

    @Test fun expandedPlayerKeepsBatteryIconAndTransportWithCapsulePercentOnly() {
        val media = MediaInfo("test", "example.player", "Track", "Artist", null, null, Color.Yellow,
            180_000, 42_000, android.os.SystemClock.elapsedRealtime(), 0f, false, true)
        compose.runOnUiThread { compose.activity.setContent { IslandTheme {
            val settings by SettingsRepo.state.collectAsState()
            Box(Modifier.background(Color.Black)) { ExpandedContent(Act.MediaA(media), settings, null, BatteryInfo(100), 320.dp) }
        } } }
        compose.onNodeWithTag("player-battery").assertIsDisplayed().assertWidthIsAtLeast(45.dp)
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("0:42").assertExists()
    }

    @Test fun disablingFallbackImmediatelyHidesOnlyApproximateTime() {
        compose.runOnUiThread { reading.value = BatteryInfo(50, charging = true, remainingMs = 3_600_000,
            remainingApproximate = true) }
        idle()
        val approximate = hasText("Approximately until full:", substring = true)
        compose.onNode(approximate).assertDoesNotExist()
        compose.runOnUiThread { SettingsRepo.update { copy(adaptiveChargeEta = true) } }
        compose.onNode(approximate).assertIsDisplayed()
        compose.runOnUiThread { SettingsRepo.update { copy(adaptiveChargeEta = false) } }
        compose.onNode(approximate).assertDoesNotExist()
        compose.runOnUiThread { reading.value = reading.value.copy(remainingApproximate = false) }
        compose.onNode(hasText("Until full:", substring = true)).assertIsDisplayed()
    }

    @Test fun alarmShortcutIsOptInHiddenOnLockAndEditorCancelDoesNotChangeTimers() {
        val timer = IslandBus.timer.value
        val alarm = IslandBus.nextAlarm.value
        idle()
        compose.onNodeWithText("New alarm").assertDoesNotExist()
        compose.runOnUiThread { SettingsRepo.update { copy(showAlarmCreator = true) } }
        compose.onNodeWithText("New alarm").assertIsDisplayed()
        compose.runOnUiThread { IslandBus.locked.value = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("New alarm").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { IslandBus.locked.value = false }
        compose.onNodeWithText("New alarm").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("system-alarm-draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("alarm-cancel").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("system-alarm-draft").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(timer, IslandBus.timer.value); assertEquals(alarm, IslandBus.nextAlarm.value) }
    }

    @Test fun paletteCommitsOnlyOnApplyAndKeepsExistingPreviewAndOtherSettings() {
        val original = 0xFF3596D8L
        compose.runOnUiThread {
            SettingsRepo.update { copy(textColorEnabled = true, textColor = original, glassBlurStrength = 72) }
            compose.activity.setContent { IslandTheme { ColorsScreen {} } }
        }
        compose.onAllNodesWithTag("open-hsv-picker").onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("hsv-hue").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.runOnIdle { assertEquals(original, SettingsRepo.current.textColor) }
        compose.onNodeWithTag("hsv-cancel").performClick()
        compose.runOnIdle { assertEquals(original, SettingsRepo.current.textColor) }
        compose.onAllNodesWithTag("open-hsv-picker").onFirst().performClick()
        compose.onNodeWithTag("hsv-hue").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.onNodeWithTag("hsv-saturation").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("hsv-value").performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithTag("hsv-apply").performClick()
        compose.runOnIdle {
            assertEquals(0xFF008000L, SettingsRepo.current.textColor)
            assertEquals(72, SettingsRepo.current.glassBlurStrength)
            assertTrue(SettingsRepo.current.batteryPercentOnly)
        }
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
    }
}
