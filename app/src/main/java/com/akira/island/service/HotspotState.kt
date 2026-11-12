package com.akira.island.service

/** No client identifiers are retained. null is an unavailable reading, never a guessed zero. */
data class HotspotStatus(val enabled: Boolean? = null, val clients: Int? = null) {
    init { require(clients == null || enabled == true && clients in 0..999) }
}

internal data class HotspotEvidence(val enabled: Boolean, val clients: Int? = null)
internal data class HotspotSnapshot(val key: String, val evidence: HotspotEvidence, val postedAt: Long)

/** Event-driven reconciliation; removing an Android notification does not prove hotspot Off. */
internal class HotspotTracker {
    private var broadcast: Boolean? = null
    private var broadcastAt = Long.MIN_VALUE
    private var lastOffAt = Long.MIN_VALUE
    private val notifications = linkedMapOf<String, HotspotSnapshot>()

    fun broadcast(enabled: Boolean, at: Long): HotspotStatus {
        if (!enabled) {
            lastOffAt = at
            notifications.clear()
        }
        broadcast = enabled
        broadcastAt = at
        return status()
    }

    fun broadcastUnavailable(): HotspotStatus {
        broadcast = null
        return status()
    }

    fun notification(snapshot: HotspotSnapshot): HotspotStatus {
        // Do not resurrect an old ongoing notification when the listener reconnects after Off.
        if (snapshot.postedAt <= lastOffAt) return status()
        if (!snapshot.evidence.enabled) {
            lastOffAt = snapshot.postedAt
            notifications.entries.removeAll { it.value.postedAt <= lastOffAt }
            if (snapshot.postedAt >= broadcastAt) broadcast = null
        }
        notifications.remove(snapshot.key)
        notifications[snapshot.key] = snapshot
        return status()
    }

    fun remove(key: String): HotspotStatus {
        notifications.remove(key)
        return status()
    }

    fun replace(snapshots: List<HotspotSnapshot>): HotspotStatus {
        notifications.clear()
        snapshots.sortedBy { it.postedAt }.forEach { notification(it) }
        return status()
    }

    fun status(): HotspotStatus {
        val newestAt = notifications.values.maxOfOrNull { it.postedAt }
        val latest = notifications.values.lastOrNull { it.postedAt == newestAt }
        val enabled = if (latest != null && (broadcast == null || latest.postedAt > broadcastAt))
            latest.evidence.enabled else broadcast
        // Only an active, current notification can supply a count; no network traffic inference.
        val clients = latest?.takeIf { enabled == true && it.evidence.enabled }?.evidence?.clients
        return HotspotStatus(enabled, clients)
    }
}
