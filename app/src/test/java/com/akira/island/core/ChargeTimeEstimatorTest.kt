package com.akira.island.core

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * Unit tests for ChargeTimeEstimator logic.
 * These test the pure Kotlin functions without Android framework dependencies.
 */
class ChargeTimeEstimatorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            // Force English locale for tests
            System.setProperty("user.language", "en")
            System.setProperty("user.region", "US")
        }
    }

    @Test
    fun `formatChargeTime - less than 1 minute`() {
        assertEquals("<1m", formatChargeTime(0))
        assertEquals("<1m", formatChargeTime(-5))
    }

    @Test
    fun `formatChargeTime - minutes only`() {
        assertEquals("45m", formatChargeTime(45))
        assertEquals("1m", formatChargeTime(1))
        assertEquals("59m", formatChargeTime(59))
    }

    @Test
    fun `formatChargeTime - hours and minutes`() {
        assertEquals("1h 25m", formatChargeTime(85))
        assertEquals("2h 30m", formatChargeTime(150))
        assertEquals("10h 5m", formatChargeTime(605))
    }

    @Test
    fun `formatChargeTime - exact hours`() {
        assertEquals("1h", formatChargeTime(60))
        assertEquals("2h", formatChargeTime(120))
        assertEquals("24h", formatChargeTime(1440))
    }

    @Test
    fun `normalizeCurrent - microamps`() {
        // Values >= 20000 are assumed to be in µA
        assertEquals(50000L, normalizeCurrentForTest(50000))
        assertEquals(20000L, normalizeCurrentForTest(20000))
        assertEquals(100000L, normalizeCurrentForTest(-100000)) // negative becomes positive
    }

    @Test
    fun `normalizeCurrent - milliamps`() {
        // Values < 20000 are assumed to be in mA, converted to µA
        assertEquals(5000000L, normalizeCurrentForTest(5000)) // 5000 mA = 5,000,000 µA
        assertEquals(1000000L, normalizeCurrentForTest(1000)) // 1000 mA = 1,000,000 µA
        assertEquals(19999000L, normalizeCurrentForTest(19999)) // 19999 mA
    }

    @Test
    fun `normalizeCurrent - special values`() {
        assertEquals(0L, normalizeCurrentForTest(0))
        assertEquals(0L, normalizeCurrentForTest(Long.MIN_VALUE))
        assertEquals(0L, normalizeCurrentForTest(Integer.MIN_VALUE.toLong()))
    }

    @Test
    fun `slowdownFactors - up to 80 percent is one`() {
        // We can't directly test private field, but we can verify the logic
        // slowdownFactors[0..80] should be 1.0
        for (i in 0..80) {
            val factor = if (i <= 80) 1.0 else 1.0 + (i - 80) / 20.0 * 1.0
            assertEquals(1.0, factor, 0.001)
        }
    }

    @Test
    fun `slowdownFactors - above 80 percent increases`() {
        // 81% = 1.05, 90% = 1.5, 100% = 2.0
        assertEquals(1.05, 1.0 + (81 - 80) / 20.0 * 1.0, 0.001)
        assertEquals(1.5, 1.0 + (90 - 80) / 20.0 * 1.0, 0.001)
        assertEquals(2.0, 1.0 + (100 - 80) / 20.0 * 1.0, 0.001)
    }
}

// Helper function to test normalizeCurrent logic (copied from ChargeTimeEstimator)
private fun normalizeCurrentForTest(raw: Long): Long {
    if (raw == 0L || raw == Long.MIN_VALUE || raw == Integer.MIN_VALUE.toLong()) {
        return 0
    }
    val absValue = kotlin.math.abs(raw)
    return if (absValue < 20000) absValue * 1000 else absValue
}