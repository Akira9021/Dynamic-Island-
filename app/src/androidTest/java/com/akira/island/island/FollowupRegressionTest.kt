package com.akira.island.island

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.service.WeatherInfo
import com.akira.island.service.WeatherRepo
import com.akira.island.ui.MainActivity
import com.akira.island.ui.IslandTheme
import com.akira.island.update.UpdateChecker
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class FollowupRegressionTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.init(context); ExtrasRepo.init(context)
                SettingsRepo.update { IslandSettings(language = AppLanguage.EN, reduceEffects = true,
                    autoUpdateCheck = false, lastSeenVersion = UpdateChecker.currentVersion(context)) }
                ExtrasRepo.update { ExtraSettings() }
                WeatherRepo.state.value = null; WeatherRepo.error.value = null; WeatherRepo.loading.value = false
                IslandBus.media.value = null; IslandBus.chrono.value = emptyMap(); IslandBus.progress.value = emptyMap()
                IslandBus.calls.value = emptyMap(); IslandBus.live.value = emptyMap(); IslandBus.notice.value = null
                IslandBus.timer.value = null; IslandBus.stopwatch.value = null; IslandBus.payReady.value = null
                IslandBus.inbox.value = emptyList(); IslandBus.expanded.value = false; IslandBus.focus.value = null
                IslandBus.calibrating.value = false; IslandBus.keyguard.value = false; IslandBus.locked.value = false
                IslandBus.screenOn.value = true; IslandBus.shadeOpen.value = false; IslandBus.widthPreviewUntil.value = 0
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)
    private fun render(content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent { IslandTheme {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 32.dp), contentAlignment = Alignment.TopCenter) { content() }
        } } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "followup-previews").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun textColor(text: String): Color {
        var color = Color.Unspecified
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
            val results = mutableListOf<TextLayoutResult>(); assertTrue(get(results))
            color = results.single().layoutInput.style.color
        }
        return color
    }
    @After fun cleanup() = compose.runOnUiThread {
        IslandBus.chrono.value = emptyMap(); IslandBus.expanded.value = false; IslandBus.focus.value = null
        ExtrasRepo.update { ExtraSettings() }; WeatherRepo.state.value = null
    }

    @Test fun customTextReachesBothRenderersAndCanBeTurnedOff() {
        val custom = Color(0xFF47D5F0)
        val s = SettingsRepo.current.copy(textColorEnabled = true, textColor = 0xFF47D5F0)
        val notice = Notice("Text sample", icon = Icons.Rounded.Info, tint = Tints.Blue)
        render { CompactContent(Act.NoticeA(notice), s, 338.dp) }
        assertEquals(custom, textColor("Text sample"))
        val media = MediaInfo("text", "test.player", "Test track", "Artist", null, null, Tints.Lavender,
            180_000, 40_000, 0, 1f, false, true)
        render { ExpandedContent(Act.MediaA(media), s, null, BatteryInfo(70), 320.dp) }
        assertEquals(custom, textColor("Test track"))
        assertEquals(custom.copy(alpha = Tints.Gray.red), textColor("Artist"))
        screenshot("custom-text")
        render { ExpandedContent(Act.MediaA(media), s.copy(textColorEnabled = false), null, BatteryInfo(70), 320.dp) }
        assertEquals(Color.White, textColor("Test track"))
    }

    @Test fun weatherAppearsAsItsOwnCardAndInBrief() {
        val reading = WeatherInfo("Moscow", 12.0, 0, System.currentTimeMillis())
        compose.runOnUiThread {
            ExtrasRepo.update { ExtraSettings(weatherEnabled = true, weatherCity = reading.city, latitude = 55.75, longitude = 37.62) }
            WeatherRepo.state.value = reading
        }
        render { IslandOverlay() }
        compose.onNodeWithText("12°").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Moscow").assertIsDisplayed()
        screenshot("weather-card")
        compose.runOnUiThread {
            ExtrasRepo.update { copy(briefEnabled = true) }
            Controls.expand("idle")
        }
        compose.onNodeWithTag("brief-panel").assertIsDisplayed()
        compose.onNodeWithTag("brief-next").performClick()
        compose.onNodeWithTag("brief-next").performClick()
        compose.onNodeWithTag("brief-weather-city").assertTextEquals("Moscow")
        screenshot("weather-brief")
    }

    @Test fun recorderRemainsVisibleWhenClockTimersAreDisabled() {
        val c = ChronoInfo("recorder", "com.google.android.apps.recorder", "Recording", null,
            System.currentTimeMillis() - 12_000, false, System.currentTimeMillis(), pausedMs = 12_000,
            recordingKind = RecordingKind.VOICE)
        compose.runOnUiThread {
            SettingsRepo.update { copy(cardTimer = false, cardRecording = true) }
            IslandBus.chrono.value = mapOf(c.key to c)
        }
        render { IslandOverlay() }
        compose.onNodeWithText("0:12").assertIsDisplayed()
    }

    @Test fun narrowRecorderSendsActualPauseResumeAndStopActions() {
        val context = compose.activity
        val received = AtomicInteger(0)
        val action = "com.akira.island.TEST_RECORDER_CONTROL"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { received.incrementAndGet() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val pending = PendingIntent.getBroadcast(context, 411, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
            val c = ChronoInfo("recorder", "com.google.android.apps.recorder", "Recording", null,
                System.currentTimeMillis() - 12_000, false, System.currentTimeMillis(), stop = pending, pause = pending,
                recordingKind = RecordingKind.VOICE)
            render { ExpandedContent(Act.ChronoA(c), SettingsRepo.current, null, BatteryInfo(70), 216.dp) }
            compose.onNodeWithContentDescription("Pause recording").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { received.get() == 1 }
            assertNull("The island waits for confirmation from the real recorder", c.pausedMs)
            render { ExpandedContent(Act.ChronoA(c.copy(pausedMs = 12_000, resume = pending)), SettingsRepo.current, null, BatteryInfo(70), 216.dp) }
            compose.onNodeWithContentDescription("Resume recording").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { received.get() == 2 }
            compose.onNodeWithContentDescription("Stop recording").performClick()
            compose.waitUntil(10_000) { received.get() == 3 }
            screenshot("recorder-controls")
        } finally { context.unregisterReceiver(receiver) }
    }

    @Test fun liveMoscowWeatherRequest() {
        Assume.assumeTrue("Optional real backend check", InstrumentationRegistry.getArguments().getString("liveWeather") == "true")
        compose.runOnUiThread { ExtrasRepo.update { ExtraSettings(weatherEnabled = true, weatherCity = "Moscow", latitude = 55.75, longitude = 37.62) } }
        val ok = runBlocking { WeatherRepo.refresh() }
        if (!ok) {
            val diagnostic = runCatching {
                val connection = com.akira.island.security.openTrustedHttps(
                    "https://api.open-meteo.com/v1/forecast?latitude=55.75&longitude=37.62&current=temperature_2m,weather_code",
                    7000, 7000, mapOf("User-Agent" to "DynamicIsland/1.7.0"))
                try { "HTTP ${connection.responseCode}" } finally { connection.disconnect() }
            }.fold({ it }, { "${it.javaClass.simpleName}: ${it.message?.take(180)}" })
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("stream", "\nLive weather transport: $diagnostic\n")
            })
        }
        assertTrue(WeatherRepo.error.value, ok)
        assertEquals("Moscow", WeatherRepo.state.value?.city)
        assertNotNull(WeatherRepo.state.value?.temperature)
    }
}
