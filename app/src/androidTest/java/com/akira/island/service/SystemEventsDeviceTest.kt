package com.akira.island.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import com.akira.island.data.AppLanguage
import com.akira.island.data.SettingsRepo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class SystemEventsDeviceTest {
    @Test fun debuggingSettingIsObservedAndDeliveredAsARealEvent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { SettingsRepo.init(context) }
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val wifi = manager.allNetworks.any { manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }
        org.junit.Assume.assumeTrue("Android requires a connected Wi-Fi network for wireless debugging", wifi)
        val resolver = context.contentResolver
        val key = "adb_wifi_enabled"
        val original = Settings.Global.getString(resolver, key)
        val saved = SettingsRepo.current
        val events = SystemEvents(context)
        fun write(value: String?) {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try { assertTrue(Settings.Global.putString(resolver, key, value)) }
            finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        }
        fun await(description: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 30_000
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertTrue(description, condition())
        }
        val automation = instrumentation.uiAutomation
        val accessibilityInfo = automation.serviceInfo
        val originalFlags = accessibilityInfo.flags
        accessibilityInfo.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = accessibilityInfo
        fun allowDebuggingOnTestNetwork() {
            // Android asks for confirmation on a new Wi-Fi network, even for a shell-written setting.
            val root = automation.rootInActiveWindow ?: return
            if (root.packageName?.toString() != "com.android.systemui") return
            root.findAccessibilityNodeInfosByViewId("android:id/button1")
                .firstOrNull { it.isClickable && it.isEnabled }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        try {
            write("0")
            instrumentation.runOnMainSync {
                SettingsRepo.update { copy(language = AppLanguage.RU, evDebugging = true, noticeDelayMs = 0) }
                IslandBus.notice.value = null
                events.start()
            }
            await("VPN snapshot should be readable using the app's normal permissions") { SystemEventObservation.state.value.vpn != null }
            await("Initial debugging state should be off") { SystemEventObservation.state.value.wirelessDebug == false }
            write("1")
            await("Debugging observer or fallback polling must see the confirmed change") {
                allowDebuggingOnTestNetwork()
                Settings.Global.getInt(resolver, key, 0) == 1 && SystemEventObservation.state.value.wirelessDebug == true
            }
            await("A real debugging change must reach the island event queue") {
                IslandBus.notice.value?.let { it.title == "Отладка Wi-Fi" && it.state == "Вкл" } == true
            }
        } finally {
            instrumentation.runOnMainSync { events.stop(); SettingsRepo.update { saved }; IslandBus.notice.value = null }
            write(original)
            accessibilityInfo.flags = originalFlags
            automation.serviceInfo = accessibilityInfo
        }
    }
}
