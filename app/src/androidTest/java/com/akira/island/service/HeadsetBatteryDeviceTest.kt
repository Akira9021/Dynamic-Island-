package com.akira.island.service

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.data.SettingsRepo
import org.junit.Assert.*
import org.junit.Test

/** Synthetic device state + real IslandBus queue, not an assertion about any physical OEM device. */
class HeadsetBatteryDeviceTest {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=i.runOnMainSync(block)
    private fun await(condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+15_000
        while(!condition()&&SystemClock.elapsedRealtime()<end) SystemClock.sleep(25)
        assertTrue("Expected queue state",condition())
    }
    private fun exercise(block:(SystemEvents,HeadsetBatteryTracker)->Unit) {
        val context=i.targetContext;SettingsRepo.init(context)
        val saved=SettingsRepo.current
        val previous=IslandBus.notice.value
        val events=SystemEvents(context)
        val tracker=SystemEvents::class.java.getDeclaredField("headsetBattery").apply{isAccessible=true}.get(events) as HeadsetBatteryTracker
        try {
            main { SettingsRepo.update { copy(autoUpdateCheck=false,noticeDelayMs=0,noticeDurationMs=1500,evWifiOn=false,evWifiOff=false) }; events.start() }
            block(events,tracker)
        } finally { main { events.stop();IslandBus.notice.value=previous;IslandBus.noticeHold.value=false;SettingsRepo.update { saved } } }
    }
    private fun update(events:SystemEvents) = SystemEvents::class.java.getDeclaredMethod("updateVisibleHeadsetBattery").apply{isAccessible=true}.invoke(events)
    private fun notice()=Notice("Synthetic headphones",icon=Icons.Rounded.Headphones,tint=Color.Green)

    @Test fun lateBatteryKeepsNoticeIdentityHoldAndOriginalExpiry()=exercise { events,tracker ->
        val n=notice()
        main { tracker.connect("synthetic-a",n.id);IslandBus.post(n) }
        await { IslandBus.notice.value?.id==n.id }
        val began=SystemClock.elapsedRealtime()
        main {
            IslandBus.noticeHold.value=true
            tracker.battery("synthetic-a",53);update(events)
            assertEquals(n.id,IslandBus.notice.value?.id)
            assertEquals(53,(IslandBus.notice.value!!.ring!!*100).toInt())
            assertTrue(IslandBus.noticeHold.value)
            tracker.battery("synthetic-a",-1);update(events);assertNull(IslandBus.notice.value!!.ring)
            IslandBus.noticeHold.value=false
        }
        await { IslandBus.notice.value?.id!=n.id }
        assertTrue("A value update must not extend or repeat a banner",SystemClock.elapsedRealtime()-began<4000)
    }
    @Test fun queuedConnectionGetsTheLatestBatteryAndForeignBannerIsUntouched()=exercise { events,tracker ->
        val foreign=Notice("Foreign notification",icon=Icons.Rounded.Headphones,tint=Color.Blue)
        val n=notice()
        main { IslandBus.post(foreign) }
        await { IslandBus.notice.value?.id==foreign.id }
        main {
            tracker.connect("synthetic-a",n.id);IslandBus.post(n)
            tracker.battery("synthetic-a",72);update(events)
            assertEquals(foreign,IslandBus.notice.value)
        }
        await { IslandBus.notice.value?.id==n.id && IslandBus.notice.value?.ring!=null }
        assertEquals(72,(IslandBus.notice.value!!.ring!!*100).toInt())
        main { tracker.disconnect("synthetic-a");update(events) }
        assertNull(IslandBus.notice.value!!.ring)
    }
}
