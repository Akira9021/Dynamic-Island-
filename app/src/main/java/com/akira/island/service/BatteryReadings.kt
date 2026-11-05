package com.akira.island.service

/** Sanitize optional hardware readings: unsupported sentinels must never become charging data. */
internal object BatteryReadings {
    fun percent(raw: Int, scale: Int): Int =
        if (raw < 0 || scale <= 0) -1 else (raw.toLong() * 100 / scale).coerceIn(0, 100).toInt()

    fun currentMa(microAmps: Int, charging: Boolean): Float? =
        if (charging && microAmps in 1..100_000_000) microAmps / 1000f else null

    fun voltageV(milliVolts: Int): Float? =
        if (milliVolts in 1000..20_000) milliVolts / 1000f else null

    fun remainingMs(value: Long, charging: Boolean): Long? =
        value.takeIf { charging && it in 1..(7L * 24 * 60 * 60 * 1000) }
}
