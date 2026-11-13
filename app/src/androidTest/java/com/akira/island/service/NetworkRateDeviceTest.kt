package com.akira.island.service

import android.app.Notification
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NetworkRateDeviceTest {
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = i.runOnMainSync(block)
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(20)
        assertTrue(message, condition())
    }
    @Test fun monitorStopsAndRestartsWithANewBaselineWithoutCreatingCards() {
        val active = MutableStateFlow(false)
        val reads = AtomicInteger()
        val latest = AtomicReference<NetworkRate?>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val progress = IslandBus.progress.value
        val monitor = NetworkRateMonitor(active, read = {
            val n = reads.incrementAndGet().toLong()
            TrafficSnapshot(n * 10_000, n * 4000, n * 2000)
        }, publish = { latest.set(it) }, interval = 40)
        try {
            main { monitor.start(scope); monitor.start(scope) }
            SystemClock.sleep(150); assertEquals(0, reads.get())
            main { active.value = true }
            await("Valid rate after two intervals") { latest.get() != null }
            assertEquals(5000.0, latest.get()!!.incoming, 0.0)
            main { active.value = false }
            await("Disabled state clears estimate") { latest.get() == null }
            val stopped = reads.get(); SystemClock.sleep(150); assertEquals(stopped, reads.get())
            main { active.value = true }
            await("Fresh samples after restart") { latest.get() != null && reads.get() >= stopped + 3 }
            assertEquals(progress, IslandBus.progress.value)
        } finally { main { monitor.stop(); scope.cancel() } }
        val stopped = reads.get(); SystemClock.sleep(150)
        assertEquals(stopped, reads.get()); assertNull(latest.get())
    }

    @Test fun sourceDetailsFollowRealProgressAndMediaDoesNotCreateADownload() {
        val context = i.targetContext
        SettingsRepo.init(context)
        val saved = SettingsRepo.current
        val old = IslandBus.progress.value
        fun notification(id: Int, media: Boolean = false, progress: Int = 40): StatusBarNotification {
            val n = Notification.Builder(context, "network-test").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Synthetic transfer").setContentText("Download: 1.5 MB/s · 2 min left")
                .setCategory(if (media) Notification.CATEGORY_TRANSPORT else Notification.CATEGORY_PROGRESS)
                .setProgress(100, progress, false).build()
            return StatusBarNotification("example.transfer", "example.transfer", id, "network-test", Process.myUid(), 0, 0,
                n, Process.myUserHandle(), System.currentTimeMillis())
        }
        val file = notification(810)
        val media = notification(811, true)
        try {
            main {
                SettingsRepo.update { copy(autoUpdateCheck = false) }
                NotificationParser.onPosted(context, file, initial = true)
                val p = IslandBus.progress.value[file.key]!!
                assertEquals(0.4f, p.fraction, 0.001f)
                assertEquals(1_500_000.0, p.transferDetails!!.bytesPerSecond!!, 0.0)
                assertEquals(120L, p.transferDetails!!.etaSeconds)
                NotificationParser.onPosted(context, media, initial = true)
                assertFalse(IslandBus.progress.value.containsKey(media.key))
                NotificationParser.onPosted(context, notification(810, progress = 100), initial = true)
                assertFalse(IslandBus.progress.value.containsKey(file.key))
            }
        } finally { main { IslandBus.progress.value = old; SettingsRepo.update { saved } } }
    }
}
