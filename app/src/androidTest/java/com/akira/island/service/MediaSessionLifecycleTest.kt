package com.akira.island.service

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.Controls
import com.akira.island.core.ChronoInfo
import com.akira.island.core.IslandBus
import com.akira.island.core.RecordingKind
import com.akira.island.data.SettingsRepo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSessionLifecycleTest {
    @Test fun confirmedCaptureDoesNotDuplicateMusicAndSavedPlaybackReturnsAfterCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        SettingsRepo.init(context)
        val savedSettings = SettingsRepo.current
        val savedChrono = IslandBus.chrono.value
        val session = MediaSession(context, "recorder-media-session-regression")
        val monitor = MediaMonitor(context)
        val recordingKey = "recorder-media-session-test"
        val externalKey = "other-recorder-media-session-test"
        fun await(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 20_000
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertTrue(message, condition())
        }
        fun metadata(title: String) = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 60_000).build()
        fun recording(pkg: String, key: String) = ChronoInfo(key, pkg, "Recording", null,
            System.currentTimeMillis() - 12_000, false, System.currentTimeMillis(), recordingKind = RecordingKind.VOICE)
        try {
            instrumentation.runOnMainSync {
                SettingsRepo.update { copy(lyricsEnabled = false) }
                // A different app recording must not silence this player's unrelated session.
                IslandBus.chrono.value = mapOf(externalKey to recording("example.other.recorder", externalKey))
                session.setMetadata(metadata("Live session"))
                session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 1_000, 1f).build())
                session.isActive = true
                monitor.start()
                val n = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_media_play)
                    .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)).build()
                monitor.onNotificationPosted(StatusBarNotification(context.packageName, context.packageName, 405, "test",
                    Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis()))
            }
            await("Unrelated recording must preserve normal music playback") { IslandBus.media.value?.title == "Live session" }
            instrumentation.runOnMainSync {
                IslandBus.chrono.value = IslandBus.chrono.value + (recordingKey to recording(context.packageName, recordingKey))
            }
            await("Confirmed live capture must not also appear as music") { IslandBus.media.value == null && Controls.mediaController == null }
            instrumentation.runOnMainSync {
                session.setMetadata(metadata("Saved recording"))
                IslandBus.chrono.value = IslandBus.chrono.value - recordingKey
            }
            await("The same package must return to normal playback after capture ends") {
                IslandBus.media.value?.title == "Saved recording" && Controls.mediaController?.sessionToken == session.sessionToken
            }
        } finally {
            instrumentation.runOnMainSync {
                monitor.stop(); session.release(); IslandBus.chrono.value = savedChrono; SettingsRepo.update { savedSettings }
            }
        }
    }

    @Test fun progressNotificationsReuseTheSessionAndDoNotLoseCallbacks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        SettingsRepo.init(context)
        val saved = SettingsRepo.current
        val session = MediaSession(context, "maintenance-session")
        val monitor = MediaMonitor(context)
        fun await(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 20_000
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertTrue(message, condition())
        }
        fun metadata(title: String) = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 60_000).build()
        try {
            instrumentation.runOnMainSync {
                SettingsRepo.update { copy(lyricsEnabled = false) }
                session.setMetadata(metadata("Track A"))
                session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 1_000, 1f).build())
                session.isActive = true
                monitor.start()
                val n = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_media_play)
                    .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)).build()
                val sbn = StatusBarNotification(context.packageName, context.packageName, 404, "test", Process.myUid(), 0, 0,
                    n, Process.myUserHandle(), System.currentTimeMillis())
                monitor.onNotificationPosted(sbn)
                val controller = Controls.mediaController
                assertNotNull(controller)
                repeat(6) { monitor.onNotificationPosted(sbn) }
                assertSame("Repeated progress notifications should retain the existing controller", controller, Controls.mediaController)
            }
            await("Initial playback must be visible") { IslandBus.media.value?.title == "Track A" }
            instrumentation.runOnMainSync { session.setMetadata(metadata("Track B")) }
            await("Metadata callbacks must survive progress updates") { IslandBus.media.value?.title == "Track B" }
            instrumentation.runOnMainSync { session.release() }
            await("A destroyed session must be removed") { IslandBus.media.value == null && Controls.mediaController == null }
        } finally {
            instrumentation.runOnMainSync { monitor.stop(); session.release(); SettingsRepo.update { saved } }
        }
    }
}
