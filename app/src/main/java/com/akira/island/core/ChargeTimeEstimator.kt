package com.akira.island.core

import android.content.Context
import android.os.Build
import android.os.BatteryManager
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Estimates remaining charge time using multiple strategies, in order:
 * 1. BatteryManager.computeChargeTimeRemaining() on API 28+ (if plausible)
 * 2. Per-percent times learned from previous charging sessions
 * 3. Rate observed during the current charging session
 * 4. Current + charge counter (when the device exposes both)
 * 5. Typical rate for the power source (AC/USB/Wireless)
 *
 * Strategy 5 makes sure an estimate is available as soon as the cable is
 * plugged in, even on devices that do not expose the charge counter.
 */
class ChargeTimeEstimator private constructor(
    private val context: Context,
) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val prefs = context.getSharedPreferences("charge_time_estimator", Context.MODE_PRIVATE)

    // Moving average window for current readings (~60 seconds at 10s intervals = 6 samples)
    private val currentSamples = ConcurrentLinkedQueue<Long>()
    private val MAX_CURRENT_SAMPLES = 6

    // Exponential moving average for per-percent charge times by plug type
    // Key: plug type (BatteryManager.BATTERY_PLUGGED_*), Value: array of seconds per percent (1-100)
    private val learnedTimes = mutableMapOf<Int, DoubleArray>()

    // Last seen battery level and plug type for learning
    private var lastLevel = -1
    private var lastPlugType = 0
    private var lastLevelChangeTime = 0L
    private var isCharging = false

    // Average seconds per percent observed during the current session
    private var sessionRateSecs = 0.0

    // Constants
    private val MAX_PLAUSIBLE_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val UPDATE_THROTTLE_MS = 30_000L // 30 seconds
    private val MIN_LEVEL_FOR_CAPACITY = 5 // Minimum battery level to estimate full capacity
    private val SLOWDOWN_START_PERCENT = 80 // Above this, charging slows down (constant voltage phase)

    // Slowdown factor: 1.0 up to 80%, then increases to ~2.0 at 100%
    private val slowdownFactors = DoubleArray(101).apply {
        for (i in 0..100) {
            this[i] = if (i <= SLOWDOWN_START_PERCENT) 1.0
            else 1.0 + (i - SLOWDOWN_START_PERCENT) / 20.0 * 1.0 // Linear from 1.0 to 2.0
        }
    }

    private var lastEstimateUpdate = 0L
    private var lastEstimate: Int? = null
    private var lastEstimatedMinutes = -1

    companion object {
        @Volatile private var INSTANCE: ChargeTimeEstimator? = null

        fun getInstance(context: Context): ChargeTimeEstimator {
            val existing = INSTANCE
            if (existing != null) return existing
            return synchronized(this) {
                INSTANCE ?: ChargeTimeEstimator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Call this when ACTION_BATTERY_CHANGED is received (or periodically while charging).
     * Updates internal state and learns from charging patterns.
     */
    fun onBatteryChanged(
        level: Int,
        status: Int,
        plugged: Int,
        chargeCounter: Long,
        currentNow: Long,
    ): ChargeEstimate? {
        val now = System.currentTimeMillis()
        val wasCharging = isCharging
        isCharging = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)

        // Handle charging start/stop
        if (isCharging && !wasCharging) {
            // Charging started: reset the session but keep learned history
            lastLevel = level
            lastPlugType = plugged
            lastLevelChangeTime = now
            sessionRateSecs = 0.0
            currentSamples.clear()
            lastEstimate = null
            lastEstimatedMinutes = -1
            lastEstimateUpdate = 0L
            loadLearnedTimes()
        } else if (!isCharging && wasCharging) {
            // Charging stopped - save learned data
            saveLearnedTimes()
            lastLevel = -1
            sessionRateSecs = 0.0
            lastEstimate = null
            lastEstimatedMinutes = -1
        }

        if (!isCharging || level <= 0) {
            return null
        }

        // Sample current for moving average (only while charging and visible)
        if (currentNow != Long.MIN_VALUE && currentNow != Integer.MIN_VALUE.toLong() && currentNow != 0L) {
            val normalizedCurrent = normalizeCurrent(currentNow)
            if (normalizedCurrent > 0) {
                currentSamples.add(normalizedCurrent)
                while (currentSamples.size > MAX_CURRENT_SAMPLES) {
                    currentSamples.poll()
                }
            }
        }

        // Learn per-percent charge time when level increases
        if (level > lastLevel && lastLevel > 0) {
            val elapsedSeconds = (now - lastLevelChangeTime) / 1000.0
            val percentGained = level - lastLevel
            if (percentGained > 0 && elapsedSeconds > 0) {
                val secsPerPercent = elapsedSeconds / percentGained
                // Discard absurd values (more than 1 hour per percent = extremely slow)
                if (secsPerPercent in 1.0..3600.0) {
                    sessionRateSecs = if (sessionRateSecs > 0) {
                        0.4 * secsPerPercent + 0.6 * sessionRateSecs
                    } else {
                        secsPerPercent
                    }
                    learnPercentTime(lastPlugType, level, secsPerPercent)
                    saveLearnedTimes()
                }
            }
            lastLevelChangeTime = now
        }
        lastLevel = level
        lastPlugType = plugged

        // Throttle estimate updates to ~30 seconds
        if (now - lastEstimateUpdate < UPDATE_THROTTLE_MS) {
            return lastEstimate?.let { ChargeEstimate(it, true) }
        }

        val estimate = computeEstimate(level, plugged, chargeCounter)
        lastEstimateUpdate = now
        lastEstimate = estimate
        lastEstimatedMinutes = estimate ?: -1
        return estimate?.let { ChargeEstimate(it, false) }
    }

    /**
     * Get the current estimate without updating (for UI).
     */
    fun getCurrentEstimate(): Int? = lastEstimatedMinutes.takeIf { it > 0 }

    /**
     * Compute the remaining charge time in minutes.
     * Returns null if not charging or estimate not available.
     */
    private fun computeEstimate(level: Int, plugged: Int, chargeCounter: Long): Int? {
        if (level <= 0 || level >= 100) return null

        // 1. Try BatteryManager API (API 28+)
        if (Build.VERSION.SDK_INT >= 28) {
            val bmEstimate = runCatching { batteryManager?.computeChargeTimeRemaining() }.getOrNull()
            if (bmEstimate != null && bmEstimate > 0 && bmEstimate < MAX_PLAUSIBLE_MS) {
                Log.d("ChargeTimeEstimator", "BatteryManager estimate: ${bmEstimate}ms")
                return (bmEstimate / 60_000L).coerceAtLeast(1L).toInt()
            }
            Log.d("ChargeTimeEstimator", "BatteryManager returned implausible value: $bmEstimate")
        }

        // 2. Learned per-percent times (from current and previous sessions)
        val learned = learnedTimes[plugged]
        if (learned != null) {
            var totalSeconds = 0.0
            var missingPercent = 0
            for (p in (level + 1)..100) {
                val t = learned.getOrNull(p) ?: 0.0
                if (t > 0) totalSeconds += t else missingPercent++
            }
            if (totalSeconds > 0) {
                val fillRate = currentFallbackRateSecs(level, chargeCounter)
                    ?: sessionRateSecs.takeIf { it > 0 }
                    ?: defaultRateSecs(plugged)
                totalSeconds += missingPercent * fillRate
                return toMinutes(totalSeconds)
            }
        }

        // 3. Rate observed during this session
        if (sessionRateSecs > 0) {
            return toMinutes((100 - level) * sessionRateSecs)
        }

        // 4. Current + charge counter
        currentFallbackRateSecs(level, chargeCounter)?.let { rate ->
            return toMinutes((100 - level) * rate)
        }

        // 5. Typical rate for this power source, so an estimate exists right after plugging in
        return toMinutes(remainingSecondsFromRate(level, defaultRateSecs(plugged), applySlowdown = true))
    }

    /** Seconds left for the remaining percents at [rateSecs], optionally slowing down above 80%. */
    private fun remainingSecondsFromRate(level: Int, rateSecs: Double, applySlowdown: Boolean): Double {
        var total = 0.0
        for (p in (level + 1)..100) {
            total += rateSecs * if (applySlowdown) slowdownFactors[p] else 1.0
        }
        return total
    }

    /**
     * Seconds per percent derived from current and charge counter.
     * Returns null when the device does not expose those properties.
     */
    private fun currentFallbackRateSecs(level: Int, chargeCounter: Long): Double? {
        if (level < MIN_LEVEL_FOR_CAPACITY || chargeCounter <= 0 || chargeCounter == Long.MIN_VALUE) return null
        val avgCurrent = currentSamples.averageOrNull() ?: return null
        if (avgCurrent <= 0) return null
        // chargeCounter is in µAh, current in µA
        val estimatedFullCapacity = chargeCounter.toDouble() * 100.0 / level
        val remainingCapacity = estimatedFullCapacity - chargeCounter
        if (remainingCapacity <= 0) return null
        val remainingSeconds = remainingCapacity / avgCurrent * 3600.0
        return remainingSeconds / (100 - level).coerceAtLeast(1)
    }

    /** Typical seconds per percent by power source, used before real data is available. */
    private fun defaultRateSecs(plugged: Int): Double = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> 36.0
        BatteryManager.BATTERY_PLUGGED_USB -> 72.0
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> 120.0
        else -> 60.0
    }

    private fun toMinutes(seconds: Double): Int? {
        if (seconds <= 0 || seconds.isNaN() || seconds.isInfinite()) return null
        val minutes = (seconds / 60.0).roundToLong()
        if (minutes <= 0) return applyHysteresis(1)
        if (minutes >= 24 * 60) return null
        return applyHysteresis(minutes.toInt())
    }

    /**
     * Apply hysteresis to prevent the displayed value from jumping up/down.
     * Round to nearest minute, only update if change >= 2 minutes.
     */
    private fun applyHysteresis(newMinutes: Int): Int {
        val rounded = newMinutes.coerceAtLeast(1)
        val last = lastEstimatedMinutes
        if (last > 0 && abs(rounded - last) < 2) {
            return last
        }
        return rounded
    }

    /**
     * Normalize current reading to µA.
     * Heuristic: if absolute value < 20000, assume mA, multiply by 1000.
     * Current can be negative during charging, use absolute value.
     */
    private fun normalizeCurrent(raw: Long): Long {
        // Discard special values (getLongProperty returns Integer.MIN_VALUE when unsupported)
        if (raw == 0L || raw == Long.MIN_VALUE || raw == Integer.MIN_VALUE.toLong()) {
            return 0
        }
        val absValue = abs(raw)
        // Heuristic: values < 20000 are likely in mA, convert to µA
        return if (absValue < 20000) absValue * 1000 else absValue
    }

    /**
     * Learn the time per percent for a given plug type.
     * Uses exponential moving average: new = 0.3 * observed + 0.7 * old
     */
    private fun learnPercentTime(plugType: Int, percent: Int, secsPerPercent: Double) {
        if (percent !in 1..100) return
        val array = learnedTimes.getOrPut(plugType) { DoubleArray(101) }
        val old = array[percent]
        val updated = if (old > 0) 0.3 * secsPerPercent + 0.7 * old else secsPerPercent
        array[percent] = updated
        learnedTimes[plugType] = array
    }

    private fun loadLearnedTimes() {
        val jsonString = prefs.getString("learned_times", null) ?: return
        try {
            val parsed = Json.decodeFromString<Map<String, List<Double>>>(jsonString)
            learnedTimes.clear()
            parsed.forEach { (k, v) ->
                learnedTimes[k.toInt()] = v.toDoubleArray()
            }
        } catch (e: Exception) {
            Log.w("ChargeTimeEstimator", "Failed to parse learned times", e)
        }
    }

    private fun saveLearnedTimes() {
        val serializable = learnedTimes.entries.associate { (k, v) -> k.toString() to v.toList() }
        val json = Json.encodeToString(serializable)
        prefs.edit().putString("learned_times", json).apply()
    }

    /**
     * Result of a charge time estimation.
     * @param minutes Estimated minutes remaining to full charge
     * @param cached Whether this is a cached value (not freshly computed)
     */
    data class ChargeEstimate(val minutes: Int, val cached: Boolean)
}

/**
 * Extension function to get average of a queue of longs.
 */
private fun ConcurrentLinkedQueue<Long>.averageOrNull(): Double? {
    if (isEmpty()) return null
    var sum = 0L
    var count = 0
    iterator().forEach {
        sum += it
        count++
    }
    return if (count > 0) sum.toDouble() / count else null
}
