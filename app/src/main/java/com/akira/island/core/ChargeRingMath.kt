package com.akira.island.core

/**
 * Pure math for the ring of the secondary charging bubble.
 *
 * The ring shows how much of the current charging session has already
 * elapsed, using the remaining time estimate as the other half:
 *
 *     progress = elapsed / (elapsed + remaining)
 *
 * Values are clamped to 0..1 and, inside one session, can only grow.
 * No Android dependency, so this is directly testable with JUnit.
 */
object ChargeRingMath {

    /** Reference of one charging session, from plug-in to unplug. */
    data class Session(
        val startElapsedRealtime: Long = 0L,
        val startLevel: Int = -1,
        val maxProgress: Float = 0f,
    ) {
        val valid: Boolean get() = startElapsedRealtime > 0L
    }

    /**
     * Ring progress for a session that started at [startElapsedRealtime].
     * Returns 0 when the reference or the estimate is missing.
     */
    fun progress(
        nowElapsedRealtime: Long,
        startElapsedRealtime: Long,
        remainingMinutes: Int?,
    ): Float {
        if (!isValidReference(startElapsedRealtime, nowElapsedRealtime)) return 0f
        return progressFromMillis(
            elapsedMs = nowElapsedRealtime - startElapsedRealtime,
            remainingMs = remainingMs(remainingMinutes),
        )
    }

    /** Progress from raw milliseconds; exposed for tests and reusable callers. */
    fun progressFromMillis(elapsedMs: Long, remainingMs: Long): Float {
        if (elapsedMs <= 0L || remainingMs <= 0L) return 0f
        val total = elapsedMs.toDouble() + remainingMs.toDouble()
        if (total <= 0.0) return 0f
        return (elapsedMs.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
    }

    /** Minutes to milliseconds, 0 when the estimate is missing or not positive. */
    fun remainingMs(minutes: Int?): Long =
        if (minutes != null && minutes > 0) minutes.toLong() * 60_000L else 0L

    /** The ring never goes backwards inside the same session. */
    fun advance(previous: Float, candidate: Float): Float =
        maxOf(previous.coerceIn(0f, 1f), candidate.coerceIn(0f, 1f))

    /**
     * A saved reference is usable only if it is in the past. A reference from
     * before a reboot is greater than the current elapsedRealtime and is dropped.
     */
    fun isValidReference(startElapsedRealtime: Long, nowElapsedRealtime: Long): Boolean =
        startElapsedRealtime > 0L && startElapsedRealtime <= nowElapsedRealtime

    /** New session, created when the charger is plugged in. */
    fun startSession(nowElapsedRealtime: Long, level: Int): Session =
        Session(nowElapsedRealtime, level, 0f)
}
