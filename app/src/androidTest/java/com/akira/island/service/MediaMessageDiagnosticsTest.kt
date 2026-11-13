package com.akira.island.service

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.*
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

/** Synthetic providers exercise public Android APIs; these are not Telegram/WhatsApp accounts. */
class MediaMessageDiagnosticsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(description: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 30_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(40)
        assertTrue(description, condition())
    }

    private class Provider(val session: MediaSession) {
        val commands = ConcurrentLinkedQueue<String>()
        var position = 1_000L
        fun state(value: Int) = session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO)
            .addCustomAction("diagnostic.like", "Like", android.R.drawable.star_big_off)
            .setState(value, position, if (value == PlaybackState.STATE_PLAYING) 1f else 0f).build())
        fun configure(title: String) {
            session.setCallback(object : MediaSession.Callback() {
                override fun onPause() { commands.add("pause"); state(PlaybackState.STATE_PAUSED) }
                override fun onPlay() { commands.add("play"); state(PlaybackState.STATE_PLAYING) }
                override fun onSeekTo(pos: Long) { commands.add("seek:$pos"); position = pos; state(PlaybackState.STATE_PAUSED) }
                override fun onCustomAction(action: String, extras: android.os.Bundle?) { commands.add("custom:$action") }
            }, Handler(Looper.getMainLooper()))
            session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, title).putLong(MediaMetadata.METADATA_KEY_DURATION, 120_000).build())
            state(PlaybackState.STATE_PLAYING)
            session.isActive = true
        }
    }

    private fun withPlayers(block: (MediaMonitor, Provider, Provider) -> Unit) {
        val context = instrumentation.targetContext
        SettingsRepo.init(context)
        val saved = SettingsRepo.current
        val previousMedia = IslandBus.media.value
        val previousLyrics = IslandBus.lyrics.value
        val previousStatus = IslandBus.lyricsStatus.value
        val previousController = Controls.mediaController
        val first = Provider(MediaSession(context, "diagnostic-first"))
        val second = Provider(MediaSession(context, "diagnostic-second"))
        val monitor = MediaMonitor(context)
        try {
            main { SettingsRepo.update { copy(lyricsEnabled = false, autoUpdateCheck = false) }; first.configure("Diagnostic one"); monitor.start() }
            publish(monitor, first, 701)
            await("First provider selected") { IslandBus.media.value?.title == "Diagnostic one" }
            block(monitor, first, second)
        } finally {
            main {
                monitor.stop(); first.session.release(); second.session.release()
                SettingsRepo.update { saved }; IslandBus.media.value = previousMedia
                IslandBus.lyrics.value = previousLyrics; IslandBus.lyricsStatus.value = previousStatus
                Controls.mediaController = previousController
            }
        }
    }

    private fun publish(monitor: MediaMonitor, provider: Provider, id: Int) = main {
        val context = instrumentation.targetContext
        val n = Notification.Builder(context, "diagnostics").setSmallIcon(android.R.drawable.ic_media_play)
            .setStyle(Notification.MediaStyle().setMediaSession(provider.session.sessionToken)).build()
        monitor.onNotificationPosted(StatusBarNotification(context.packageName, context.packageName, id, "diagnostic",
            Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis()))
    }

    @Test fun pausePlaySeekAndCustomActionReachThePublishedSession() = withPlayers { _, first, _ ->
        main { Controls.playPause() }
        await("Pause reaches the actual MediaSession callback") { "pause" in first.commands && IslandBus.media.value?.playing == false }
        main { Controls.playPause() }
        await("Resume reaches the actual MediaSession callback") { "play" in first.commands && IslandBus.media.value?.playing == true }
        assertTrue(IslandBus.media.value!!.canSeek)
        main { Controls.seekTo(42_000) }
        await("Seek updates the source and published position") { "seek:42000" in first.commands && IslandBus.media.value?.positionMs == 42_000L }
        val like = IslandBus.media.value!!.like
        assertNotNull("Existing custom-action route must remain available", like)
        main { Controls.playerAction(like!!.action) }
        await("Custom action is delivered without an invented success state") { "custom:diagnostic.like" in first.commands }
        assertEquals(42_000L, IslandBus.media.value!!.positionMs)
    }

    @Test fun anOldPausedSessionNotificationDoesNotStealCommandsFromThePlayingSession() = withPlayers { monitor, first, second ->
        main { first.state(PlaybackState.STATE_PAUSED); second.configure("Diagnostic two") }
        publish(monitor, second, 702)
        await("New playing session is selected") { IslandBus.media.value?.title == "Diagnostic two" }
        publish(monitor, first, 701)
        main {
            assertEquals(second.session.sessionToken, Controls.mediaController?.sessionToken)
            Controls.seekTo(9_000)
        }
        await("Seek is delivered only to selected session") { "seek:9000" in second.commands }
        assertFalse("Old notification must not redirect the command", "seek:9000" in first.commands)
    }

    private fun message(key: String, text: String) = Notice("Diagnostic conversation", icon = Icons.Rounded.ChatBubble,
        tint = Color.Blue, style = NoticeStyle.MESSAGE,
        message = AppMessage(key, "example.messenger", "Synthetic messenger", text, null, null, null))

    private fun withQueue(block: (String, ConcurrentLinkedQueue<Long>) -> Unit) {
        SettingsRepo.init(instrumentation.targetContext)
        val saved = SettingsRepo.current
        val oldNotice = IslandBus.notice.value
        val oldHold = IslandBus.noticeHold.value
        val oldDetail = IslandBus.messageDetail.value
        val ids = ConcurrentLinkedQueue<Long>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val prefix = "diagnostic-${SystemClock.elapsedRealtimeNanos()}"
        try {
            main {
                SettingsRepo.update { copy(messageSeconds = 1, noticeDelayMs = 0, autoUpdateCheck = false) }
                scope.launch { IslandBus.notice.collect { it?.id?.let(ids::add) } }
            }
            block(prefix, ids)
        } finally {
            main {
                listOf("chat", "pending", "last").forEach { IslandBus.removeMessage("$prefix:$it") }
                IslandBus.noticeHold.value = false
                scope.cancel(); SettingsRepo.update { saved }
                IslandBus.notice.value = oldNotice; IslandBus.noticeHold.value = oldHold; IslandBus.messageDetail.value = oldDetail
            }
        }
    }

    @Test fun updatingTheSameConversationDoesNotReplaceAHeldMessage() = withQueue { prefix, _ ->
        val first = message("$prefix:chat", "First message")
        val next = message("$prefix:chat", "Second message")
        main { IslandBus.post(first) }
        await("First message appears") { IslandBus.notice.value?.id == first.id }
        main { IslandBus.noticeHold.value = true; IslandBus.post(next) }
        SystemClock.sleep(1_300)
        assertEquals("Reading/answering keeps the current snapshot", first.id, IslandBus.notice.value?.id)
        assertEquals("First message", IslandBus.notice.value?.message?.text)
        main { IslandBus.noticeHold.value = false }
        await("Queued update follows after reading") { IslandBus.notice.value?.id == next.id }
        assertEquals("Second message", IslandBus.notice.value?.message?.text)
    }

    @Test fun dismissingAPendingMessagePreventsItsReappearance() = withQueue { prefix, ids ->
        val first = message("$prefix:chat", "Reading")
        val removed = message("$prefix:pending", "Dismissed in shade")
        val last = message("$prefix:last", "Still unread")
        main { IslandBus.post(first) }
        await("First message appears") { IslandBus.notice.value?.id == first.id }
        main {
            IslandBus.noticeHold.value = true; IslandBus.post(removed); IslandBus.post(last)
            IslandBus.removeMessage("$prefix:pending"); IslandBus.removeMessage("$prefix:chat")
        }
        await("Next non-dismissed message appears") { IslandBus.notice.value?.id == last.id }
        assertFalse("A cancelled queued message must never be shown", removed.id in ids)
    }
}
