package com.akira.island.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class IdleVisibilityTest {
    @Test fun liquidExpansionDefaultsOnButExplicitChoiceSurvives() {
        assertTrue(IslandSettings().liquidExpand)
        assertTrue(Json.decodeFromString<IslandSettings>("{}").liquidExpand)
        assertFalse(Json.decodeFromString<IslandSettings>("{\"liquidExpand\":false}").liquidExpand)
    }
    @Test fun notchHasAnIndependentTrimWithoutMovingExistingPlacements() {
        assertEquals(0, IslandSettings(notch = true, offsetY = 80).topOffset)
        assertEquals(-6, IslandSettings(notch = true, offsetY = 80, notchOffsetY = -6).topOffset)
        assertEquals(80, IslandSettings(notch = false, offsetY = 80, notchOffsetY = -6).topOffset)
    }
    private fun eligible(s: IslandSettings = IslandSettings(idleHideSec = 10), idle: Boolean = true, expanded: Boolean = false,
        calibrating: Boolean = false, screen: Boolean = true, keyguard: Boolean = false, shade: Boolean = false) =
        shouldTimeOutIdle(s, idle, expanded, calibrating, screen, keyguard, shade)
    @Test fun timedHideNeverInterruptsActivitiesOrAnInactiveScreen() {
        assertTrue(eligible())
        assertFalse(eligible(idle = false)); assertFalse(eligible(expanded = true)); assertFalse(eligible(calibrating = true))
        assertFalse(eligible(screen = false)); assertFalse(eligible(keyguard = true))
        assertFalse(eligible(IslandSettings(idleHideSec = 0)))
        assertFalse(eligible(IslandSettings(idleHideSec = 10, hideIdle = true)))
        assertFalse(eligible(IslandSettings(idleHideSec = 10, collapseOnShade = true), shade = true))
    }
}
