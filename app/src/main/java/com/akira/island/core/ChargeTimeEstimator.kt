package com.akira.island.core

import android.os.Build
import android.os.BatteryManager
import android.util.Log
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

/**
 * Estimates remaining charge time using multiple strategies:
 * 1. BatteryManager.computeChargeTimeRemaining() on API 28+ (if plausible)
 * 2. Current + charge counter fallback with moving average
 * 3. Learned per-percent charging times by power source type (AC/USB/Wireless)
 * 
 * This class is pure Kotlin with no Android framework dependencies in its core logic,
 * making it easily unit-testable.
 */
class ChargeTimeEstimator private constructor(
    private val context: android.content.Context,
) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val prefs = context.getSharedPreferences("charge_time_estimator", android.content.Context.MODE_PRIVATE)
    
    // Moving average window for current readings (~60 seconds at 10s intervals = 6 samples)
    private val currentSamples = ConcurrentLinkedQueue<Long>()
    private val MAX_CURRENT_SAMPLES = 6
    
    // Exponential moving average for per-percent charge times by plug type
    // Key: plug type (BatteryManager.BATTERY_PLUGGED_*), Value: array of seconds per percent (1-100)
    private val learnedTimes = mutableMapOf<Int, DoubleArray>()
    
    // Last seen battery level and plug type for learning
    private var lastLevel = -1
    private var lastPlugType = 0
    private var lastChargeStartTime = 0L
    private var isCharging = false
    
    // Constants
    private val MAX_PLAUSIBLE_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val CURRENT_SAMPLE_INTERVAL_MS = 10_000L // 10 seconds
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
    private var lastEstimatedMinutes = -1
    
    companion object {
        @Volatile private var INSTANCE: ChargeTimeEstimator? = null
        
        fun getInstance(context: android.content.Context): ChargeTimeEstimator {
            val existing = INSTANCE
            if (existing != null) return existing
            return synchronized(this) {
                INSTANCE ?: ChargeTimeEstimator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Call this when ACTION_BATTERY_CHANGED is received.
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
            // Charging started
            lastChargeStartTime = now
            lastLevel = level
            lastPlugType = plugged
            currentSamples.clear()
            loadLearnedTimes()
        } else if (!isCharging && wasCharging) {
            // Charging stopped - save learned data
            saveLearnedTimes()
            lastLevel = -1
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
            val elapsedSeconds = (now - lastChargeStartTime) / 1000
            val percentGained = level - lastLevel
            if (percentGained > 0 && elapsedSeconds > 0) {
                val secsPerPercent = elapsedSeconds.toDouble() / percentGained
                // Discard absurd values (more than 1 hour per percent = extremely slow)
                if (secsPerPercent < 3600) {
                    learnPercentTime(lastPlugType, level, secsPerPercent)
                }
            }
            lastChargeStartTime = now
        }
        lastLevel = level
        lastPlugType = plugged
        
        // Throttle estimate updates to ~30 seconds
        if (now - lastEstimateUpdate < UPDATE_THROTTLE_MS) {
            return lastEstimate?.let { ChargeEstimate(it, true) }
        }
        
        val estimate = computeEstimate(level, plugged, chargeCounter)
        lastEstimateUpdate = now
        lastEstimatedMinutes = estimate ?: -1
        return estimate?.let { ChargeEstimate(it, false) }
    }
    
    private var lastEstimate: Int? = null
    
    /**
     * Get the current estimate without updating (for UI).
     */
    fun getCurrentEstimate(): Int? = lastEstimatedMinutes.takeIf { it > 0 }
    
    /**
     * Compute the remaining charge time in minutes.
     * Returns null if not charging or estimate not available.
     */
    private fun computeEstimate(level: Int, plugged: Int, chargeCounter: Long): Int? {
        // 1. Try BatteryManager API (API 28+)
        if (Build.VERSION.SDK_INT >= 28) {
            val bmEstimate = batteryManager?.computeChargeTimeRemaining()
            if (bmEstimate != null && bmEstimate > 0 && bmEstimate < MAX_PLAUSIBLE_MS) {
                Log.d("ChargeTimeEstimator", "BatteryManager estimate: ${bmEstimate}ms")
                return (bmEstimate / 60000).coerceAtLeast(1).toInt()
            }
            Log.d("ChargeTimeEstimator", "BatteryManager returned implausible value: $bmEstimate")
        }
        
        // 2. Fallback: current + charge counter with moving average
        val avgCurrent = currentSamples.averageOrNull()
        if (avgCurrent != null && avgCurrent > 0) {
            // Estimate full capacity from charge counter
            // chargeCounter is in µAh, level is 0-100
            if (level >= MIN_LEVEL_FOR_CAPACITY && chargeCounter > 0 && chargeCounter != Long.MIN_VALUE) {
                val estimatedFullCapacity = (chargeCounter * 100L) / level
                val remainingCapacity = estimatedFullCapacity - chargeCounter
                if (remainingCapacity > 0) {
                    val hours = remainingCapacity.toDouble() / avgCurrent
                    val minutes = (hours * 60).toLong()
                    if (minutes > 0 && minutes < 24 * 60) {
                        // Apply slowdown factor for levels above 80%
                        val factor = slowdownFactors[level.coerceIn(0, 100)]
                        val adjusted = (minutes * factor).toLong()
                        return applyHysteresis(adjusted.coerceAtLeast(1).toInt())
                    }
                }
            }
        }
        
        // 3. Learned per-percent times
        val learned = learnedTimes[plugged]
        if (learned != null) {
            var totalSeconds = 0.0
            var hasData = false
            for (p in (level + 1)..100) {
                val t = learned[p]
                if (t > 0) {
                    totalSeconds += t
                    hasData = true
                } else {
                    // No learned data for this percent - use fallback with slowdown
                    val fallbackEstimate = estimateFromCurrentFallback(level, plugged, chargeCounter)
                    if (fallbackEstimate != null) {
                        totalSeconds += fallbackEstimate * 60.0
                        hasData = true
                    }
                    break
                }
            }
            if (hasData && totalSeconds > 0) {
                val minutes = (totalSeconds / 60).toLong()
                return applyHysteresis(minutes.coerceAtLeast(1).toInt())
            }
        }
        
        // 4. Pure fallback with slowdown factor
        return estimateFromCurrentFallback(level, plugged, chargeCounter)?.let { applyHysteresis(it) }
    }
    
    private fun estimateFromCurrentFallback(level: Int, plugged: Int, chargeCounter: Long): Int? {
        val avgCurrent = currentSamples.averageOrNull()
        if (avgCurrent != null && avgCurrent > 0 && level >= MIN_LEVEL_FOR_CAPACITY && chargeCounter > 0) {
            val estimatedFullCapacity = (chargeCounter * 100L) / level
            val remainingCapacity = estimatedFullCapacity - chargeCounter
            if (remainingCapacity > 0) {
                val hours = remainingCapacity.toDouble() / avgCurrent
                val minutes = (hours * 60).toLong()
                if (minutes > 0 && minutes < 24 * 60) {
                    val factor = slowdownFactors[level.coerceIn(0, 100)]
                    return (minutes * factor).toLong().coerceAtLeast(1).toInt()
                }
            }
        }
        return null
    }
    
    /**
     * Apply hysteresis to prevent the displayed value from jumping up/down.
     * Round to nearest minute, only update if change >= 2 minutes.
     */
    private fun applyHysteresis(newMinutes: Int): Int {
        // Round to nearest minute
        val rounded = (newMinutes + 0.5).toInt()
        // Only update if change >= 2 minutes
        val last = lastEstimatedMinutes
        if (last > 0 && abs(rounded - last) < 2) {
            return last
        }
        return rounded.coerceAtLeast(1)
    }
    
    /**
     * Normalize current reading to µA.
     * Heuristic: if absolute value < 20000, assume mA, multiply by 1000.
     * Current can be negative during charging, use absolute value.
     */
    private fun normalizeCurrent(raw: Long): Long {
        val absValue = kotlin.math.abs(raw)
        // Discard special values
        if (absValue == 0L || absValue == Long.MIN_VALUE.toLong() || absValue == Integer.MIN_VALUE.toLong()) {
            return 0
        }
        // Heuristic: values < 20000 are likely in mA, convert to µA
        return if (absValue < 20000) absValue * 1000 else absValue
    }
    
    /**
     * Learn the time per percent for a given plug type.
     * Uses exponential moving average: new = 0.3 * observed + 0.7 * old
     */
    private fun learnPercentTime(plugType: Int, percent: Int, secsPerPercent: Double) {
        val array = learnedTimes.getOrPut(plugType) { DoubleArray(101) }
        val old = array[percent]
        val updated = if (old > 0) 0.3 * secsPerPercent + 0.7 * old else secsPerPercent
        array[percent] = updated
        learnedTimes[plugType] = array
    }
    
    private fun loadLearnedTimes() {
        val jsonString = prefs.getString("learned_times", null)
        if (jsonString != null) {
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
    }
    
    private fun saveLearnedTimes() {
        val serializable = learnedTimes.entries.associate { (k, v) -> k.toString() to v.toList() }
        val json = Json.encodeToString<Map<String, List<Double>>>(serializable)
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