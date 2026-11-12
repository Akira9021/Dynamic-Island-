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
import com.akira.island.core.*
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.lyrics.LyricsRepo
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.net.*
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LyricsLifecycleDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var monitor: MediaMonitor
    private lateinit var session: MediaSession
    private lateinit var savedSettings: IslandSettings
    private var savedMedia: MediaInfo? = null
    private var savedLyrics: Lyrics? = null
    private var savedStatus: Pair<String, LyricsStatus>? = null
    private var savedFocus: String? = null
    private var savedExpanded = false
    private var savedOn = true

    @Before fun prepare() {
        Http.responder = { response(it, false) }
        Http.requests.clear()
        resetLookup()
        instrumentation.runOnMainSync {
            SettingsRepo.init(context)
            savedSettings = SettingsRepo.current
            savedMedia = IslandBus.media.value; savedLyrics = IslandBus.lyrics.value; savedStatus = IslandBus.lyricsStatus.value
            savedFocus = IslandBus.focus.value; savedExpanded = IslandBus.expanded.value; savedOn = IslandBus.screenOn.value
            IslandBus.media.value = null; IslandBus.lyrics.value = null; IslandBus.lyricsStatus.value = null
            IslandBus.expanded.value = false; IslandBus.focus.value = null; IslandBus.screenOn.value = true
            SettingsRepo.update { copy(lyricsEnabled = true, autoUpdateCheck = false) }
            session = MediaSession(context, "lyrics-lifecycle")
            monitor = MediaMonitor(context)
            monitor.start()
        }
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            monitor.stop(); session.release()
            SettingsRepo.update { savedSettings }
            IslandBus.media.value = savedMedia; IslandBus.lyrics.value = savedLyrics; IslandBus.lyricsStatus.value = savedStatus
            IslandBus.focus.value = savedFocus; IslandBus.expanded.value = savedExpanded; IslandBus.screenOn.value = savedOn
        }
        Http.responder = null
        resetLookup()
    }

    @Test(timeout = 120_000) fun retryWaitsForAnOpenPlayerAndScreenThenUpgradesCachedPlainText() {
        val healthy = java.util.concurrent.atomic.AtomicBoolean(false)
        Http.responder = { response(it, healthy.get()) }
        play("Synthetic A")
        await("Initial plain text") { IslandBus.lyrics.value?.synced == false }
        healthy.set(true)
        SystemClock.sleep(5_500)
        assertEquals("Hidden player must not retry", 1, exactRequests())
        instrumentation.runOnMainSync {
            IslandBus.screenOn.value = false; Controls.expand("media")
        }
        SystemClock.sleep(5_500)
        assertEquals("Screen-off player must not start a retry", 1, exactRequests())
        instrumentation.runOnMainSync { IslandBus.screenOn.value = true }
        await("Cached plain lyrics upgraded by the single visible retry") { IslandBus.lyrics.value?.synced == true }
        assertEquals(2, exactRequests())
        assertEquals(session.sessionToken, Controls.mediaController?.sessionToken)
        assertTrue(IslandBus.media.value?.playing == true)
    }

    @Test(timeout = 120_000) fun reopeningOrWakingDoesNotCreateAThirdAutomaticLookup() {
        instrumentation.runOnMainSync { Controls.expand("media") }
        play("Synthetic A")
        await("One retry completes") { exactRequests() == 2 && IslandBus.lyrics.value?.synced == false }
        SystemClock.sleep(5_500)
        instrumentation.runOnMainSync { Controls.collapse(); IslandBus.screenOn.value = false }
        instrumentation.runOnMainSync { IslandBus.screenOn.value = true; Controls.expand("media") }
        SystemClock.sleep(5_500)
        assertEquals("Only one automatic retry for this current request", 2, exactRequests())
    }

    @Test(timeout = 120_000) fun disablingLyricsCancelsPendingRetryWithoutStoppingPlayback() {
        play("Synthetic A")
        await("Plain fallback") { IslandBus.lyrics.value?.synced == false }
        instrumentation.runOnMainSync { SettingsRepo.update { copy(lyricsEnabled = false) }; Controls.expand("media") }
        await("Lyrics cleared") { IslandBus.lyrics.value == null && IslandBus.lyricsStatus.value == null }
        SystemClock.sleep(5_500)
        assertEquals(1, exactRequests())
        assertEquals(session.sessionToken, Controls.mediaController?.sessionToken)
        Http.responder = { response(it, true) }
        instrumentation.runOnMainSync { SettingsRepo.update { copy(lyricsEnabled = true) } }
        await("Enabling lyrics can recover current track") { IslandBus.lyrics.value?.synced == true }
    }

    @Test(timeout = 120_000) fun aSlowOldTrackCannotOverwriteTheNewTrack() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Http.responder = { url ->
            if (url.path == "/api/get" && trackTitle(url) == "Synthetic A") {
                entered.countDown()
                check(release.await(45, TimeUnit.SECONDS))
            }
            response(url, true)
        }
        try {
            play("Synthetic A")
            assertTrue("First request started", entered.await(20, TimeUnit.SECONDS))
            play("Synthetic B")
            await("Second track has its own lyrics") {
                IslandBus.media.value?.title == "Synthetic B" && IslandBus.lyrics.value?.key == IslandBus.media.value?.key
            }
            val newKey = IslandBus.lyrics.value!!.key
            release.countDown()
            SystemClock.sleep(500)
            assertEquals(newKey, IslandBus.lyrics.value?.key)
            assertTrue(IslandBus.lyrics.value?.synced == true)
        } finally { release.countDown() }
    }

    private fun play(title: String) = instrumentation.runOnMainSync {
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Synthetic Artist")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Synthetic Album").putLong(MediaMetadata.METADATA_KEY_DURATION, 120_000).build())
        session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO)
            .setState(PlaybackState.STATE_PLAYING, 1_000, 1f).build())
        session.isActive = true
        val n = Notification.Builder(context, "lyrics-test").setSmallIcon(android.R.drawable.ic_media_play)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)).build()
        monitor.onNotificationPosted(StatusBarNotification(context.packageName, context.packageName, 701, "lyrics",
            Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis()))
    }

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 25_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        assertTrue("$message; media=${IslandBus.media.value?.title}; key=${IslandBus.media.value?.key}; " +
            "lyrics=${IslandBus.lyrics.value?.key}; status=${IslandBus.lyricsStatus.value}; " +
            "enabled=${SettingsRepo.current.lyricsEnabled}; requests=${synchronized(Http.requests) { Http.requests.toList() }}", condition())
    }

    private fun exactRequests() = synchronized(Http.requests) { Http.requests.count { it.path == "/api/get" } }

    private fun response(url: URL, synced: Boolean): String {
        if (url.host != "lrclib.net") return ""
        val title = trackTitle(url) ?: "Synthetic A"
        val record = JSONObject().put("trackName", title).put("artistName", "Synthetic Artist")
            .put("albumName", "Synthetic Album").put("duration", 120).put("plainLyrics", "Synthetic plain text")
            .put("syncedLyrics", if (synced) "[00:01]Synthetic complete line\n[00:10]Synthetic next line" else JSONObject.NULL)
        return if (url.path == "/api/get") record.toString() else "[]"
    }

    private fun trackTitle(url: URL) = url.query.orEmpty().split('&').firstOrNull { it.startsWith("track_name=") }
        ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }

    private fun resetLookup() {
        for ((field, map) in listOf("cache" to "entries", "backoff" to "deadlines")) {
            val owner = LyricsRepo::class.java.getDeclaredField(field).apply { isAccessible = true }.get(null)
            (owner.javaClass.getDeclaredField(map).apply { isAccessible = true }.get(owner) as MutableMap<*, *>).clear()
        }
    }

    private object Http {
        val requests = Collections.synchronizedList(mutableListOf<URL>())
        @Volatile var responder: ((URL) -> String)? = null
        private val original = URL("https://localhost/")
        init {
            URL.setURLStreamHandlerFactory { protocol -> if (protocol == "https") object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    val route = responder ?: return URL(original, url.toExternalForm()).openConnection()
                    requests.add(url)
                    val body = route(url)
                    return object : HttpURLConnection(url) {
                        override fun connect() = Unit
                        override fun disconnect() = Unit
                        override fun usingProxy() = false
                        override fun getResponseCode() = if (url.host == "lrclib.net") 200 else 404
                        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                    }
                }
            } else null }
        }
    }
}
