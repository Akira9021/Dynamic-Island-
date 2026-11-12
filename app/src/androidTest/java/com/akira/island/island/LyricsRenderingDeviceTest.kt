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
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
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

    @Test fun karaokeFrontIsSoftContinuousAndKeepsTheCustomTextHue() {
        val custom = 0xFFFFCF73L
        settings.value = settings.value.copy(textColorEnabled = true, textColor = custom)
        content()
        val first = layout(FIRST)
        assertEquals(FIRST, first.layoutInput.text.text)
        assertTrue("A whole line must not contain per-letter color spans", first.layoutInput.text.spanStyles.isEmpty())
        assertEquals(Color(custom), first.layoutInput.style.color)
        compose.runOnIdle { media.value = media.value.copy(positionMs = 19_000) }
        val full = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        compose.runOnIdle { media.value = media.value.copy(positionMs = 14_250) }
        val middle = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        var softPixels = 0
        for (y in 0 until full.height) for (x in 0 until full.width) {
            if (full[x, y].red > 0.98f && middle[x, y].red in 0.45f..0.9f) softPixels++
        }
        assertTrue("The wave front must contain a soft band inside full glyphs", softPixels > 12)
        compose.runOnIdle { media.value = media.value.copy(positionMs = 14_270) }
        val advanced = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        assertTrue("A 20 ms movement must change pixels without waiting for a whole letter", differences(middle, advanced) > 5)
        val later = layout(FIRST)
        assertEquals(first.layoutInput.text, later.layoutInput.text)
        assertEquals(first.layoutInput.style.color, later.layoutInput.style.color)
        screenshot("smooth-wave-custom-color")
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

    @Test fun rightToLeftLyricsIlluminateFromTheRight() {
        val arabic = "ضوء يتحرك بسلاسة"
        lyrics.value = verses("a", arabic)
        media.value = track("a", 19_000)
        content()
        val paragraph = layout(arabic)
        assertEquals(ResolvedTextDirection.Rtl, paragraph.getBidiRunDirection(0))
        val full = compose.onNodeWithText(arabic).captureToImage().toPixelMap()
        compose.runOnIdle { media.value = media.value.copy(positionMs = 14_250) }
        val middle = compose.onNodeWithText(arabic).captureToImage().toPixelMap()
        val split = (paragraph.getLineLeft(0) + paragraph.getLineRight(0)) / 2f
        var left = 0f; var right = 0f; var leftCount = 0; var rightCount = 0
        for (y in 0 until full.height) for (x in 0 until full.width) if (full[x, y].red > 0.98f) {
            if (x < split) { left += middle[x, y].red; leftCount++ }
            else { right += middle[x, y].red; rightCount++ }
        }
        assertTrue(leftCount > 0 && rightCount > 0)
        assertTrue("RTL wave must have brighter completed glyphs on the right", right / rightCount > left / leftCount + 0.25f)
        screenshot("smooth-wave-rtl")
    }

    @Test fun playingTicksInterpolateBetweenFramesAndPauseFreezesTheWave() {
        val position = mutableStateOf(12_000L)
        val playing = mutableStateOf(true)
        compose.runOnUiThread {
            compose.activity.setContent {
                IslandTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 40.dp)) {
                        Box(Modifier.width(288.dp)) {
                            SmoothKaraokeText(FIRST, position.value, 10_000, 20_000, playing.value, true, settings.value)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { position.value = 12_300 }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
        val during = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(160)
        val after = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        assertTrue(differences(before, during) > 5)
        assertTrue(differences(during, after) > 5)
        compose.runOnIdle { playing.value = false }
        compose.mainClock.advanceTimeByFrame()
        val paused = compose.onNodeWithText(FIRST).captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, differences(paused, compose.onNodeWithText(FIRST).captureToImage().toPixelMap()))
    }

    private fun differences(a: PixelMap, b: PixelMap): Int {
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) count++
        return count
    }

    private companion object {
        const val FIRST = "Плавная волна внутри строки"
        const val SECOND = "Подсветка плавно идёт вслед за музыкой по всей строке"
        const val NEW_TRACK = "У нового трека свой текст"
        fun track(key: String, position: Long) = MediaInfo(key, "example.music", "Проверка строки", "Тестовый исполнитель",
            null, null, Tints.Lavender, 60_000, position, SystemClock.elapsedRealtime(), 1f, false, true)
        fun verses(key: String, first: String = FIRST) = Lyrics(key,
            listOf(LyricLine(10_000, first), LyricLine(20_000, SECOND), LyricLine(40_000, "Последняя строка")))
    }
}
