package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Passive sources only: no polling, private APIs, root, permissions or raw notification logs. */
object HotspotMonitor {
    private val tracker = HotspotTracker()
    private val mutable = MutableStateFlow(HotspotStatus())
    val state = mutable.asStateFlow()
    private val sources = setOf("android", "com.android.systemui", "com.android.settings",
        "com.android.networkstack.tethering", "com.google.android.networkstack.tethering",
        "com.miui.securitycenter", "com.oplus.wirelesssettings", "com.coloros.wirelesssettings")

    private fun publish(status: HotspotStatus) {
        mutable.value = status
        SystemEventObservation.state.value = SystemEventObservation.state.value.copy(hotspot = status.enabled)
    }

    @Synchronized fun broadcastStarted() = publish(tracker.broadcastUnavailable())
    @Synchronized fun broadcastStopped() = publish(tracker.broadcastUnavailable())
    @Synchronized fun onBroadcast(enabled: Boolean) = publish(tracker.broadcast(enabled, System.currentTimeMillis()))
    @Synchronized fun notificationsUnavailable() = publish(tracker.replace(emptyList()))
    @Synchronized fun onRemoved(key: String) = publish(tracker.remove(key))

    @Synchronized fun onPosted(context: Context, notification: StatusBarNotification) {
        val snapshot = snapshot(context, notification)
        publish(if (snapshot != null) tracker.notification(snapshot) else tracker.remove(notification.key))
    }

    @Synchronized fun rescan(context: Context, notifications: List<StatusBarNotification>) =
        publish(tracker.replace(notifications.mapNotNull { runCatching { snapshot(context, it) }.getOrNull() }))

    private fun snapshot(context: Context, sbn: StatusBarNotification): HotspotSnapshot? {
        if (sbn.packageName !in sources) return null
        val system = runCatching {
            val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
        if (!system) return null
        val n = sbn.notification ?: return null
        val ex = n.extras ?: return null
        val texts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT, Notification.EXTRA_INFO_TEXT)
            .mapNotNull { ex.getCharSequence(it)?.toString() } +
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().take(6).map { it.toString() }
        val evidence = HotspotNotificationParser.parse(true, n.flags and Notification.FLAG_ONGOING_EVENT != 0, texts) ?: return null
        return HotspotSnapshot(sbn.key, evidence, sbn.postTime)
    }
}
