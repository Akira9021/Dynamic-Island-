package com.akira.island.core

import org.junit.Assert.*
import org.junit.Test

class GlassMotionTest {
    @Test fun screenAndCameraRotationsKeepLightInIslandCoordinates() {
        val portrait = glassTiltFromGravity(4.905f, -2.4525f, 0, 0)
        assertEquals(GlassTilt(0.5f, -0.25f), portrait)
        assertEquals(GlassTilt(-0.25f, -0.5f), glassTiltFromGravity(4.905f, -2.4525f, 1, 0))
        assertEquals(GlassTilt(-0.5f, 0.25f), glassTiltFromGravity(4.905f, -2.4525f, 2, 0))
        assertEquals(GlassTilt(0.25f, 0.5f), glassTiltFromGravity(4.905f, -2.4525f, 3, 0))
        assertEquals(portrait, glassTiltFromGravity(4.905f, -2.4525f, 1, -90))
        assertEquals(portrait, glassTiltFromGravity(4.905f, -2.4525f, 3, 90))
    }

    @Test fun invalidOrExtremeSensorValuesCannotPoisonTheRenderer() {
        assertEquals(GlassTilt.Zero, glassTiltFromGravity(Float.NaN, Float.POSITIVE_INFINITY, 0, 0))
        assertEquals(GlassTilt(1f, -1f), glassTiltFromGravity(100f, -100f, 0, 0))
        val filter = GlassTiltFilter()
        val result = filter.update(GlassTilt(Float.NaN, Float.NEGATIVE_INFINITY), 1_000_000_000L)
        assertEquals(GlassTilt.Zero, result)
    }

    @Test fun smoothingDoesNotDependOnSensorSampleRate() {
        fun sample(rate: Int): GlassTilt {
            val filter = GlassTiltFilter()
            filter.update(GlassTilt.Zero, 1_000_000_000L)
            repeat(rate) { n -> filter.update(GlassTilt(1f, -0.5f), 1_000_000_000L + (n + 1L) * 1_000_000_000L / rate) }
            return filter.value
        }
        val slow = sample(30)
        val fast = sample(90)
        assertEquals(slow.x, fast.x, 0.00001f)
        assertEquals(slow.y, fast.y, 0.00001f)
        assertTrue(slow.x in 0.99f..1f)
    }

    @Test fun aStoppedSensorResetsBothAxesWithoutAnOvershoot() {
        val filter = GlassTiltFilter()
        repeat(40) { n ->
            val next = filter.update(GlassTilt(1f, -1f), 1_000_000_000L + n * 16_666_666L)
            assertTrue(next.x in 0f..1f)
            assertTrue(next.y in -1f..0f)
        }
        filter.reset()
        assertEquals(GlassTilt.Zero, filter.value)
        val next = filter.update(GlassTilt(-1f, 1f), 50_000_000_000L)
        assertTrue(next.x in -0.2f..0f)
        assertTrue(next.y in 0f..0.2f)
    }

    @Test fun aSheenStartsAndFinishesWithoutAFlash() {
        listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY).forEach { assertEquals(0f, glassSheenStrength(it), 0f) }
        assertEquals(1f, glassSheenStrength(0.5f), 0.00001f)
        assertEquals(glassSheenStrength(0.25f), glassSheenStrength(0.75f), 0.00001f)
    }
}
