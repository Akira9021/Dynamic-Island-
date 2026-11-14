package com.akira.island.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class HsvColorSelectionTest {
    @Test fun primariesAndGreyRemainTheirExactColors() {
        for (argb in listOf(0xFFFF0000L, 0xFF00FF00L, 0xFF0000FFL, 0xFFFFFFFFL, 0xFF000000L, 0xFF808080L))
            assertEquals(argb, HsvColorSelection.fromArgb(argb).toArgb())
    }
    @Test fun alphaAndArbitraryChosenColorsSurviveTheRoundTrip() {
        val random = Random(170)
        repeat(1024) {
            val argb = random.nextInt().toLong() and 0xFFFFFFFFL
            assertEquals(argb, HsvColorSelection.fromArgb(argb).toArgb())
        }
    }
    @Test fun hueWrapAndInvalidSliderInputCannotProduceInvalidArgb() {
        assertEquals(0xFFFF0000L, HsvColorSelection(360f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FFL, HsvColorSelection(-120f, 1f, 1f).toArgb())
        assertEquals(0xFFFFFFFFL, HsvColorSelection(Float.NaN, -1f, 2f, 999).toArgb())
        assertEquals(0L, HsvColorSelection(0f, Float.NaN, Float.NaN, -1).toArgb())
    }
}
