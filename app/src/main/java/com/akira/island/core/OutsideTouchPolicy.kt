package com.akira.island.core

internal enum class OutsideTouchDecision { KEEP, CLOSE_REPLY, COLLAPSE }

/** Reply/keyboard rules take precedence over the optional expanded-card preference. */
internal fun outsideTouchDecision(expanded: Boolean, replyOpen: Boolean, keyboardShown: Boolean,
    keepExpanded: Boolean): OutsideTouchDecision = when {
    replyOpen -> if (keyboardShown) OutsideTouchDecision.KEEP else OutsideTouchDecision.CLOSE_REPLY
    expanded && !keepExpanded -> OutsideTouchDecision.COLLAPSE
    else -> OutsideTouchDecision.KEEP
}
