package com.akira.island.island

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.MainActivity
import com.akira.island.ui.screens.BehaviorScreen
import com.akira.island.ui.screens.ExtrasScreen
import com.akira.island.update.UpdateChecker
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

class ShortcutsBatteryDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var oldSettings = IslandSettings()
    private var oldExtras = ExtraSettings()
    private var oldLocked = false
    private val seed = object : ExternalResource() {
        override fun before() {
            val i = InstrumentationRegistry.getInstrumentation()
            i.runOnMainSync {
                SettingsRepo.init(i.targetContext); ExtrasRepo.init(i.targetContext)
                oldSettings = SettingsRepo.current; oldExtras = ExtrasRepo.state.value; oldLocked = IslandBus.locked.value
                SettingsRepo.update { IslandSettings(language = AppLanguage.EN, reduceEffects = true, lyricsEnabled = false,
                    autoUpdateCheck = false, lastSeenVersion = UpdateChecker.currentVersion(i.targetContext), quickActions = emptyList()) }
                IslandBus.locked.value = false
            }
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.update { oldSettings }; ExtrasRepo.update { oldExtras }; IslandBus.locked.value = oldLocked; IslandBus.expanded.value=false
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)
    private val apps = listOf("com.akira.island", "com.android.settings", "com.android.contacts",
        "com.android.dialer", "com.android.calendar", "example.missing")
    private val contacts = (1..6).map { ContactShortcut("Contact $it", "content://com.android.contacts/contacts/$it") }

    private fun content(width: Int = 276, scale: Float = 1f, body: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                IslandTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                        Box(Modifier.fillMaxSize().background(Color.Black).padding(top = 24.dp)) {
                            Box(Modifier.width(width.dp)) { body() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureToImage() // Wait for the first actual draw on software emulators.
    }

    private fun screenshot(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "shortcuts-battery-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun orbitPagesKeepAllTwelveTargetsAndTheirRealCallbacks() {
        var openedApp: String? = null
        var openedContact: ContactShortcut? = null
        content { ShortcutOrbit(apps, contacts, { openedApp = it }, { openedContact = it }) }
        compose.onNodeWithTag("shortcut-orbit").assertIsDisplayed()
        compose.onNodeWithTag("shortcuts-previous").assertDoesNotExist()
        apps.forEach { compose.onNodeWithTag("app:$it").assertIsDisplayed() }
        compose.onNodeWithTag("app:${apps.first()}").performClick()
        compose.runOnIdle { assertEquals(apps.first(), openedApp) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ownLabel = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(ownLabel).fetchSemanticsNodes().isNotEmpty() }
        screenshot("apps-orbit")
        contacts.forEach { compose.onNodeWithTag("contact:${it.uri}").assertIsDisplayed() }
        compose.onNodeWithTag("contact:${contacts.last().uri}").performClick()
        compose.runOnIdle { assertEquals(contacts.last(), openedContact) }
        compose.onNodeWithTag("shortcuts-next").assertDoesNotExist()
        screenshot("contacts-orbit")
    }

    @Test fun largeFontsUseGridAndRemovingLastPageKeepsRemainingAppsReachable() {
        compose.runOnUiThread { ExtrasRepo.update { ExtraSettings(apps = this@ShortcutsBatteryDeviceTest.apps, contacts = this@ShortcutsBatteryDeviceTest.contacts) } }
        content(width = 216, scale = 1.5f) {
            val extras = ExtrasRepo.state.collectAsState().value
            ShortcutOrbit(extras.apps, extras.contacts, {}, {})
        }
        compose.onNodeWithTag("shortcut-grid").assertIsDisplayed()
        compose.onNodeWithTag("contact:${contacts.last().uri}").assertIsDisplayed()
        screenshot("large-font-grid")
        compose.runOnUiThread { ExtrasRepo.update { copy(contacts = emptyList()) } }
        compose.onNodeWithTag("app:${apps.first()}").assertIsDisplayed()
        compose.onNodeWithTag("shortcuts-next").assertDoesNotExist()
    }

    @Test fun lockImmediatelyHidesAppsAndContactsWithoutErasingSelections() {
        compose.runOnUiThread { ExtrasRepo.update { ExtraSettings(apps = this@ShortcutsBatteryDeviceTest.apps, contacts = this@ShortcutsBatteryDeviceTest.contacts) } }
        compose.runOnUiThread {
            SettingsRepo.update { copy(cardCharging=false) }
            IslandBus.expanded.value=true; IslandBus.notice.value=null
        }
        content { IslandOverlay() }
        compose.onNodeWithTag("app:${apps.first()}").assertIsDisplayed()
        compose.runOnUiThread { IslandBus.locked.value = true }
        // StateFlow delivery to collectAsState is asynchronous; wait for that frame, not an old tree.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("shortcut-orbit").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("shortcut-orbit").assertDoesNotExist()
        compose.runOnIdle { assertEquals(apps, ExtrasRepo.state.value.apps); assertEquals(contacts, ExtrasRepo.state.value.contacts) }
        compose.runOnUiThread { IslandBus.locked.value = false }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("app:${apps.first()}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("app:${apps.first()}").assertIsDisplayed()
    }

    @Test fun settingsRemoveButtonOnlyRemovesTheChosenContact() {
        compose.runOnUiThread { ExtrasRepo.update { ExtraSettings(apps = listOf(this@ShortcutsBatteryDeviceTest.apps.first()), contacts = this@ShortcutsBatteryDeviceTest.contacts) } }
        content(width = 320) { ExtrasScreen {} }
        compose.onNodeWithContentDescription("Remove Contact 1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(contacts.drop(1), ExtrasRepo.state.value.contacts)
            assertEquals(listOf(apps.first()), ExtrasRepo.state.value.apps)
        }
        compose.onNodeWithContentDescription("Remove Contact 2").performScrollTo().assertIsDisplayed()
        screenshot("selected-contact-rows")
    }

    @Test fun batteryToggleUsesTheExistingLivePreviewWithoutPostingRealEvents() {
        val notice = IslandBus.notice.value
        val media = IslandBus.media.value
        content(width = 320) { BehaviorScreen {} }
        compose.onNodeWithText("Battery in the player").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(SettingsRepo.current.showPlayerBattery) }
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        screenshot("battery-setting-preview")
        compose.onNodeWithText("Battery in the player").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(SettingsRepo.current.showPlayerBattery)
            assertEquals(notice, IslandBus.notice.value)
            assertEquals(media, IslandBus.media.value)
        }
    }

    @Test fun realPlayerShowsOnlyKnownBatteryAndPreservesTheTransportAndTimeline() {
        val media = MediaInfo("test", "example.player", "A long music title", "Artist", null, null, Color.Yellow,
            180_000, 42_000, android.os.SystemClock.elapsedRealtime(), 0f, false, true)
        fun player(level: Int, enabled: Boolean, percentOnly: Boolean = false, charging: Boolean = false,
            width: Int = 216, scale: Float = 1.5f, contentScale: Int = 130) {
            content(width = width, scale = scale) {
                ExpandedContent(Act.MediaA(media), SettingsRepo.current.copy(showPlayerBattery = enabled, batteryPercentOnly = percentOnly, contentScale = contentScale),
                    null, BatteryInfo(level, charging = charging), width.dp)
            }
        }
        player(100, true)
        compose.onNodeWithTag("player-battery").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("0:42").assertExists()
        screenshot("player-battery-large-font")
        player(51, true, width = 320, scale = 1f, contentScale = 100)
        compose.onNodeWithText("51%").assertIsDisplayed()
        screenshot("player-battery-normal")
        player(-1, true)
        compose.onNodeWithTag("player-battery").assertDoesNotExist()
        player(15, false)
        compose.onNodeWithTag("player-battery").assertDoesNotExist()
        player(1, true, percentOnly = true, charging = true)
        compose.onNodeWithContentDescription("Battery: 1%, Charging").assertIsDisplayed()
    }
}
