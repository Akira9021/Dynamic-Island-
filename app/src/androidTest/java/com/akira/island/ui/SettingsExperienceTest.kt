package com.akira.island.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import com.akira.island.core.IslandBus
import com.akira.island.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsExperienceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                SettingsRepo.init(instrumentation.targetContext)
                SettingsRepo.update { IslandSettings(language = AppLanguage.RU, reduceEffects = true, lastSeenVersion = com.akira.island.update.UpdateChecker.currentVersion(InstrumentationRegistry.getInstrumentation().targetContext)) }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)
    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "settings-previews").apply { mkdirs() }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Before fun reset() {
        // Activity launch can finish before its first Compose frame on software emulators.
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.runOnUiThread {
            SettingsRepo.update { IslandSettings(language = AppLanguage.RU, reduceEffects = true, lastSeenVersion = com.akira.island.update.UpdateChecker.currentVersion(InstrumentationRegistry.getInstrumentation().targetContext)) }
            IslandBus.noticeHold.value = false
        }
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Понятно").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Понятно").performClick()
            compose.waitForIdle()
        }
    }
    @After fun cleanup() { compose.runOnUiThread { IslandBus.noticeHold.value = false } }

    private fun openColors() {
        compose.onAllNodesWithText("Оформление").onLast().performClick()
        compose.onNodeWithText("Цвет и рамка").performScrollTo().performClick()
    }

    @Test fun borderChoicesAreExclusiveAndPreviewStaysVisible() {
        openColors()
        compose.onNodeWithText("Своя рамка").performScrollTo().performClick()
        compose.onNodeWithText("По сторонам").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BorderWidths.EDGES, SettingsRepo.current.borderWidths(false)) }
        screenshot("capsule-border-edges")
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.onNodeWithText("Одинаковая толщина").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BorderWidths.UNIFORM, SettingsRepo.current.borderWidths(false)) }
        compose.onNodeWithText("Без рамки").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(SettingsRepo.current.resolveOutline(false).visible) }
    }

    @Test fun previewDoesNotReleaseOrHoldRealNotifications() {
        openColors()
        compose.runOnIdle { IslandBus.noticeHold.value = true }
        compose.onNodeWithText("Карточка").performClick()
        compose.onNodeWithText("Сообщение").performClick()
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").assertIsDisplayed()
        compose.onNodeWithText("Цвет и рамка").assertIsDisplayed()
        compose.runOnIdle { assertTrue(IslandBus.noticeHold.value) }
        compose.onNodeWithTag("appearance-preview").assertHasNoClickAction()
        compose.onAllNodes(hasAnyAncestor(hasTestTag("appearance-preview")) and hasClickAction()).assertCountEquals(0)
        screenshot("expanded-message")
        compose.onNodeWithText("Тень").performScrollTo()
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.runOnIdle { assertTrue(IslandBus.noticeHold.value) }
    }

    @Test fun systemEventsHaveOneHomeAndHardwareReadings() {
        compose.onNodeWithText("Функции").performClick()
        compose.onNodeWithText("Поведение").performScrollTo().performClick()
        compose.onNodeWithText("VPN").assertDoesNotExist()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("События").performScrollTo().performClick()
        compose.onNode(hasText("VPN") and hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подключения").assertExists()
        screenshot("events-connections")
        compose.onNodeWithText("Отладка USB и Wi-Fi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Для разработчиков").assertExists()
        screenshot("events-debugging")
    }
}
