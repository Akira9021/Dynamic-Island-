package com.akira.island.core

internal enum class ChargeEtaSource { SYSTEM, OBSERVED }
internal data class ChargeEta(val remainingMs: Long, val source: ChargeEtaSource)

/**
 * Conservative, session-only fallback from measured percentage growth. Call on existing battery
 * updates; owns no timer, receiver, persistence, hardware-current heuristic or wall-clock access.
 * The UI must label OBSERVED as approximate: charge taper and phone load can change its rate.
 */
internal class AdaptiveChargeEta {
    private var plug = 0
    private var level = -1
    private var anchorMs = 0L
    private var lastSampleMs = -1L
    private val rates = ArrayDeque<Long>()

    fun observe(elapsedMs: Long, percent: Int, charging: Boolean, plugType: Int, systemRemainingMs: Long?): ChargeEta? {
        if (!charging || plugType <= 0 || percent !in 0..99 || elapsedMs < 0) {
            reset()
            return null
        }
        val system = systemRemainingMs?.takeIf { it in 1..MAX_ETA_MS }?.let { ChargeEta(it, ChargeEtaSource.SYSTEM) }
        if (lastSampleMs < 0 || elapsedMs < lastSampleMs || elapsedMs - lastSampleMs > MAX_GAP_MS ||
            plugType != plug || percent < level || percent - level > 3 || elapsedMs - anchorMs > MAX_ANCHOR_MS) {
            reset()
            plug = plugType; level = percent; anchorMs = elapsedMs; lastSampleMs = elapsedMs
            return system
        }
        lastSampleMs = elapsedMs
        if (percent > level) {
            val elapsed = elapsedMs - anchorMs
            val rate = elapsed / (percent - level)
            if (rate in MIN_RATE_MS..MAX_RATE_MS) {
                rates.addLast(rate)
                if (rates.size > 5) rates.removeFirst()
            } else rates.clear()
            level = percent; anchorMs = elapsedMs
        }
        if (system != null) return system
        if (rates.size < 2) return null
        val sorted = rates.sorted()
        val median = if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            else sorted[sorted.size / 2]
        // If growth stalls, do not keep advertising the old speed indefinitely.
        if (elapsedMs - anchorMs > minOf(MAX_ANCHOR_MS, median * 3)) return null
        val remaining = median * (100 - percent)
        return remaining.takeIf { it in 1..MAX_ETA_MS }?.let { ChargeEta(it, ChargeEtaSource.OBSERVED) }
    }

    fun reset() {
        plug = 0; level = -1; anchorMs = 0; lastSampleMs = -1; rates.clear()
    }

    private companion object {
        const val MIN_RATE_MS = 20_000L
        const val MAX_RATE_MS = 10 * 60_000L
        const val MAX_GAP_MS = 2 * 60_000L
        const val MAX_ANCHOR_MS = 20 * 60_000L
        const val MAX_ETA_MS = 12 * 60 * 60_000L
    }
}
