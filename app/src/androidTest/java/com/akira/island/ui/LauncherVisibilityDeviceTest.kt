package com.akira.island.ui

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.net.Uri
import android.service.quicksettings.TileService
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.data.LauncherVisibility
import com.akira.island.core.Controls
import com.akira.island.data.SettingsRepo
import com.akira.island.service.IslandService
import com.akira.island.service.IslandTile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Exercises the installed package's real component overrides, not a mocked PackageManager. */
@RunWith(AndroidJUnit4::class)
class LauncherVisibilityDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pm get() = context.packageManager
    private val main get() = ComponentName(context, MainActivity::class.java)
    private val tile get() = ComponentName(context, IslandTile::class.java)

    @Before fun startVisible() {
        LauncherVisibility.onTileRemoved(context)
    }

    @After fun alwaysLeaveTheIconVisible() {
        // Even a failed assertion must never leave a device with its launcher entry disabled.
        LauncherVisibility.onTileRemoved(context)
        assertNotNull(pm.getLaunchIntentForPackage(context.packageName))
    }

    @Test fun addingATileAloneOrOpeningOtherSettingsCannotAuthorizeHiding() {
        assertFalse(LauncherVisibility.setHidden(context, true))
        LauncherVisibility.onTileAdded(context)
        assertFalse(LauncherVisibility.setHidden(context, true))
        for (malformed in listOf(Uri.parse("https://example.invalid"), Bundle())) {
            LauncherVisibility.onSettingsOpened(context,
                Intent(TileService.ACTION_QS_TILE_PREFERENCES).putExtra(Intent.EXTRA_COMPONENT_NAME, malformed))
            assertFalse("Malformed exported preferences intents must neither crash nor confirm recovery",
                LauncherVisibility.state.value.recoveryTested)
        }
        LauncherVisibility.onSettingsOpened(context, LauncherVisibility.settingsIntent(context))
        assertFalse(LauncherVisibility.setHidden(context, true))
        LauncherVisibility.onSettingsOpened(context,
            Intent(TileService.ACTION_QS_TILE_PREFERENCES).putExtra(Intent.EXTRA_COMPONENT_NAME, main))
        assertFalse(LauncherVisibility.setHidden(context, true))
        assertNotNull(pm.getLaunchIntentForPackage(context.packageName))
    }

    @Suppress("DEPRECATION")
    @Test fun hiddenAliasLeavesRecoveryAndLegacyPinnedActivityWorking() {
        SettingsRepo.init(context)
        val savedSettings = SettingsRepo.current
        // Keep this activity/navigation test independent of GitHub and the update dialog.
        instrumentation.runOnMainSync {
            SettingsRepo.update { copy(autoUpdateCheck = false, lastSeenVersion = com.akira.island.update.UpdateChecker.currentVersion(context)) }
        }
        try {
            LauncherVisibility.onTileAdded(context)
            val recovery = Intent(TileService.ACTION_QS_TILE_PREFERENCES).setPackage(context.packageName)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, tile)
            ActivityScenario.launch<MainActivity>(recovery).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(TileService.ACTION_QS_TILE_PREFERENCES, activity.intent.action)
                    assertTrue(LauncherVisibility.state.value.canHide)
                }
            }
            assertTrue(LauncherVisibility.setHidden(context, true))
            assertNull(pm.getLaunchIntentForPackage(context.packageName))
            assertTrue(launcherEntries().isEmpty())
            val launcherApps = context.getSystemService(LauncherApps::class.java)
                .getActivityList(context.packageName, Process.myUserHandle())
                .map { it.componentName.flattenToShortString() }
            // Some Android/OEM launchers synthesize an App info entry despite no enabled alias.
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\nLauncherApps entries while alias hidden: $launcherApps\n")
            })
            assertTrue(pm.getActivityInfo(main, 0).enabled)
            assertNotEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(main))
            var opened: Intent? = null
            val capture = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) { opened = intent }
            }
            Controls.openApp(capture, context.packageName)
            assertEquals("Island actions must reopen settings even while the launcher alias is hidden",
                main, opened?.component)
            for (action in listOf(TileService.ACTION_QS_TILE_PREFERENCES, Intent.ACTION_APPLICATION_PREFERENCES)) {
                val resolved = pm.resolveActivity(Intent(action).setPackage(context.packageName), 0)
                assertEquals(main.className, resolved?.activityInfo?.name)
            }
            // A pinned shortcut from the version that launched MainActivity directly still works.
            val oldPinnedShortcut = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(main)
            assertEquals(main.className, pm.resolveActivity(oldPinnedShortcut, 0)?.activityInfo?.name)

            ActivityScenario.launch<MainActivity>(LauncherVisibility.settingsIntent(context)
                .setAction(Intent.ACTION_APPLICATION_PREFERENCES)).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(Intent.ACTION_APPLICATION_PREFERENCES, activity.intent.action)
                    assertTrue(LauncherVisibility.refresh(activity).hidden)
                    assertTrue(activity.packageManager.getActivityInfo(main, 0).enabled)
                }
            }
            assertTrue(LauncherVisibility.setHidden(context, false))
            assertEquals(LauncherVisibility.launcherComponent(context).className,
                pm.getLaunchIntentForPackage(context.packageName)?.component?.className)
            assertFalse(launcherEntries().isEmpty())
            assertFalse("Showing the icon requires testing recovery again before the next hide",
                LauncherVisibility.setHidden(context, true))
        } finally {
            instrumentation.runOnMainSync { SettingsRepo.update { savedSettings } }
        }
    }

    @Test fun tileRemovalRestoresTheLauncherAndClearsTheRecoveryProof() {
        confirmTileRecovery()
        assertTrue(LauncherVisibility.setHidden(context, true))
        LauncherVisibility.onTileRemoved(context)
        assertFalse(LauncherVisibility.refresh(context).hidden)
        assertNotNull(pm.getLaunchIntentForPackage(context.packageName))
        assertFalse(LauncherVisibility.state.value.tileAdded)
        assertFalse(LauncherVisibility.state.value.recoveryTested)
        assertFalse(LauncherVisibility.setHidden(context, true))
    }

    @Suppress("DEPRECATION")
    @Test fun accessibilitySettingsGearTargetsTheAlwaysEnabledActivity() {
        val info = pm.getServiceInfo(ComponentName(context, IslandService::class.java), PackageManager.GET_META_DATA)
        val xml = info.loadXmlMetaData(pm, "android.accessibilityservice")
        assertNotNull(xml)
        xml!!.use { parser ->
            while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) parser.next()
            assertEquals(main.className,
                parser.getAttributeValue("http://schemas.android.com/apk/res/android", "settingsActivity"))
        }
        assertTrue(pm.getActivityInfo(main, 0).enabled)
    }

    private fun confirmTileRecovery() {
        LauncherVisibility.onTileAdded(context)
        LauncherVisibility.onSettingsOpened(context,
            Intent(TileService.ACTION_QS_TILE_PREFERENCES).putExtra(Intent.EXTRA_COMPONENT_NAME, tile))
        assertTrue(LauncherVisibility.state.value.canHide)
    }

    @Suppress("DEPRECATION")
    private fun launcherEntries() = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
    )
}
