package com.akira.island.island

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.MainActivity
import com.akira.island.update.UpdateChecker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LyricsRenderingDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val settings = mutableStateOf(IslandSettings(lyricsEnabled = true, liquidGlass = false))
    private val media = mutableStateOf(track("a", 12_000))
    private val lyrics = mutableStateOf(verses("a"))
    private lateinit var savedSettings: IslandSettings
    private val seed = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.init(context)
                savedSettings = SettingsRepo.current
                SettingsRepo.update { IslandSettings(language = AppLanguage.RU, autoUpdateCheck = false,
                    lastSeenVersion = UpdateChecker.currentVersion(context)) }
            }
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { SettingsRepo.update { savedSettings } }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun content() {
        compose.runOnUiThread {
            compose.activity.setContent {
                IslandTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF151419)).padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.width(288.dp).testTag("lyrics-card").background(Color.Black)) {
                            ExpandedContent(Act.MediaA(media.value), settings.value, lyrics.value, BatteryInfo(60), 288.dp)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        return results.single()
    }

    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "lyrics-verification").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("lyrics-card").captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun fullLineHasOneCustomColorThroughoutItsTimestampInterval() {
        val custom = 0xFFFFCF73L
        settings.value = settings.value.copy(textColorEnabled = true, textColor = custom)
        content()
        val first = layout(FIRST)
        assertEquals(FIRST, first.layoutInput.text.text)
        assertTrue("A whole line must not contain per-letter color spans", first.layoutInput.text.spanStyles.isEmpty())
        assertEquals(Color(custom), first.layoutInput.style.color)
        compose.runOnIdle { media.value = media.value.copy(positionMs = 18_000) }
        val later = layout(FIRST)
        assertEquals(first.layoutInput.text, later.layoutInput.text)
        assertEquals(first.layoutInput.style.color, later.layoutInput.style.color)
        screenshot("whole-line-custom-color")
    }

    @Test fun seekCrossfadesWholeLinesAndKeepsCardHeightWithinItsEndpoints() {
        content()
        val initialHeight = compose.onNodeWithTag("lyrics-card").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { media.value = media.value.copy(positionMs = 22_000) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithText(FIRST, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(SECOND, useUnmergedTree = true).assertExists()
        val intermediateHeight = compose.onNodeWithTag("lyrics-card").fetchSemanticsNode().boundsInRoot.height
        screenshot("line-transition-middle")
        compose.mainClock.advanceTimeBy(400)
        val last = layout(SECOND)
        assertEquals("This fixture must exercise a two-line verse", 2, last.lineCount)
        assertTrue(last.layoutInput.text.spanStyles.isEmpty())
        val finalHeight = compose.onNodeWithTag("lyrics-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue(intermediateHeight >= minOf(initialHeight, finalHeight) - 2)
        assertTrue(intermediateHeight <= maxOf(initialHeight, finalHeight) + 2)
        compose.onNodeWithText(FIRST, useUnmergedTree = true).assertDoesNotExist()
        screenshot("line-transition-finished")
    }

    @Test fun changingTracksAtTheSameLineIndexPreservesTheOutgoingSnapshot() {
        content()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { media.value = track("b", 12_000); lyrics.value = verses("b", NEW_TRACK) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText(FIRST, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(NEW_TRACK, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(400)
        layout(NEW_TRACK)
        compose.onNodeWithText(FIRST, useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { media.value = media.value.copy(positionMs = 0) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        layout("• • •")
        compose.onNodeWithText(NEW_TRACK, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun reducedEffectsSwitchesLinesImmediatelyAndPlainLyricsStayScrollable() {
        settings.value = settings.value.copy(reduceEffects = true)
        content()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { media.value = media.value.copy(positionMs = 22_000) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        layout(SECOND)
        compose.onNodeWithText(FIRST, useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { lyrics.value = Lyrics("a", List(20) { LyricLine(0, "Обычный текст, строка ${it + 1}") }, synced = false) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Текст без синхронизации").assertIsDisplayed()
        val plain = (1..20).joinToString("\n") { "Обычный текст, строка $it" }
        compose.onNodeWithText(plain).assertExists().performTouchInput { swipeUp() }
        assertTrue(layout(plain).layoutInput.text.spanStyles.isEmpty())
        screenshot("plain-text-fallback")
    }

    private companion object {
        const val FIRST = "Строка появляется целиком"
        const val SECOND = "Следующая строка плавно сменяет предыдущую целиком"
        const val NEW_TRACK = "У нового трека свой текст"
        fun track(key: String, position: Long) = MediaInfo(key, "example.music", "Проверка строки", "Тестовый исполнитель",
            null, null, Tints.Lavender, 60_000, position, SystemClock.elapsedRealtime(), 1f, false, true)
        fun verses(key: String, first: String = FIRST) = Lyrics(key,
            listOf(LyricLine(10_000, first), LyricLine(20_000, SECOND), LyricLine(40_000, "Последняя строка")))
    }
}
