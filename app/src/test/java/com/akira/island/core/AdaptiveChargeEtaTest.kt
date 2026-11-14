package com.akira.island.core

import org.junit.Assert.*
import org.junit.Test

class AdaptiveChargeEtaTest {
    private fun AdaptiveChargeEta.sample(at: Long, level: Int, system: Long? = null, plug: Int = 1) =
        observe(at, level, true, plug, system)

    @Test fun unknownUntilTwoMeasuredPercentageIntervals() {
        val estimator = AdaptiveChargeEta()
        assertNull(estimator.sample(0, 30))
        assertNull(estimator.sample(60_000, 31))
        assertEquals(ChargeEta(68 * 60_000L, ChargeEtaSource.OBSERVED), estimator.sample(120_000, 32))
    }

    @Test fun platformEstimateAlwaysWinsOverLearnedRate() {
        val estimator = AdaptiveChargeEta()
        estimator.sample(0, 30); estimator.sample(60_000, 31)
        assertEquals(ChargeEta(900_000, ChargeEtaSource.SYSTEM), estimator.sample(120_000, 32, 900_000))
        assertEquals(ChargeEtaSource.OBSERVED, estimator.sample(135_000, 32, -1)?.source)
    }

    @Test fun unpluggingAndChangingChargerNeverReuseOldRates() {
        val estimator = AdaptiveChargeEta()
        estimator.sample(0, 30); estimator.sample(60_000, 31); estimator.sample(120_000, 32)
        assertNull(estimator.observe(125_000, 32, false, 0, 900_000))
        assertNull(estimator.sample(130_000, 32))
        estimator.sample(190_000, 33); estimator.sample(250_000, 34)
        assertNull(estimator.sample(265_000, 34, plug = 4))
    }

    @Test fun screenOffGapAndBackwardMonotonicTimestampInvalidateSession() {
        val estimator = AdaptiveChargeEta()
        estimator.sample(0, 30); estimator.sample(60_000, 31); estimator.sample(120_000, 32)
        assertNull(estimator.sample(10 * 60_000L, 35))
        estimator.sample(11 * 60_000L, 36); estimator.sample(12 * 60_000L, 37)
        assertNull(estimator.sample(1000, 37))
    }

    @Test fun droppingOrJumpingBatteryGaugeIsNotAChargingRate() {
        val estimator = AdaptiveChargeEta()
        estimator.sample(0, 30); estimator.sample(60_000, 31); estimator.sample(120_000, 32)
        assertNull(estimator.sample(135_000, 31))
        assertNull(estimator.sample(150_000, 40))
        assertNull(estimator.sample(151_000, 41))
        assertNull(estimator.sample(152_000, 42))
    }

    @Test fun stalledChargingDoesNotKeepAdvertisingTheOldRate() {
        val estimator = AdaptiveChargeEta()
        estimator.sample(0, 30); estimator.sample(60_000, 31); estimator.sample(120_000, 32)
        var result: ChargeEta? = null
        for (at in 135_000L..315_000L step 15_000L) result = estimator.sample(at, 32)
        assertNull(result)
    }

    @Test fun fullAndUnsupportedGaugesDoNotInventRemainingTime() {
        val estimator = AdaptiveChargeEta()
        for (level in listOf(-1, 100, 101)) assertNull(estimator.sample(0, level, 900_000))
        assertNull(estimator.sample(0, 50, plug = 0))
        assertNull(estimator.sample(0, 50, Long.MAX_VALUE))
    }
}
