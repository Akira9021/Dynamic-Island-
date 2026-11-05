package com.akira.island.island

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
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
import com.akira.island.core.*
import com.akira.island.data.*
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
class FeedbackRegressionTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.init(context)
                SettingsRepo.update { IslandSettings(language = AppLanguage.RU, reduceEffects = true, lastSeenVersion = UpdateChecker.currentVersion(context)) }
                IslandBus.locked.value = false; IslandBus.reply.value = null; IslandBus.noticeHold.value = false
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)
    @After fun cleanup() { compose.runOnUiThread { IslandBus.reply.value = null; IslandBus.locked.value = false; IslandBus.noticeHold.value = false; IslandBus.notice.value = null } }

    private fun content(block: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent { IslandTheme { block() } } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "feedback-previews").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun detailedNotificationKeepsReplyAndReadWithoutThreeUtilityButtons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val action = PendingIntent.getBroadcast(context, 93, Intent("feedback-test-read").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val message = AppMessage("feedback", "example.chat", "Telegram", "Окей, аккуратнее", null, null, null,
            actions = listOf(MsgAction("Ответить", action, listOf(RemoteInput.Builder("reply").build())), MsgAction("Пометить прочитанным", action, emptyList())),
            history = listOf("Я в пути", "Окей, аккуратнее"), sensitive = true)
        val notice = Notice("Анна", icon = Icons.Rounded.ChatBubble, tint = Tints.Blue, style = NoticeStyle.MESSAGE, message = message)
        content {
            Box(Modifier.fillMaxSize().background(Color(0xFF151419)).padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                MessageCard(notice, message, SettingsRepo.current, 338.dp, detail = true)
            }
        }
        listOf("Открыть", "Удалить", "Свернуть").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("Ответить").assertIsDisplayed()
        compose.onNodeWithText("Пометить прочитанным").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                val layouts = mutableListOf<TextLayoutResult>(); assertTrue(get(layouts))
                assertFalse("Read action must have a legible label", layouts.single().isLineEllipsized(0))
            }
        screenshot("notification")
        compose.onNodeWithText("Ответить").performClick()
        compose.runOnIdle { assertEquals(notice.id, IslandBus.reply.value?.noticeId) }
        compose.runOnIdle { IslandBus.locked.value = true }
        compose.onNodeWithText("Содержимое скрыто").assertIsDisplayed()
        compose.onNodeWithText("Окей, аккуратнее").assertDoesNotExist()
        compose.onNodeWithText("Пометить прочитанным").assertDoesNotExist()
    }

    @Test fun playerTimestampsFitNarrowCardsAndLargeSystemText() {
        val settings = IslandSettings(reduceEffects = true, contentScale = 130)
        val media = MediaInfo("feedback", "example.music", "Трек", "Исполнитель", null, null, Tints.Lavender,
            228_000, 11_000, android.os.SystemClock.elapsedRealtime(), 1f, false, true)
        content {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                    ExpandedContent(Act.MediaA(media), settings, null, BatteryInfo(60), 216.dp)
                }
            }
        }
        screenshot("narrow-player")
        listOf("0:11", "-3:37").forEach { time ->
            compose.onNodeWithText(time).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                val result = mutableListOf<TextLayoutResult>(); assertTrue(get(result))
                assertEquals(1, result.single().lineCount)
                val layout = result.single()
                assertFalse("Timestamp $time must not be ellipsized", layout.isLineEllipsized(0))
                // With softWrap=false Compose may reserve a paragraph wider than its glyphs.
                // Check the actual text's intrinsic width, not that unused paragraph area.
                assertTrue("Timestamp $time needs ${layout.multiParagraph.maxIntrinsicWidth}px but has ${layout.size.width}px",
                    layout.multiParagraph.maxIntrinsicWidth <= layout.size.width + 1f)
                assertFalse("Timestamp $time must fit vertically", layout.didOverflowHeight)
            }
        }
    }

    @Test fun idleTimerPickerDoesNotSqueezeItsCounter() {
        content {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.width(200.dp)) { IdleTimerPicker(IslandSettings(timerMinutes = 10)) }
            }
        }
        val count = compose.onNodeWithText("10 мин").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithText("Таймер").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("On a narrow card the start button must be below the counter", start.top >= count.bottom)
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        screenshot("timer-picker")
    }

    @Test fun settingsSearchOpensASectionAndKeepsTheQueryOnBack() {
        compose.waitUntil(timeoutMillis = 60_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithText("Функции").performClick()
        compose.onNodeWithContentDescription("Поиск по настройкам").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("радиус")
        compose.onNodeWithText("Размер и форма").assertIsDisplayed().performClick()
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("радиус")
        screenshot("settings-search")
    }
}
