package com.akira.island.service

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reads public battery metadata only; does not spoof charge, change system settings or read logs. */
@RunWith(AndroidJUnit4::class)
class FrameworkBatteryDeviceTest {
    @Test fun publicAndroidBatterySnapshotReachesTheActualIslandState() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        SettingsRepo.init(context)
        val saved = SettingsRepo.current
        val previous = IslandBus.battery.value
        val events = SystemEvents(context)
        try {
            i.runOnMainSync {
                SettingsRepo.update { copy(enabled = false, adaptiveChargeEta = false) }
                events.start()
            }
            i.waitForIdleSync()
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
            val raw = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val expected = if (raw < 0 || scale <= 0) -1 else (raw.toLong() * 100 / scale).coerceIn(0, 100).toInt()
            val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 &&
                status in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
            assertEquals("Displayed level comes from Android rather than a full-battery placeholder", expected, IslandBus.battery.value.level)
            assertEquals(charging, IslandBus.battery.value.charging)
            assertFalse("System data is not labelled as our approximate fallback", IslandBus.battery.value.remainingApproximate)
        } finally {
            i.runOnMainSync {
                events.stop()
                SettingsRepo.update { saved }
                IslandBus.battery.value = previous
            }
        }
    }
}
