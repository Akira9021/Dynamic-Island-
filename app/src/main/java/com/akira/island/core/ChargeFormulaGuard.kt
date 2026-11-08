package com.akira.island.core

/**
 * Sanity checks for the "current + charge counter" fallback.
 *
 * The charge counter is not reliable on every device: some OEMs report a
 * counter that goes down while the battery is charging, or a counter that
 * derives an impossible full capacity. When that happens the fallback must
 * be skipped instead of showing an invented time.
 *
 * Pure Kotlin, so it is directly testable with JUnit.
 */
object ChargeFormulaGuard {

    /** 1.500 mAh, lower bound for a plausible phone battery. */
    const val MIN_CAPACITY_UAH = 1_500_000L

    /** 12.000 mAh, upper bound that still covers large batteries. */
    const val MAX_CAPACITY_UAH = 12_000_000L

    /** Counter noise: anything below this is not considered a real drop. */
    private const val COUNTER_NOISE_UAH = 2_000L

    /** Full capacity in µAh derived from the counter, or null when unusable. */
    fun fullCapacityUah(level: Int, chargeCounterUah: Long): Long? {
        if (level !in 1..100) return null
        if (chargeCounterUah <= 0L || chargeCounterUah > Long.MAX_VALUE / 100L) return null
        return chargeCounterUah * 100L / level
    }

    /** Whether a derived full capacity is in the plausible range. */
    fun isPlausibleCapacity(capacityUah: Long?): Boolean =
        capacityUah != null && capacityUah in MIN_CAPACITY_UAH..MAX_CAPACITY_UAH

    /**
     * True when [current] rose above [peak] by more than the counter noise.
     * A healthy gauge rises while charging; the Vivo V2050 counter falls, so
     * this never becomes true there and the formula branch stays disabled.
     */
    fun counterRoseAboveNoise(peak: Long, current: Long): Boolean {
        if (peak <= 0L || current <= 0L) return false
        val tolerance = maxOf(COUNTER_NOISE_UAH, peak / 1000L)
        return current > peak + tolerance
    }

    /**
     * True when the counter fell below the peak seen during this charge while
     * the level did not go down. A real gauge never does that while charging.
     * [peakLevel] is the level at which [peak] was observed.
     */
    fun counterDroppedWhileCharging(peak: Long, current: Long, level: Int, peakLevel: Int): Boolean {
        if (peak <= 0L || current <= 0L) return false
        if (level < peakLevel) return false
        val tolerance = maxOf(COUNTER_NOISE_UAH, peak / 1000L)
        return current < peak - tolerance
    }
}
