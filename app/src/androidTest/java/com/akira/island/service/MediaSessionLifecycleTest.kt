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
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSessionLifecycleTest {
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
