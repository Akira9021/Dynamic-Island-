package com.akira.island.service

/** Stable identity of a message, independent of notification artwork/ranking updates. */
internal data class MessageIdentity(
    val title: String,
    val text: String,
    val sender: String,
    val timestamp: Long,
    val count: Int,
)

/**
 * A notification key is a conversation, not a message. Equal text with a new message timestamp/count
 * must be shown again. Cross-app text matching is deliberately avoided: "OK" from two apps is not a duplicate.
 */
internal class MessageDeduplicator(private val limit: Int = 200) {
    private val byKey = object : LinkedHashMap<String, MessageIdentity>(limit, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MessageIdentity>?) = size > limit
    }
    private val events = object : LinkedHashMap<Pair<String, MessageIdentity>, Long>(limit, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, MessageIdentity>, Long>?) = size > limit
    }

    fun shouldShow(key: String, pkg: String, identity: MessageIdentity, dedupe: Boolean, now: Long): Boolean {
        if (byKey[key] == identity) return false
        byKey[key] = identity
        if (!dedupe || identity.timestamp <= 0) return true
        val event = pkg to identity
        val previous = events[event]
        events[event] = now
        return previous == null || now - previous >= 120_000
    }

    fun forget(key: String) { byKey.remove(key) }
}
