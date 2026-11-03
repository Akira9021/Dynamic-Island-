package com.akira.island.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure ring logic of the secondary charging bubble.
 * No Android framework dependency.
 */
class ChargeRingMathTest {

    private val minute = 60_000L
    private val start = 1_000_000L

    @Test
    fun `progress is zero right after plug-in`() {
        assertEquals(0f, ChargeRingMath.progress(start, start, 51), 0.0001f)
    }

    @Test
    fun `progress is about half at mid charge`() {
        // 25 minutes elapsed, 25 minutes left.
        val progress = ChargeRingMath.progress(start + 25 * minute, start, 25)
        assertEquals(0.5f, progress, 0.0001f)
    }

    @Test
    fun `progress follows elapsed over elapsed plus remaining`() {
        // 20 minutes elapsed, 30 minutes left -> 20 / 50 = 0.4
        val progress = ChargeRingMath.progress(start + 20 * minute, start, 30)
        assertEquals(0.4f, progress, 0.0001f)
    }

    @Test
    fun `rising estimate never makes the ring go backwards`() {
        val first = ChargeRingMath.progress(start + 20 * minute, start, 30) // 0.4
        // Ten minutes later the estimator suddenly says 45 minutes remain:
        // the raw value would drop to 30 / 75 = 0.4 (same), then to 0.37 with 50.
        val later = ChargeRingMath.progress(start + 30 * minute, start, 50) // 0.375
        assertEquals(0.375f, later, 0.0001f)
        assertEquals(first, ChargeRingMath.advance(first, later), 0.0001f)
    }

    @Test
    fun `ring never goes backwards across many estimates`() {
        var displayed = 0f
        var previousRaw = -1f
        var sawDecrease = false
        val estimates = listOf(51, 53, 40, 60, 27, 35, 20)
        estimates.forEachIndexed { i, minutes ->
            val candidate = ChargeRingMath.progress(start + (i + 1) * 5 * minute, start, minutes)
            if (previousRaw >= 0f && candidate < previousRaw) sawDecrease = true
            previousRaw = candidate
            val next = ChargeRingMath.advance(displayed, candidate)
            assertTrue("ring must not decrease", next >= displayed)
            displayed = next
        }
        assertTrue("the estimate did drop at least once", sawDecrease)
        assertTrue(displayed in 0f..1f)
    }

    @Test
    fun `progress is clamped between zero and one`() {
        assertEquals(0f, ChargeRingMath.progressFromMillis(-1000, 2000), 0.0001f)
        assertEquals(0f, ChargeRingMath.progressFromMillis(1000, 0), 0.0001f)
        assertEquals(1f, ChargeRingMath.advance(1.2f, 0.3f), 0.0001f)
        assertEquals(0f, ChargeRingMath.advance(-0.5f, -0.2f), 0.0001f)
    }

    @Test
    fun `unplug clears the session and replug starts from zero`() {
        // A finished session: started at 10%, ring was at 40%.
        val session = ChargeRingMath.Session(start, 10, 0.4f)
        assertTrue(session.valid)

        // Unplug -> no reference in BatteryInfo.
        val unplugged = ChargeRingMath.Session()
        assertFalse(unplugged.valid)
        assertEquals(0f, ChargeRingMath.progress(start + 30 * minute, unplugged.startElapsedRealtime, 20), 0.0001f)

        // Replug -> new instant and level, ring restarts from zero.
        val replugged = ChargeRingMath.startSession(start + 40 * minute, 35)
        assertEquals(start + 40 * minute, replugged.startElapsedRealtime)
        assertEquals(35, replugged.startLevel)
        assertEquals(0f, replugged.maxProgress, 0.0001f)
        assertEquals(0f, ChargeRingMath.progress(replugged.startElapsedRealtime, replugged.startElapsedRealtime, 30), 0.0001f)
    }

    @Test
    fun `service restart keeps the saved reference and max progress`() {
        // Before the restart: 20 minutes elapsed, 30 left, max 0.4.
        val saved = ChargeRingMath.Session(start, 24, 0.4f)

        // Service restarts, the same SharedPreferences values are restored.
        val restored = ChargeRingMath.Session(saved.startElapsedRealtime, saved.startLevel, saved.maxProgress)
        assertTrue(ChargeRingMath.isValidReference(restored.startElapsedRealtime, start + 25 * minute))

        // One more minute of charging: elapsed counts from the original plug-in.
        val progress = ChargeRingMath.progress(start + 26 * minute, restored.startElapsedRealtime, 29)
        assertEquals(26f / 55f, progress, 0.0001f)
        assertEquals(0.4f, restored.maxProgress, 0.0001f)
        assertEquals(progress, ChargeRingMath.advance(restored.maxProgress, progress), 0.0001f)
    }

    @Test
    fun `saved reference from a previous boot is rejected`() {
        // After a reboot elapsedRealtime is smaller than the saved start.
        assertFalse(ChargeRingMath.isValidReference(start, 500_000L))
        assertEquals(0f, ChargeRingMath.progress(500_000L, start, 30), 0.0001f)
        assertTrue(ChargeRingMath.isValidReference(start, start))
    }

    @Test
    fun `missing or non positive estimate keeps the previous ring value`() {
        assertEquals(0f, ChargeRingMath.progress(start + 10 * minute, start, null), 0.0001f)
        assertEquals(0f, ChargeRingMath.progress(start + 10 * minute, start, 0), 0.0001f)
        assertEquals(0.3f, ChargeRingMath.advance(0.3f, 0f), 0.0001f)
        assertEquals(0L, ChargeRingMath.remainingMs(null))
        assertEquals(0L, ChargeRingMath.remainingMs(-1))
        assertEquals(30 * minute, ChargeRingMath.remainingMs(30))
    }

    @Test
    fun `reference is captured from the first reliable estimate`() {
        assertEquals(48, ChargeRingMath.referenceMinutes(-1, 42, 42, 48))
        assertEquals(-1, ChargeRingMath.referenceMinutes(-1, 42, 42, null))
    }

    @Test
    fun `reference is kept while the level does not change`() {
        // The live estimate changes from 13 to 25 minutes at the same level:
        // the ring must keep the time it was already using.
        assertEquals(13, ChargeRingMath.referenceMinutes(13, 50, 50, 25))
    }

    @Test
    fun `reference refreshes when the level goes up`() {
        assertEquals(20, ChargeRingMath.referenceMinutes(13, 50, 51, 20))
        // No estimate at the new level: keep the previous reference.
        assertEquals(13, ChargeRingMath.referenceMinutes(13, 50, 51, null))
    }

    @Test
    fun `ring keeps the saved reference during a primary to secondary transition`() {
        // 20 minutes elapsed. The live estimate jumps to 25 but the ring was
        // using 13; the saved reference must win, so the value does not change.
        val saved = 13
        val reference = ChargeRingMath.referenceMinutes(saved, 50, 50, 25)
        val progress = ChargeRingMath.progress(start + 20 * minute, start, reference)
        assertEquals(13, reference)
        assertEquals(20f / 33f, progress, 0.0001f)
    }
}
