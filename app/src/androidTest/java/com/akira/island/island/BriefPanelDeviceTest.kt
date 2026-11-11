package com.akira.island.island

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.AlarmInfo
import com.akira.island.core.BatteryInfo
import com.akira.island.core.IslandBus
import com.akira.island.data.*
import com.akira.island.service.StepRepo
import com.akira.island.service.WeatherInfo
import com.akira.island.service.WeatherRepo
import com.akira.island.ui.MainActivity
import com.akira.island.ui.IslandTheme
import com.akira.island.update.UpdateChecker
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BriefPanelDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var oldExtras = ExtraSettings()
    private var oldSettings = IslandSettings()
    private var oldWeather: WeatherInfo? = null
    private var oldSteps: Long? = null
    private var oldAlarm: AlarmInfo? = null
    private var oldLocked = false
    private var oldWeatherError: String? = null
    private var oldWeatherLoading = false
    private val seed = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.init(context); ExtrasRepo.init(context)
                oldSettings = SettingsRepo.current; oldExtras = ExtrasRepo.state.value
                oldSteps = StepRepo.count.value; oldWeather = WeatherRepo.state.value
                oldWeatherError = WeatherRepo.error.value; oldWeatherLoading = WeatherRepo.loading.value
                oldAlarm = IslandBus.nextAlarm.value; oldLocked = IslandBus.locked.value
                SettingsRepo.update { IslandSettings(language = AppLanguage.EN, reduceEffects = true, autoUpdateCheck = false,
                    lastSeenVersion = UpdateChecker.currentVersion(context), quickActions = emptyList()) }
                ExtrasRepo.update { ExtraSettings(briefEnabled = true, steps = true, weatherEnabled = true,
                    weatherCity = "Private test city", latitude = 50.0, longitude = 30.0) }
                StepRepo.count.value = 4_567
                WeatherRepo.state.value = WeatherInfo("Private test city", 12.0, 0, System.currentTimeMillis())
                WeatherRepo.error.value = null; WeatherRepo.loading.value = false
                IslandBus.locked.value = false
                IslandBus.nextAlarm.value = AlarmInfo(System.currentTimeMillis() + 3_600_000, null, null)
            }
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ExtrasRepo.update { oldExtras }; SettingsRepo.update { oldSettings }
                StepRepo.count.value = oldSteps; WeatherRepo.state.value = oldWeather
                WeatherRepo.error.value = oldWeatherError; WeatherRepo.loading.value = oldWeatherLoading
                IslandBus.nextAlarm.value = oldAlarm; IslandBus.locked.value = oldLocked
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun content(width: Int = 320, scale: Int = 100, fontScale: Float = 1f, actions: List<QuickAction> = emptyList()) {
        compose.runOnUiThread {
            compose.activity.setContent {
                IslandTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                            ExpandedContent(Act.Idle, SettingsRepo.current.copy(contentScale = scale, quickActions = actions), null, BatteryInfo(62), width.dp)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "brief-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertTextFits(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(get(results))
            val layout = results.single()
            assertEquals(1, layout.lineCount)
            assertFalse(layout.isLineEllipsized(0))
            assertFalse(layout.didOverflowHeight)
            assertTrue(layout.multiParagraph.maxIntrinsicWidth <= layout.size.width + 1f)
        }
    }

    @Test fun narrowAndScaledClockPagesKeepTimeBatteryAndAlarmInsideTheCard() {
        for (style in BriefClockStyle.entries) {
            compose.runOnUiThread { ExtrasRepo.update { copy(briefClockStyle = style) } }
            content(width = 216, scale = 130, fontScale = 1.3f)
            compose.onNodeWithTag("brief-previous").assertIsNotEnabled()
            compose.onNodeWithTag("brief-clock").performScrollTo().assertIsDisplayed()
            if (style != BriefClockStyle.SPLIT) {
                assertTextFits(compose.onNode(hasAnyAncestor(hasTestTag("brief-clock")) and
                    SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true))
            }
            compose.onNodeWithTag("brief-alarm").performScrollTo().assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithTag("brief-battery").assertIsDisplayed()
            val page = compose.onNodeWithTag("brief-page-0").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("brief-battery", "brief-alarm")) {
                val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag must fit the page width", bounds.left >= page.left - 1 && bounds.right <= page.right + 1)
                assertTrue("$tag must fit the finite page height", bounds.top >= page.top - 1 && bounds.bottom <= page.bottom + 1)
            }
            screenshot("narrow-${style.name.lowercase()}")
        }
    }

    @Test fun pagerSupportsSwipesAndButtonsAndLockImmediatelyRemovesPrivateContentAndActions() {
        compose.runOnUiThread {
            ExtrasRepo.update { copy(controls = true, contacts = listOf(ContactShortcut("Private person", "content://com.android.contacts/contacts/1"))) }
        }
        content(actions = listOf(QuickAction.FLASHLIGHT))
        screenshot("standard-clock")
        compose.onNodeWithTag("brief-pages").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("brief-step-count").assertIsDisplayed()
        compose.onNodeWithText("4,567").assertIsDisplayed()
        compose.onNodeWithTag("brief-next").performClick()
        compose.onNodeWithTag("brief-next").assertIsNotEnabled()
        compose.onNodeWithTag("brief-weather-city").assertTextEquals("Private test city")
        compose.runOnIdle { IslandBus.locked.value = true }
        compose.onNodeWithTag("brief-weather-city").assertDoesNotExist()
        compose.onNodeWithTag("brief-step-count").assertDoesNotExist()
        compose.onNodeWithText("Private person").assertDoesNotExist()
        compose.onNodeWithText("Media volume").assertDoesNotExist()
        compose.onNodeWithText("Flashlight").assertDoesNotExist()
        compose.onNode(hasText("Unlock your phone to view") and hasAnyAncestor(hasTestTag("brief-page-2"))).assertIsDisplayed()
        compose.onNodeWithTag("brief-previous").performClick()
        compose.onNodeWithTag("brief-step-count").assertDoesNotExist()
        compose.onNodeWithTag("brief-previous").performClick()
        compose.onNodeWithTag("brief-clock").assertIsDisplayed()
        compose.onNodeWithTag("brief-alarm").assertHasNoClickAction()
        screenshot("locked-clock")
        compose.runOnIdle { IslandBus.locked.value = false }
        compose.onNodeWithTag("brief-alarm").assertHasClickAction()
        compose.onNodeWithTag("brief-next").performClick()
        compose.onNodeWithTag("brief-step-count").assertIsDisplayed()
    }

    @Test fun optingOutRestoresTheExistingHeaderAndSingleStepsRow() {
        compose.runOnUiThread { ExtrasRepo.update { copy(briefEnabled = false) } }
        content()
        compose.onNodeWithTag("brief-panel").assertDoesNotExist()
        compose.onNodeWithText("Steps today: 4567").assertIsDisplayed()
        compose.onAllNodesWithText("62%").assertCountEquals(1)
        compose.runOnUiThread { ExtrasRepo.update { copy(briefEnabled = true) } }
        compose.onNodeWithTag("brief-panel").assertIsDisplayed()
        compose.onNodeWithText("Steps today: 4567").assertDoesNotExist()
        compose.onAllNodesWithText("62%").assertCountEquals(1)
    }

    @Test fun ordinaryDigitalAndStepsPagesWrapTheirContentInsteadOfReserving240Dp() {
        compose.runOnUiThread { IslandBus.nextAlarm.value = null }
        content()
        val density = compose.activity.resources.displayMetrics.density
        var bounds = compose.onNodeWithTag("brief-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Default digital Brief should fit within 190dp, was ${bounds.height / density}", bounds.height <= 190 * density)
        compose.onNodeWithTag("brief-clock").assertIsDisplayed()
        compose.onNodeWithTag("brief-battery").assertIsDisplayed()
        screenshot("compact-clock")
        compose.onNodeWithTag("brief-next").performClick()
        bounds = compose.onNodeWithTag("brief-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Steps should not reserve blank vertical space", bounds.height <= 190 * density)
        compose.onNodeWithTag("brief-step-count").assertIsDisplayed()
        screenshot("compact-steps")
    }

    @Test fun weatherDistinguishesLoadingAndErrorsAndHidesRefreshWhileLocked() {
        compose.runOnUiThread {
            WeatherRepo.state.value = null
            WeatherRepo.error.value = "Weather update failed. Check your connection."
        }
        content()
        compose.onNodeWithTag("brief-next").performClick()
        compose.onNodeWithTag("brief-next").performClick()
        compose.onNodeWithText("Weather update failed. Check your connection.").assertIsDisplayed()
        compose.onNodeWithTag("brief-weather-refresh").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithTag("brief-weather-city").assertTextEquals("Private test city")
        screenshot("weather-error")
        compose.runOnIdle { WeatherRepo.error.value = null; WeatherRepo.loading.value = true }
        compose.onNodeWithText("Loading weather…").assertIsDisplayed()
        compose.onNodeWithTag("brief-weather-refresh").assertIsNotEnabled()
        compose.runOnIdle { IslandBus.locked.value = true }
        compose.onNodeWithTag("brief-weather-city").assertDoesNotExist()
        compose.onNodeWithTag("brief-weather-refresh").assertDoesNotExist()
        compose.onNodeWithText("Loading weather…").assertDoesNotExist()
    }
}
