package com.akira.island.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingRoutingDeviceTest {
    @Test fun recorderMislabelledAsNavigationKeepsItsTimerAndStopIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val chrono = IslandBus.chrono.value
        val live = IslandBus.live.value
        val progress = IslandBus.progress.value
        val stop = PendingIntent.getBroadcast(context, 91, Intent("feedback-test-stop").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val started = System.currentTimeMillis() - 10_000
        fun notification(title: String, pkg: String, id: Int, post: Long, intent: PendingIntent? = null): StatusBarNotification {
            val builder = Notification.Builder(context, "feedback-tests").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title).setContentText(if (id == 91) "" else "100 m · Arrive 13:00")
                .setCategory(Notification.CATEGORY_NAVIGATION).setOngoing(true).setWhen(0).setProgress(100, 1, false)
                .setContentIntent(intent)
            return StatusBarNotification(pkg, pkg, id, "feedback", Process.myUid(), 0, 0, builder.build(), Process.myUserHandle(), post)
        }
        try {
            val recording = notification("Tap here to stop recording.", "com.android.systemui", 91, started, stop)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, recording, initial = true) }
            val result = IslandBus.chrono.value[recording.key]!!
            assertTrue(result.recording); assertTrue(result.screenRecording)
            assertEquals(stop, result.stop)
            assertFalse(result.countDown)
            assertFalse(IslandBus.live.value.containsKey(recording.key))
            assertFalse(IslandBus.progress.value.containsKey(recording.key))
            val updated = notification("Tap here to stop recording.", "com.android.systemui", 91, started + 3_000, stop)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, updated, initial = true) }
            assertEquals("An artwork/text update must not reset recording duration", result.base, IslandBus.chrono.value[updated.key]!!.base)
            val route = notification("Turn right", "com.google.android.apps.maps", 92, started)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, route, initial = true) }
            assertTrue("Genuine navigation remains a live activity", IslandBus.live.value.containsKey(route.key))
            instrumentation.runOnMainSync { NotificationParser.onRemoved(recording.key) }
            assertFalse(IslandBus.chrono.value.containsKey(recording.key))
        } finally {
            instrumentation.runOnMainSync { IslandBus.chrono.value = chrono; IslandBus.live.value = live; IslandBus.progress.value = progress }
        }
    }
}
