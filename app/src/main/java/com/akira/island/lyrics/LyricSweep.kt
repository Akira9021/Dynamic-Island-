package com.akira.island.lyrics

/** Visual interpolation between real line timestamps; it is not a source of word timings. */
internal fun lyricSweepFraction(positionMs: Long, startMs: Long, endMs: Long): Float {
    val start = startMs.coerceAtLeast(0)
    if (endMs <= start) return 1f
    if (positionMs <= start) return 0f
    if (positionMs >= endMs) return 1f
    val interval = (endMs - start).toDouble() * 0.85
    return ((positionMs - start).toDouble() / interval).toFloat().coerceIn(0f, 1f)
}

/** Seek and wake jumps follow the player immediately; ordinary ticks can interpolate. */
internal fun animateLyricSweep(previousMs: Long, positionMs: Long, playing: Boolean): Boolean =
    playing && positionMs > previousMs && positionMs.toDouble() - previousMs.toDouble() <= 500
