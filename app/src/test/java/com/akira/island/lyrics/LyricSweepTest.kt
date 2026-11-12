package com.akira.island.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricSweepTest {
    @Test fun `wave follows real line interval and keeps the earlier completion envelope`() {
        assertEquals(0f, lyricSweepFraction(10_000, 10_000, 20_000), 0f)
        assertEquals(0.5f, lyricSweepFraction(14_250, 10_000, 20_000), 0.00001f)
        assertEquals(1f, lyricSweepFraction(18_500, 10_000, 20_000), 0f)
    }

    @Test fun `position is clamped before and after a line`() {
        assertEquals(0f, lyricSweepFraction(-1, 10_000, 20_000), 0f)
        assertEquals(1f, lyricSweepFraction(Long.MAX_VALUE, 10_000, 20_000), 0f)
    }

    @Test fun `unknown or duplicate end does not invent a four second timing`() {
        assertEquals(1f, lyricSweepFraction(12_000, 10_000, 0), 0f)
        assertEquals(1f, lyricSweepFraction(12_000, 10_000, 10_000), 0f)
    }

    @Test fun `small time steps change the fraction without character quantization`() {
        val a = lyricSweepFraction(14_250, 10_000, 20_000)
        val b = lyricSweepFraction(14_270, 10_000, 20_000)
        assertTrue(b > a)
        assertTrue(b - a < 0.003f)
    }

    @Test fun `fraction stays continuous and monotone over the line`() {
        var previous = 0f
        for (position in 10_000L..18_500L step 5) {
            val current = lyricSweepFraction(position, 10_000, 20_000)
            assertTrue(current >= previous && current - previous < 0.001f)
            previous = current
        }
    }

    @Test fun `ordinary playing updates interpolate`() {
        assertFalse(animateLyricSweep(12_000, 12_000, true))
        assertTrue(animateLyricSweep(12_000, 12_100, true))
        assertTrue(animateLyricSweep(12_000, 12_500, true))
    }

    @Test fun `pause reverse seek and large wake jump snap to the player`() {
        assertFalse(animateLyricSweep(12_000, 12_100, false))
        assertFalse(animateLyricSweep(12_000, 11_999, true))
        assertFalse(animateLyricSweep(12_000, 18_000, true))
    }

    @Test fun `large timestamp origin does not lose a small real interval`() {
        val start = Long.MAX_VALUE - 10_000
        assertEquals(0.5f, lyricSweepFraction(start + 4_250, start, Long.MAX_VALUE), 0.00001f)
    }
}
