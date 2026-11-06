package com.akira.island.data

/** Counts notifications known to the island, rather than individual lines in a conversation. */
internal fun retainedNotificationCount(currentKey: String, retainedKeys: List<String>): Int =
    (retainedKeys + currentKey).distinct().size
