package com.akira.island.service

import com.akira.island.core.NetworkRate

/** Pure counter model. Null means no trustworthy/visible estimate, rather than an invented zero. */
internal class NetworkRateCounter {
    private var previous: Triple<Long, Long, Long>? = null
    private var visible = false
    private var busy = 0
    private var quiet = 0
    fun reset() { previous = null; visible = false; busy = 0; quiet = 0 }
    fun sample(rx: Long, tx: Long, elapsed: Long): NetworkRate? {
        if (rx < 0 || tx < 0 || elapsed < 0) { reset(); return null }
        val before = previous
        previous = Triple(rx, tx, elapsed)
        if (before == null) return null
        val dt = elapsed - before.third
        if (dt <= 0 || dt > 10_000 || rx < before.first || tx < before.second) {
            reset(); previous = Triple(rx, tx, elapsed); return null
        }
        val rate = NetworkRate((rx - before.first).toDouble() * 1000 / dt, (tx - before.second).toDouble() * 1000 / dt)
        if (rate.incoming + rate.outgoing >= 1024) { busy++; quiet = 0 } else { quiet++; busy = 0 }
        if (busy >= 1) visible = true
        if (quiet >= 3) visible = false
        return rate.takeIf { visible }
    }
}
