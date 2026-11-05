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
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPauseDeviceTest {
    @Test(timeout = 150_000) fun aPausedSessionStaysWhileOpenThenHidesAfterClosing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        SettingsRepo.init(context)
        val saved = SettingsRepo.current
        val oldMedia = IslandBus.media.value
        val oldExpanded = IslandBus.expanded.value
        val oldFocus = IslandBus.focus.value
        val session = MediaSession(context, "feedback-pause")
        val monitor = MediaMonitor(context)
        fun await(description: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeoutMs
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertTrue(description, condition())
        }
        try {
            instrumentation.runOnMainSync {
                SettingsRepo.update { copy(lyricsEnabled = false) }
                IslandBus.expanded.value = true
                IslandBus.focus.value = "media"
                session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Paused test track")
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "feedback-pause").putLong(MediaMetadata.METADATA_KEY_DURATION, 228_000).build())
                session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SEEK_TO)
                    .setState(PlaybackState.STATE_PAUSED, 11_000, 0f).build())
                session.isActive = true
                monitor.start()
                val notification = Notification.Builder(context, "feedback-tests").setSmallIcon(android.R.drawable.ic_media_play)
                    .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)).build()
                monitor.onNotificationPosted(StatusBarNotification(context.packageName, context.packageName, 94, "feedback",
                    Process.myUid(), 0, 0, notification, Process.myUserHandle(), System.currentTimeMillis()))
            }
            await("The real MediaSession token must publish a paused player") {
                IslandBus.media.value?.let { it.title == "Paused test track" && !it.playing } == true
            }
            // Cross the production 45-second pause deadline, rather than mocking its visibility policy.
            SystemClock.sleep(46_500)
            assertEquals("An expanded paused player must remain available", "Paused test track", IslandBus.media.value?.title)
            instrumentation.runOnMainSync { IslandBus.expanded.value = false; IslandBus.focus.value = null }
            await("Closing the player must restore normal pause expiry", 50_000) { IslandBus.media.value == null }
        } finally {
            instrumentation.runOnMainSync {
                monitor.stop()
                session.release()
                SettingsRepo.update { saved }
                IslandBus.media.value = oldMedia
                IslandBus.expanded.value = oldExpanded
                IslandBus.focus.value = oldFocus
            }
        }
    }
}
