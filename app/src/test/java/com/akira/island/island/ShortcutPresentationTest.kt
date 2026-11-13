package com.akira.island.island

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ShortcutPresentationTest {
    @Test fun pagesKeepEverySelectedAppAndContactReachable() {
        assertEquals(0, shortcutPageCount(0))
        assertEquals(1, shortcutPageCount(1))
        assertEquals(1, shortcutPageCount(6))
        assertEquals(1, shortcutPageCount(7))
        assertEquals(1, shortcutPageCount(12))
        assertEquals(2, shortcutPageCount(13))
    }

    @Test fun narrowCardsAndLargeFontsUseGrid() {
        assertFalse(useShortcutOrbit(200f, 1f))
        assertTrue(useShortcutOrbit(216f, 1f))
        assertTrue(useShortcutOrbit(269f, 1f))
        assertTrue(useShortcutOrbit(270f, 1f))
        assertFalse(useShortcutOrbit(320f, 1.3f))
        assertFalse(useShortcutOrbit(320f, 1.5f))
    }

    @Test fun twelveTouchTilesStayInsideTheCompactCloudAndNeverOverlap() {
        for (diameter in listOf(216f, 240f, 270f)) {
            val centers = shortcutOrbitCenters(12, diameter, 44f)
            for ((x, y) in centers) {
                assertTrue(x - 22 >= -0.01f && x + 22 <= diameter + 0.01f)
                assertTrue(y - 22 >= -0.01f && y + 22 <= diameter + 0.01f)
                val center=shortcutCloudCenter(12,diameter)
                val distance=kotlin.math.hypot(x-center.first,y-center.second)
                assertTrue("App targets must not overlap the circular summary target",distance>=56f-.01f)
            }
            centers.forEachIndexed { i, a -> centers.drop(i + 1).forEach { b ->
                assertTrue("44dp targets must not overlap", abs(a.first - b.first) >= 44f || abs(a.second - b.second) >= 44f)
            } }
        }
    }

    @Test fun sixOrFewerPinsUseTheSmallerCloudWithoutBlockingItsCenter() {
        assertTrue(useShortcutOrbit(176f,1f,6))
        assertFalse(useShortcutOrbit(176f,1f,12))
        for ((x,y) in shortcutOrbitCenters(6,176f,44f)) {
            assertTrue(x-22 >= 0 && x+22 <= 176)
            assertTrue(y-22 >= 0 && y+22 <= 176)
            assertTrue(x+22 <= 54 || x-22 >= 122 || y+22 <= 54 || y-22 >= 122)
        }
    }

    @Test fun initialsHandleWhitespaceNonLatinAndSupplementaryLetters() {
        assertEquals("АН", shortcutInitials("  Анна   Николаева "))
        assertEquals("李小", shortcutInitials("李 小龙"))
        assertEquals("𐐀B", shortcutInitials("𐐨name bob"))
        assertEquals("?", shortcutInitials("   "))
        assertEquals("?", shortcutInitials("💚"))
    }
}
