package com.akira.island.island

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ShortcutPresentationTest {
    @Test fun pagesKeepEverySelectedAppAndContactReachable() {
        assertEquals(0, shortcutPageCount(0))
        assertEquals(1, shortcutPageCount(1))
        assertEquals(1, shortcutPageCount(6))
        assertEquals(2, shortcutPageCount(7))
        assertEquals(2, shortcutPageCount(12))
    }

    @Test fun narrowCardsAndLargerFontsUseTheReadableGrid() {
        assertFalse(useShortcutOrbit(216f, 1f))
        assertFalse(useShortcutOrbit(269f, 1f))
        assertTrue(useShortcutOrbit(270f, 1f))
        assertFalse(useShortcutOrbit(320f, 1.3f))
        assertFalse(useShortcutOrbit(320f, 1.5f))
    }

    @Test fun sixTouchTilesStayInsideTheCircleAndNeverOverlap() {
        for (diameter in listOf(270f, 276f, 300f)) {
            val centers = shortcutOrbitCenters(6, diameter, 80f)
            for ((x, y) in centers) {
                assertTrue(x - 40 >= -0.01f && x + 40 <= diameter + 0.01f)
                assertTrue(y - 40 >= -0.01f && y + 40 <= diameter + 0.01f)
            }
            centers.forEachIndexed { i, a -> centers.drop(i + 1).forEach { b ->
                assertTrue("80dp targets must not overlap", abs(a.first - b.first) >= 80f || abs(a.second - b.second) >= 80f)
            } }
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
