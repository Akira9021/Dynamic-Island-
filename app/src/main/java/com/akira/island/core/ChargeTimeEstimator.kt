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
    /** Anything slower than 10 minutes per percent is useless data and is discarded. */
    private val MAX_SECS_PER_PERCENT = 600.0

    // Raw readings of the last calculation, only used for the debug log
    private var lastRawCurrent = Long.MIN_VALUE
    private var lastNormalizedCurrent = 0L
    private var lastCurrentUnit = "unsupported"
    private var lastRawChargeCounter = Long.MIN_VALUE
    private var lastSystemRaw = "n/a"

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
        // Same universal rule as SystemEvents: plugged and not explicitly
        // discharging/not-charging (some OEMs report BATTERY_STATUS_UNKNOWN first).
        isCharging = plugged != 0 &&
            status != BatteryManager.BATTERY_STATUS_DISCHARGING &&
            status != BatteryManager.BATTERY_STATUS_NOT_CHARGING

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
            logHistory()
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

        // Keep the raw values for the debug log and sample the normalized current
        lastRawCurrent = currentNow
        lastNormalizedCurrent = normalizeCurrent(currentNow)
        lastCurrentUnit = currentUnitOf(currentNow)
        lastRawChargeCounter = chargeCounter

        // Sample current for moving average (only while charging and visible)
        if (lastNormalizedCurrent > 0) {
            currentSamples.add(lastNormalizedCurrent)
            while (currentSamples.size > MAX_CURRENT_SAMPLES) {
                currentSamples.poll()
            }
        }

        // Learn per-percent charge time when level increases
        if (level > lastLevel && lastLevel > 0) {
            val elapsedSeconds = (now - lastLevelChangeTime) / 1000.0
            val percentGained = level - lastLevel
            if (percentGained > 0 && elapsedSeconds > 0) {
                val secsPerPercent = elapsedSeconds / percentGained
                // Discard absurd values (more than 10 minutes per percent = useless)
                if (secsPerPercent in 1.0..MAX_SECS_PER_PERCENT) {
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
        lastSystemRaw = "n/a"
        if (level <= 0 || level >= 100) {
            return logResult("none", 0.0, "")
        }

        // 1. Try BatteryManager API (API 28+)
        if (Build.VERSION.SDK_INT >= 28) {
            val bmEstimate = runCatching { batteryManager?.computeChargeTimeRemaining() }.getOrNull()
            lastSystemRaw = bmEstimate?.toString() ?: "null"
            if (bmEstimate != null && bmEstimate > 0 && bmEstimate < MAX_PLAUSIBLE_MS) {
                return logResult("system", bmEstimate / 1000.0, "slowdownMode=none")
            }
        }

        // 2. Learned per-percent times (from current and previous sessions)
        val learned = learnedTimes[plugged]
        if (learned != null) {
            var knownSeconds = 0.0
            var knownPercent = 0
            var missingPercent = 0
            for (p in (level + 1)..100) {
                val t = learned.getOrNull(p) ?: 0.0
                if (t > 0) {
                    knownSeconds += t
                    knownPercent++
                } else {
                    missingPercent++
                }
            }
            if (knownSeconds > 0) {
                val fillRate = currentFallbackRateSecs(level, chargeCounter)
                    ?: sessionRateSecs.takeIf { it > 0 }
                    ?: defaultRateSecs(plugged)
                val totalSeconds = knownSeconds + missingPercent * fillRate
                return logResult(
                    "history", totalSeconds,
                    "knownPct=$knownPercent missingPct=$missingPercent " +
                        "fillSecsPerPct=${fmt(fillRate)} historySumSecs=${fmt(knownSeconds)} slowdownMode=none",
                )
            }
        }

        // 3. Rate observed during this session
        if (sessionRateSecs > 0) {
            return logResult(
                "session", (100 - level) * sessionRateSecs,
                "sessionSecsPerPct=${fmt(sessionRateSecs)} slowdownMode=none",
            )
        }

        // 4. Current + charge counter
        currentFallbackRateSecs(level, chargeCounter)?.let { rate ->
            return logResult("formula", (100 - level) * rate, "secsPerPct=${fmt(rate)} slowdownMode=none")
        }

        // 5. Typical rate for this power source, so an estimate exists right after plugging in
        val rate = defaultRateSecs(plugged)
        return logResult(
            "default", remainingSecondsFromRate(level, rate, applySlowdown = true),
            "secsPerPct=${fmt(rate)} slowdownMode=perPercent",
        )
    }

    /** Writes one structured debug line per calculation and returns the (hysteresis-adjusted) minutes. */
    private fun logResult(branch: String, seconds: Double, extra: String): Int? {
        val minutes = toMinutes(seconds)
        val fullCapacity = estimatedFullCapacity(lastLevel, lastRawChargeCounter)
        val slowdown = slowdownFactors[lastLevel.coerceIn(0, 100)]
        val line = StringBuilder()
            .append("branch=").append(branch)
            .append(" level=").append(lastLevel)
            .append(" plugged=").append(lastPlugType)
            .append(" bmRaw=").append(lastSystemRaw)
            .append(" currentRaw=").append(lastRawCurrent)
            .append(" currentNorm=").append(lastNormalizedCurrent)
            .append(" currentUnit=").append(lastCurrentUnit)
            .append(" counterRaw=").append(lastRawChargeCounter)
            .append(" counterNorm=").append(lastRawChargeCounter)
            .append(" counterUnit=uAh")
            .append(" fullCapacity=").append(fullCapacity?.roundToLong() ?: -1)
            .append(" slowdown=").append(fmt(slowdown))
            .append(" finalSecs=").append(seconds.roundToLong())
            .append(" finalMin=").append(minutes ?: -1)
        if (extra.isNotEmpty()) line.append(' ').append(extra)
        ChargeEtaLog.log(context, line.toString())
        return minutes
    }

    /** Writes the whole learned table at the start of every charge. */
    private fun logHistory() {
        if (learnedTimes.isEmpty()) {
            ChargeEtaLog.log(context, "history empty")
            return
        }
        learnedTimes.forEach { (plug, values) ->
            val entries = (1..100).mapNotNull { p ->
                val v = values.getOrNull(p) ?: 0.0
                if (v > 0) "$p=${fmt(v)}" else null
            }
            ChargeEtaLog.log(context, "history plug=$plug secsPerPct={${entries.joinToString(",")}}")
        }
    }

    /** Round to 1 decimal, enough for the log and locale-independent. */
    private fun fmt(value: Double): String = ((value * 10).roundToLong() / 10.0).toString()

    /** Seconds left for the remaining percents at [rateSecs], optionally slowing down above 80%. */
    private fun remainingSecondsFromRate(level: Int, rateSecs: Double, applySlowdown: Boolean): Double {
        var total = 0.0
        for (p in (level + 1)..100) {
            total += rateSecs * if (applySlowdown) slowdownFactors[p] else 1.0
        }
        return total
    }

    /** Full capacity in µAh derived from the charge counter, when the device exposes it. */
    private fun estimatedFullCapacity(level: Int, chargeCounter: Long): Double? {
        if (level < MIN_LEVEL_FOR_CAPACITY || chargeCounter <= 0 || chargeCounter == Long.MIN_VALUE) return null
        return chargeCounter.toDouble() * 100.0 / level
    }

    /**
     * Seconds per percent derived from current and charge counter.
     * Returns null when the device does not expose those properties.
     */
    private fun currentFallbackRateSecs(level: Int, chargeCounter: Long): Double? {
        val fullCapacity = estimatedFullCapacity(level, chargeCounter) ?: return null
        val avgCurrent = currentSamples.averageOrNull() ?: return null
        if (avgCurrent <= 0) return null
        // chargeCounter is in µAh, current in µA
        val remainingCapacity = fullCapacity - chargeCounter
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

    /** Unit implied by the raw current reading, mirroring [normalizeCurrent]. */
    private fun currentUnitOf(raw: Long): String = when {
        raw == 0L || raw == Long.MIN_VALUE || raw == Integer.MIN_VALUE.toLong() -> "unsupported"
        abs(raw) < 20000 -> "mA"
        else -> "uA"
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
        array[percent] = updated.coerceAtMost(MAX_SECS_PER_PERCENT)
        learnedTimes[plugType] = array
    }

    private fun loadLearnedTimes() {
        learnedTimes.clear()
        val jsonString = prefs.getString("learned_times", null) ?: return
        try {
            val parsed = Json.decodeFromString<Map<String, List<Double>>>(jsonString)
            var dropped = false
            parsed.forEach { (k, v) ->
                val array = v.toDoubleArray()
                for (i in array.indices) {
                    if (array[i] > MAX_SECS_PER_PERCENT) {
                        array[i] = 0.0
                        dropped = true
                    }
                }
                learnedTimes[k.toInt()] = array
            }
            if (dropped) {
                ChargeEtaLog.log(context, "history cleanup: dropped values > ${MAX_SECS_PER_PERCENT}s per percent")
                saveLearnedTimes()
            }
        } catch (e: Exception) {
            Log.w("ChargeTimeEstimator", "Failed to parse learned times", e)
        }
    }

    /** Debug action: forget everything learned and start from the typical rates again. */
    fun clearLearnedTimes() {
        learnedTimes.clear()
        prefs.edit().remove("learned_times").apply()
        sessionRateSecs = 0.0
        currentSamples.clear()
        lastLevelChangeTime = System.currentTimeMillis()
        lastEstimate = null
        lastEstimatedMinutes = -1
        lastEstimateUpdate = 0L
        ChargeEtaLog.log(context, "history cleared")
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
