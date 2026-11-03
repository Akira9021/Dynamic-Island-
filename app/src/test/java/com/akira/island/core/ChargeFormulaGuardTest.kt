package com.akira.island.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the charge-counter sanity checks.
 * No Android framework dependency.
 */
class ChargeFormulaGuardTest {

    @Test
    fun `full capacity is derived from counter and level`() {
        // 2.000.000 µAh at 50% -> 4.000.000 µAh (4.000 mAh).
        assertEquals(4_000_000L, ChargeFormulaGuard.fullCapacityUah(50, 2_000_000L))
        assertTrue(ChargeFormulaGuard.isPlausibleCapacity(4_000_000L))
    }

    @Test
    fun `large phone batteries remain plausible`() {
        // 6.000 mAh battery at 50%.
        val capacity = ChargeFormulaGuard.fullCapacityUah(50, 3_000_000L)
        assertEquals(6_000_000L, capacity)
        assertTrue(ChargeFormulaGuard.isPlausibleCapacity(capacity))
    }

    @Test
    fun `implausible capacities are rejected`() {
        assertFalse(ChargeFormulaGuard.isPlausibleCapacity(500_000L)) // 500 mAh
        assertFalse(ChargeFormulaGuard.isPlausibleCapacity(15_000_000L)) // 15.000 mAh
        assertFalse(ChargeFormulaGuard.isPlausibleCapacity(null))
    }

    @Test
    fun `vivo counter derives 8 Ah which is still in range but drops while charging`() {
        // Real log from the Vivo V2050: 1.975.001 µAh at 24% -> ~8.229 mAh.
        val capacity = ChargeFormulaGuard.fullCapacityUah(24, 1_975_001L)
        assertEquals(8_229_170L, capacity)
        // 8.2 Ah is inside 1.5-12 Ah, so the range alone cannot catch it.
        assertTrue(ChargeFormulaGuard.isPlausibleCapacity(capacity))
        // The hard check is the counter falling while the level stays/rises.
        assertTrue(ChargeFormulaGuard.counterDroppedWhileCharging(1_975_001L, 1_972_001L, 24, 24))
        assertTrue(ChargeFormulaGuard.counterDroppedWhileCharging(1_975_001L, 1_960_001L, 25, 24))
    }

    @Test
    fun `rising counter is accepted`() {
        assertFalse(ChargeFormulaGuard.counterDroppedWhileCharging(1_975_001L, 1_980_000L, 24, 24))
    }

    @Test
    fun `counter is trusted only after a rise above noise`() {
        // A real increase of 80.000 µAh on a 1.3 Ah counter is a real rise.
        assertTrue(ChargeFormulaGuard.counterRoseAboveNoise(1_319_001L, 1_400_000L))
        // 1.000 µAh is below the noise tolerance.
        assertFalse(ChargeFormulaGuard.counterRoseAboveNoise(2_000_000L, 2_001_000L))
        // A falling counter is never a rise: the Vivo case stays untrusted.
        assertFalse(ChargeFormulaGuard.counterRoseAboveNoise(1_975_001L, 1_972_001L))
        assertFalse(ChargeFormulaGuard.counterRoseAboveNoise(0L, 1_000_000L))
    }

    @Test
    fun `small counter noise is not treated as a drop`() {
        // 1.000 µAh of noise on a 2 Ah counter is below the tolerance.
        assertFalse(ChargeFormulaGuard.counterDroppedWhileCharging(2_000_000L, 1_999_000L, 50, 50))
    }

    @Test
    fun `counter falling during discharge is not a charging problem`() {
        assertFalse(ChargeFormulaGuard.counterDroppedWhileCharging(2_000_000L, 1_900_000L, 49, 50))
    }

    @Test
    fun `missing or invalid readings are rejected`() {
        assertNull(ChargeFormulaGuard.fullCapacityUah(0, 2_000_000L))
        assertNull(ChargeFormulaGuard.fullCapacityUah(101, 2_000_000L))
        assertNull(ChargeFormulaGuard.fullCapacityUah(50, 0L))
        assertNull(ChargeFormulaGuard.fullCapacityUah(50, Long.MAX_VALUE))
        assertFalse(ChargeFormulaGuard.counterDroppedWhileCharging(0L, 1_000_000L, 50, 50))
    }
}
