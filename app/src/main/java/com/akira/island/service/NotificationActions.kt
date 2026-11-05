package com.akira.island.service

/** Guards cancellation using the current system entry, rather than an outdated copy in history. */
internal class NotificationActions(
    private val isClearable: (String) -> Boolean,
    private val cancel: (String) -> Unit,
) {
    fun dismiss(key: String): Boolean = runCatching {
        if (!isClearable(key)) return false
        cancel(key)
        true
    }.getOrDefault(false)

    fun dismissAll(keys: List<String>): Int = keys.distinct().count(::dismiss)
}
