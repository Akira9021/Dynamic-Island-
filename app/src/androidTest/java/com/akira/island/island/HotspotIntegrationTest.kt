package com.akira.island.island

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.service.HotspotMonitor
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.MainActivity
import com.akira.island.update.UpdateChecker
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Controlled Notification/Compose fixtures; these are not real tethering hardware tests. */
@RunWith(AndroidJUnit4::class)
class HotspotIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var previousSettings = IslandSettings()
    private var previousExtras = ExtraSettings()
    private val seed = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                val context = instrumentation.targetContext
                SettingsRepo.init(context); ExtrasRepo.init(context)
                previousSettings = SettingsRepo.current; previousExtras = ExtrasRepo.state.value
                SettingsRepo.update { IslandSettings(language = AppLanguage.EN, reduceEffects = true, evHotspot = true,
                    autoUpdateCheck = false, lastSeenVersion = UpdateChecker.currentVersion(context)) }
                ExtrasRepo.update { ExtraSettings() }
                IslandBus.media.value = null; IslandBus.calls.value = emptyMap(); IslandBus.notice.value = null
                IslandBus.timer.value = null; IslandBus.stopwatch.value = null; IslandBus.chrono.value = emptyMap()
                IslandBus.progress.value = emptyMap(); IslandBus.live.value = emptyMap(); IslandBus.payReady.value = null
                IslandBus.inbox.value = emptyList(); IslandBus.nextAlarm.value = null
                IslandBus.expanded.value = false; IslandBus.focus.value = null; IslandBus.calibrating.value = false
                IslandBus.keyguard.value = false; IslandBus.locked.value = false; IslandBus.screenOn.value = true
                IslandBus.shadeOpen.value = false; IslandBus.widthPreviewUntil.value = 0
                IslandBus.battery.value = BatteryInfo(70)
                HotspotMonitor.notificationsUnavailable(); HotspotMonitor.broadcastStopped()
            }
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                HotspotMonitor.notificationsUnavailable(); HotspotMonitor.broadcastStopped()
                SettingsRepo.update { previousSettings }; ExtrasRepo.update { previousExtras }
                IslandBus.media.value = null; IslandBus.calls.value = emptyMap()
                IslandBus.expanded.value = false; IslandBus.focus.value = null
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun render(content: @Composable () -> Unit = { IslandOverlay() }) {
        compose.runOnUiThread { compose.activity.setContent { IslandTheme {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 32.dp), contentAlignment = Alignment.TopCenter) { content() }
        } } }
        compose.waitForIdle()
    }
    private fun notification(count: Int, pkg: String = "android"): StatusBarNotification {
        val n = Notification.Builder(compose.activity, "hotspot-fixture").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Точка доступа включена").setContentText("Подключено устройств: $count").setOngoing(true).build()
        return StatusBarNotification(pkg, pkg, 706, "hotspot-fixture", Process.myUid(), 0, 0, n,
            Process.myUserHandle(), System.currentTimeMillis() + 1)
    }

    @Test fun unknownCountIsNotZeroAndNotificationUpdatesReachTheCard() {
        compose.runOnUiThread { HotspotMonitor.onBroadcast(true) }
        render()
        compose.onNodeWithContentDescription("Hotspot").performTouchInput { click() }
        compose.onNodeWithText("Connected device count is unavailable").assertIsDisplayed()
        for (count in listOf(0, 1, 2, 1, 0)) {
            compose.runOnUiThread { HotspotMonitor.onPosted(compose.activity, notification(count)) }
            compose.onNodeWithText("Connected devices: $count").assertIsDisplayed()
        }
        compose.runOnUiThread { HotspotMonitor.onBroadcast(false) }
        compose.onNodeWithContentDescription("hotspot-card").assertDoesNotExist()
    }

    @Test fun thirdPartyPackageCannotSupplyAHotspotCounter() {
        compose.runOnUiThread {
            HotspotMonitor.onBroadcast(true)
            HotspotMonitor.onPosted(compose.activity, notification(2, compose.activity.packageName))
        }
        assertNull(HotspotMonitor.state.value.clients)
    }

    @Test fun musicRetainsItsCardAndHotspotCanBeOpenedFromTheBubble() {
        compose.runOnUiThread {
            HotspotMonitor.onBroadcast(true)
            IslandBus.media.value = MediaInfo("music", "test.player", "Test track", "Artist", null, null,
                Tints.Lavender, 180_000, 10_000, 0, 1f, false, true)
        }
        render()
        compose.onNodeWithContentDescription("Hotspot").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithContentDescription("hotspot-card").assertIsDisplayed()
        assertNotNull(IslandBus.media.value)
        compose.runOnUiThread { SettingsRepo.update { copy(evHotspot = false) } }
        compose.onNodeWithContentDescription("hotspot-card").assertDoesNotExist()
        assertNotNull(IslandBus.media.value)
    }

    @Test fun incomingCallKeepsPriorityAndHidesTheSecondaryBubble() {
        compose.runOnUiThread {
            HotspotMonitor.onBroadcast(true)
            val call = CallInfo("call", "test.dialer", "Caller", null, true, 0, System.currentTimeMillis(), null, null, null, null)
            IslandBus.calls.value = mapOf(call.key to call)
        }
        render()
        compose.onNodeWithText("Caller").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hotspot").assertDoesNotExist()
    }

    @Test fun lockedCardDoesNotOfferASettingsAction() {
        compose.runOnUiThread { IslandBus.locked.value = true }
        render { ExpandedContent(Act.HotspotA(com.akira.island.service.HotspotStatus(true, 2)), SettingsRepo.current, null, BatteryInfo(70), 216.dp) }
        compose.onNodeWithText("Connected devices: 2").assertIsDisplayed()
        compose.onNodeWithText("Hotspot settings").assertDoesNotExist()
    }
}
