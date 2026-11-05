package com.akira.island.data

import org.junit.Assert.*
import org.junit.Test

class SettingsSearchTest {
    @Test fun multilingualCaseAndAccentsMatch() {
        assertTrue(matchesSettingsSearch("РАЗМЕР", "Размер и форма"))
        assertTrue(matchesSettingsSearch("трех", "Трёхсторонняя рамка"))
        assertTrue(matchesSettingsSearch("francais", "Français"))
    }
    @Test fun allWordsAreRequiredAndBlankQueryListsEverything() {
        assertTrue(matchesSettingsSearch("  music   player ", "Music and player controls"))
        assertFalse(matchesSettingsSearch("music timer", "Music and player controls"))
        assertTrue(matchesSettingsSearch("", "Any setting"))
    }
}
