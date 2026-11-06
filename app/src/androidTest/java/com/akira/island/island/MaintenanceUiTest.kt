package com.akira.island.island

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.ui.MainActivity
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.screens.SizeScreen
import com.akira.island.update.UpdateChecker
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class MaintenanceUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.init(context)
                SettingsRepo.update { IslandSettings(language = AppLanguage.RU, reduceEffects = true, autoUpdateCheck = false,
                    lastSeenVersion = UpdateChecker.currentVersion(context)) }
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
    @After fun cleanup() { compose.runOnUiThread { IslandBus.calibrating.value = false; IslandBus.expanded.value = false } }
    private fun content(block: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent { IslandTheme { block() } } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "maintenance-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun recording(kind: RecordingKind, stop: PendingIntent? = null) = ChronoInfo("test", "example.recorder", "Recording", null,
        System.currentTimeMillis() - 12_000, false, System.currentTimeMillis(), stop, pausedMs = 12_000, recordingKind = kind)

    @Test fun audioScreenAndCameraHaveDifferentPresentationsWithoutAFakeStopButton() {
        for (kind in listOf(RecordingKind.VOICE, RecordingKind.SCREEN, RecordingKind.VIDEO)) {
            content {
                Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                    ExpandedContent(Act.ChronoA(recording(kind)), SettingsRepo.current, null, BatteryInfo(60), 320.dp)
                }
            }
            compose.onNodeWithText(kind.title, substring = true).assertIsDisplayed()
            compose.onNodeWithText("0:12").assertIsDisplayed()
            compose.onNodeWithContentDescription("Открыть приложение").assertIsDisplayed()
            compose.onNodeWithContentDescription("Остановить запись").assertDoesNotExist()
            if (kind != RecordingKind.VOICE) compose.onNodeWithContentDescription(RecordingKind.VOICE.title).assertDoesNotExist()
            compose.onNodeWithContentDescription(kind.title).assertIsDisplayed()
            screenshot("recording-${kind.name.lowercase()}")
        }
    }

    @Test fun actualStopButtonSendsTheProvidedAction() {
        val context = compose.activity
        val called = AtomicBoolean(false)
        val action = "com.akira.island.TEST_STOP_RECORDING"
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, intent: Intent) { called.set(true) } }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val stop = PendingIntent.getBroadcast(context, 303, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
            content { ExpandedContent(Act.ChronoA(recording(RecordingKind.SCREEN, stop)), SettingsRepo.current, null, BatteryInfo(60), 320.dp) }
            compose.onNodeWithContentDescription("Открыть приложение").assertDoesNotExist()
            compose.onNodeWithContentDescription("Остановить запись").performClick()
            compose.waitUntil(10_000) { called.get() }
        } finally { context.unregisterReceiver(receiver) }
    }

    @Test fun idleHidesAfterItsDeadlineAndCameraTapRestoresIt() {
        compose.runOnUiThread { SettingsRepo.update { copy(idleHideSec = 5, hideIdle = false, evLock = false) } }
        var bounds = Rect.Zero
        content { Box(Modifier.fillMaxSize().background(Color(0xFF16151B))) { IslandOverlay(onBodyBounds = { bounds = it }) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("idle-island-wake").fetchSemanticsNodes().isNotEmpty() }
        val target = compose.onNodeWithTag("idle-island-wake").assertIsDisplayed()
        compose.runOnIdle { assertTrue(bounds.width > 0); assertTrue(bounds.width <= 48 * compose.activity.resources.displayMetrics.density + 1) }
        target.performClick()
        compose.onNodeWithTag("idle-island-wake").assertDoesNotExist()
        SystemClock.sleep(1000)
        compose.onNodeWithTag("idle-island-wake").assertDoesNotExist()
        screenshot("idle-restored")
    }

    @Test fun notchTopOffsetIsAvailableAndDoesNotOverwriteCapsulePlacement() {
        compose.runOnUiThread { SettingsRepo.update { copy(notch = true, offsetY = 60) } }
        content { SizeScreen {} }
        compose.onNodeWithText("Отступ сверху").performScrollTo().assertIsDisplayed()
        val trim = SemanticsMatcher("Notch trim slider") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == -10f..30f
        }
        compose.onNode(trim).performSemanticsAction(SemanticsActions.SetProgress) { set -> assertTrue(set(-6f)) }
        compose.runOnIdle { assertEquals(-6, SettingsRepo.current.topOffset); assertEquals(60, SettingsRepo.current.offsetY) }
        screenshot("notch-offset")
    }
}
