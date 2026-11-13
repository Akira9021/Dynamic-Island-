package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class BackdropBlurPolicyTest {
    @Test fun `opaque fills do not spend GPU work on an invisible backdrop`() {
        val s = com.akira.island.data.IslandSettings()
        assertFalse(backdropTransmits(s))
        assertTrue(backdropTransmits(s.copy(liquidGlass = true, glassOpacity = 68)))
        assertFalse(backdropTransmits(s.copy(liquidGlass = true, glassOpacity = 100)))
        assertTrue(backdropTransmits(s.copy(islandOpacity = 60)))
        assertTrue(backdropTransmits(s.copy(expandedColorEnabled = true, expandedColor = 0x80000000)))
    }
    @Test fun `blur requires every visibility condition and respects all safety gates`() {
        assertTrue(backdropAllowed(true, true, true, true, true, false, false, false, true))
        val normal = booleanArrayOf(true, true, true, true, true, false, false, false, true)
        for (index in normal.indices) {
            val f = normal.copyOf(); f[index] = !f[index]
            assertFalse("Gate $index", backdropAllowed(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8]))
        }
    }
    @Test fun `old settings keep blur opt in`() {
        assertFalse(com.akira.island.data.IslandSettings().backdropBlur)
        val old = kotlinx.serialization.json.Json.decodeFromString<com.akira.island.data.IslandSettings>("{}")
        assertFalse(old.backdropBlur)
        val saved = kotlinx.serialization.json.Json.encodeToString(com.akira.island.data.IslandSettings.serializer(), old.copy(backdropBlur = true))
        assertTrue(kotlinx.serialization.json.Json.decodeFromString<com.akira.island.data.IslandSettings>(saved).backdropBlur)
    }
}
