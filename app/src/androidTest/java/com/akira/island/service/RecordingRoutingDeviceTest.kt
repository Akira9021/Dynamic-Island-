package com.akira.island.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.session.MediaSession
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingRoutingDeviceTest {
    @Test fun pixelRecorderWithMediaTokenKeepsRealPauseResumeSaveAndOpenIntents() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = IslandBus.chrono.value
        val savedLive = IslandBus.live.value
        val savedProgress = IslandBus.progress.value
        val session = MediaSession(context, "pixel-recorder-regression")
        val pkg = "com.google.android.apps.recorder"
        fun intent(id: Int) = PendingIntent.getBroadcast(context, id, Intent("pixel-recorder-test-$id").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val pause = intent(161); val resume = intent(162); val save = intent(163); val open = intent(164)
        fun notification(title: String, paused: Boolean, finished: Boolean = false): StatusBarNotification {
            val builder = Notification.Builder(context, "pixel-recorder-test").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title).setContentText("00:12").setWhen(0).setContentIntent(open)
                .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            if (!finished) {
                builder.addAction(Notification.Action.Builder(null, if (paused) "Resume recording" else "Pause recording", if (paused) resume else pause).build())
                builder.addAction(Notification.Action.Builder(null, "Save", save).build())
            }
            val n = builder.build().apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }
            return StatusBarNotification(pkg, pkg, 161, "pixel", Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        }
        try {
            val running = notification("Recording", false)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, running, initial = true) }
            val first = IslandBus.chrono.value[running.key]!!
            assertEquals(com.akira.island.core.RecordingKind.VOICE, first.recordingKind)
            assertEquals(pause, first.pause); assertNull(first.resume)
            assertEquals(save, first.stop); assertEquals(open, first.open)
            assertFalse(first.countDown); assertFalse(first.screenRecording)
            val paused = notification("Recording paused", true)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, paused, initial = true) }
            val frozen = IslandBus.chrono.value[paused.key]!!
            assertEquals(12_000L, frozen.pausedMs)
            assertEquals(resume, frozen.resume); assertNull(frozen.pause); assertEquals(save, frozen.stop)
            val resumed = notification("Recording", false)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, resumed, initial = true) }
            assertNull(IslandBus.chrono.value[resumed.key]!!.pausedMs)
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, notification("Recording saved", false, true), initial = true) }
            assertFalse("A finished recording must leave the island", IslandBus.chrono.value.containsKey(running.key))
        } finally {
            instrumentation.runOnMainSync {
                session.release(); IslandBus.chrono.value = saved; IslandBus.live.value = savedLive; IslandBus.progress.value = savedProgress
            }
        }
    }

    @Test fun pixelRecorderPlaybackChronometerIsNotShownAsActiveCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = IslandBus.chrono.value
        val savedLive = IslandBus.live.value
        val savedProgress = IslandBus.progress.value
        val session = MediaSession(context, "pixel-playback-regression")
        val action = PendingIntent.getBroadcast(context, 165, Intent("pixel-playback-test").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val pkg = "com.google.android.apps.recorder"
        val n = Notification.Builder(context, "pixel-playback-test").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Recording 12").setContentText("00:12").setOngoing(true)
            .setWhen(System.currentTimeMillis() - 12_000).setUsesChronometer(true)
            .setCategory(Notification.CATEGORY_TRANSPORT).setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .addAction(Notification.Action.Builder(null, "Pause", action).build())
            .addAction(Notification.Action.Builder(null, "Stop", action).build()).build()
        val sbn = StatusBarNotification(pkg, pkg, 165, "pixel-playback", Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        try {
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, sbn, initial = true) }
            assertFalse("Listening to a saved recording must not create a recording timer", IslandBus.chrono.value.containsKey(sbn.key))
        } finally {
            instrumentation.runOnMainSync { session.release(); IslandBus.chrono.value = saved; IslandBus.live.value = savedLive; IslandBus.progress.value = savedProgress }
        }
    }

    @Test fun titlelessPixelRecordingCanExposeStatusOutsideTheMainTextField() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = IslandBus.chrono.value
        val savedLive = IslandBus.live.value
        val savedProgress = IslandBus.progress.value
        val pause = PendingIntent.getBroadcast(context, 166, Intent("pixel-titleless-test").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val pkg = "com.google.android.apps.recorder"
        val n = Notification.Builder(context, "pixel-titleless-test").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("").setWhen(0).addAction(Notification.Action.Builder(null, "Приостановить запись", pause).build()).build()
        n.extras.putCharSequence(Notification.EXTRA_INFO_TEXT, "Идёт запись")
        n.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("00:12"))
        val sbn = StatusBarNotification(pkg, pkg, 166, "pixel-titleless", Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        try {
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, sbn, initial = true) }
            val chrono = IslandBus.chrono.value[sbn.key]!!
            assertEquals(com.akira.island.core.RecordingKind.VOICE, chrono.recordingKind)
            assertEquals(pause, chrono.pause)
            assertNull(chrono.stop)
            assertTrue(chrono.elapsed(System.currentTimeMillis()) in 12_000L..13_000L)
        } finally {
            instrumentation.runOnMainSync { IslandBus.chrono.value = saved; IslandBus.live.value = savedLive; IslandBus.progress.value = savedProgress }
        }
    }

    @Test fun resumingARecorderWithoutAnElapsedFieldDoesNotCountThePause() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val saved = IslandBus.chrono.value
        val savedLive = IslandBus.live.value
        val savedProgress = IslandBus.progress.value
        val action = PendingIntent.getBroadcast(context, 96, Intent("test-recording").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        fun notification(button: String): StatusBarNotification {
            val n = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Recorder").setWhen(0).addAction(Notification.Action.Builder(null, button, action).build()).build()
            n.flags = n.flags or Notification.FLAG_FOREGROUND_SERVICE
            return StatusBarNotification("com.miui.soundrecorder", "com.miui.soundrecorder", 96, "test", Process.myUid(), 0, 0,
                n, Process.myUserHandle(), System.currentTimeMillis() - 5_000)
        }
        try {
            val running = notification("Pause")
            val paused = notification("Resume")
            instrumentation.runOnMainSync {
                NotificationParser.onPosted(context, running, initial = true)
                NotificationParser.onPosted(context, paused, initial = true)
            }
            val frozen = IslandBus.chrono.value[paused.key]!!.pausedMs!!
            SystemClock.sleep(2200)
            instrumentation.runOnMainSync {
                NotificationParser.onPosted(context, notification("Pause"), initial = true)
                val elapsed = IslandBus.chrono.value[running.key]!!.elapsed(System.currentTimeMillis())
                assertTrue("Resuming must continue from recorded time, not include the pause", elapsed in frozen..frozen + 1000)
            }
        } finally {
            instrumentation.runOnMainSync { IslandBus.chrono.value = saved; IslandBus.live.value = savedLive; IslandBus.progress.value = savedProgress }
        }
    }
    @Test fun foregroundRecorderWithoutAStopButtonTracksPauseAndFinish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = IslandBus.chrono.value
        val live = IslandBus.live.value
        val progress = IslandBus.progress.value
        val action = PendingIntent.getBroadcast(context, 95, Intent("test-recorder-pause").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        fun notification(title: String, button: String): StatusBarNotification {
            val n = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title).setContentText("00:12").setWhen(0)
                .addAction(Notification.Action.Builder(null, button, action).build()).build()
            n.flags = n.flags or Notification.FLAG_FOREGROUND_SERVICE
            return StatusBarNotification("com.miui.soundrecorder", "com.miui.soundrecorder", 95, "test", Process.myUid(), 0, 0,
                n, Process.myUserHandle(), System.currentTimeMillis())
        }
        try {
            val running = notification("Диктофон", "Пауза")
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, running, initial = true) }
            val first = IslandBus.chrono.value[running.key]!!
            assertEquals(com.akira.island.core.RecordingKind.VOICE, first.recordingKind)
            assertNull(first.stop)
            assertFalse(first.screenRecording)
            val paused = notification("Запись приостановлена", "Продолжить")
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, paused, initial = true) }
            assertEquals(12_000L, IslandBus.chrono.value[paused.key]!!.pausedMs)
            val ended = notification("Запись сохранена", "Открыть")
            instrumentation.runOnMainSync { NotificationParser.onPosted(context, ended, initial = true) }
            assertFalse(IslandBus.chrono.value.containsKey(ended.key))
        } finally {
            instrumentation.runOnMainSync { IslandBus.chrono.value = original; IslandBus.live.value = live; IslandBus.progress.value = progress }
        }
    }
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
