package com.akira.island.service

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStyleProgressRegressionDeviceTest {
    @Test fun realDownloadFromTheSamePlayerKeepsItsReportedProgress() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val before = IslandBus.progress.value
        val n = Notification.Builder(context, "synthetic-download")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Synthetic download")
            .setContentText("45 of 180").setOngoing(true).setProgress(180, 45, false)
            .setStyle(Notification.BigTextStyle().bigText("Downloading a file")).build()
        val entry = StatusBarNotification("example.synthetic.player", "example.synthetic.player", 482, "download",
            Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        try {
            i.runOnMainSync { NotificationParser.onPosted(context, entry, initial = true) }
            val progress = requireNotNull(IslandBus.progress.value[entry.key])
            assertEquals(45, progress.progress)
            assertEquals(180, progress.max)
        } finally {
            i.runOnMainSync { NotificationParser.onRemoved(entry.key); IslandBus.progress.value = before }
        }
    }

    @Test fun mediaStyleWithoutCategoryOrTokenDoesNotBecomeADownload() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val before = IslandBus.progress.value
        val n = Notification.Builder(context, "synthetic-media")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Synthetic song")
            .setContentText("Synthetic artist").setOngoing(true).setProgress(180, 45, false).build()
        n.category = null
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MediaStyle")
        val entry = StatusBarNotification("example.synthetic.player", "example.synthetic.player", 481, "media-style",
            Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        try {
            i.runOnMainSync { NotificationParser.onPosted(context, entry, initial = true) }
            assertFalse("A media-style playback position is not a file download", IslandBus.progress.value.containsKey(entry.key))
        } finally {
            i.runOnMainSync { NotificationParser.onRemoved(entry.key); IslandBus.progress.value = before }
        }
    }
}
