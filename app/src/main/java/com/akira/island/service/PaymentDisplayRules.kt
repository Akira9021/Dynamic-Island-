package com.akira.island.service

/** Empty expanded notification text must not hide its populated normal text. */
internal fun paymentNotificationBody(bigText: String?, text: String?): String =
    bigText?.takeIf(String::isNotBlank) ?: text.orEmpty()

/** Preserve settling for ordinary UI changes, but never absorb a new explicit success into baseline. */
internal fun paymentBaselinePending(elapsedMs: Long, baseline: Set<String>, texts: Set<String>): Boolean =
    elapsedMs < 500 && texts.none { it !in baseline && PayScreenParser.paid(it) }
