package com.akira.island.island

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.MainActivity
import com.akira.island.ui.screens.NotificationsScreen
import com.akira.island.update.UpdateChecker
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLayoutDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            instrumentation.runOnMainSync {
                SettingsRepo.init(context)
                SettingsRepo.update {
                    IslandSettings(language = AppLanguage.RU, reduceEffects = true, autoUpdateCheck = false,
                        lastSeenVersion = UpdateChecker.currentVersion(context))
                }
                IslandBus.locked.value = false
                IslandBus.reply.value = null
                IslandBus.inbox.value = emptyList()
                IslandBus.messageDetail.value = false
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    @After fun cleanup() {
        compose.runOnUiThread {
            IslandBus.locked.value = false
            IslandBus.reply.value = null
            IslandBus.inbox.value = emptyList()
            IslandBus.noticeHold.value = false
            IslandBus.messageDetail.value = false
        }
    }

    private fun image(color: Int) = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }.asImageBitmap()

    private fun message() = AppMessage("current", "example.chat", "Chat", "Private message", image(android.graphics.Color.RED),
        image(android.graphics.Color.BLUE), null, history = List(8) { "Line $it" }, sensitive = true)

    private fun notice(m: AppMessage) = Notice("Private sender", icon = Icons.Rounded.ChatBubble, tint = Tints.Blue,
        style = NoticeStyle.MESSAGE, message = m)

    private fun content(s: IslandSettings, n: Notice) {
        compose.runOnUiThread {
            SettingsRepo.update { s }
            compose.activity.setContent {
                IslandTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.testTag("notification-under-test")) { CompactContent(Act.NoticeA(n), s, 338.dp) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun centeredAvatarStaysBelowTheCameraAndHonorsAvatarAppAndNone() {
        val m = message()
        for (style in listOf(MessageStyle.CARD, MessageStyle.PILL)) {
            for (mode in MessageIconMode.entries) {
                val s = SettingsRepo.current.copy(messageStyle = style, height = 44, cameraGap = 80,
                    messageIconMode = mode, messageIconPosition = MessageIconPosition.CENTER)
                content(s, notice(m))
                val icon = compose.onNodeWithContentDescription("Значок уведомления")
                if (mode == MessageIconMode.NONE) {
                    icon.assertDoesNotExist()
                } else {
                    val iconBounds = icon.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val card = compose.onNodeWithTag("notification-under-test").fetchSemanticsNode().boundsInRoot
                    val density = compose.activity.resources.displayMetrics.density
                    assertEquals("The avatar must be horizontally centered", card.center.x, iconBounds.center.x, 1f)
                    assertTrue("The avatar must be below the camera band", iconBounds.top >= card.top + s.height * density)
                    val bitmap = icon.captureToImage().asAndroidBitmap()
                    val expectedColor = if (mode == MessageIconMode.APP) android.graphics.Color.BLUE else android.graphics.Color.RED
                    assertEquals(expectedColor, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                }
            }
        }
    }

    @Test fun centeredAvatarKeepsSensitiveContentPrivateOnTheLockScreen() {
        val m = message()
        compose.runOnUiThread { IslandBus.locked.value = true }
        for (style in listOf(MessageStyle.CARD, MessageStyle.PILL)) {
            content(SettingsRepo.current.copy(messageStyle = style, messageIconPosition = MessageIconPosition.CENTER), notice(m))
            compose.onNodeWithText("Содержимое скрыто").assertIsDisplayed()
            compose.onNodeWithText(m.text).assertDoesNotExist()
            compose.onNodeWithText("Private sender").assertDoesNotExist()
            val bitmap = compose.onNodeWithContentDescription("Значок уведомления").captureToImage().asAndroidBitmap()
            assertEquals("Sensitive avatars must use the app icon", android.graphics.Color.BLUE,
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        }
    }

    @Test fun positionChoiceReplacesTheOldRightToggleAndPreservesIconMode() {
        compose.runOnUiThread {
            SettingsRepo.update { copy(messageIconRight = true, messageIconMode = MessageIconMode.APP) }
            compose.activity.setContent { IslandTheme { NotificationsScreen({}, {}) } }
        }
        compose.onNodeWithText("Значок справа").assertDoesNotExist()
        compose.onNodeWithText("Положение значка").performScrollTo().performClick()
        compose.onNodeWithText("Справа").assertIsDisplayed()
        compose.onNodeWithText("По центру под камерой").performClick()
        compose.runOnIdle {
            assertEquals(MessageIconPosition.CENTER, SettingsRepo.current.notificationIconPosition)
            assertFalse(SettingsRepo.current.messageIconRight)
            assertEquals(MessageIconMode.APP, SettingsRepo.current.messageIconMode)
        }
    }

    @Test fun compactCountDeduplicatesHistoryAndKeepsTextOutsideTheCameraGap() {
        val m = message()
        fun retained(key: String) = InboxItem(key, m.pkg, m.appName, "Sender", "Body", null, null, null, 0L, false)
        compose.runOnUiThread {
            IslandBus.inbox.value = listOf(retained("other"), retained(m.key), retained("third"), retained("other"))
        }
        val s = SettingsRepo.current.copy(messageStyle = MessageStyle.ICON, width = 160, cameraGap = 80)
        content(s, notice(m))
        compose.onNodeWithText(m.text).assertDoesNotExist()
        compose.onNodeWithText("Private sender").assertDoesNotExist()
        compose.onNodeWithText("8").assertDoesNotExist()
        val count = compose.onNodeWithText("3").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val island = compose.onNodeWithTag("notification-under-test").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Count must stay to the right of the camera", count.left >= island.center.x + s.cameraGap * density / 2 - 1f)
        compose.runOnUiThread { IslandBus.inbox.value = listOf(retained(m.key)) }
        compose.onNodeWithText("1").assertIsDisplayed()
    }
}
