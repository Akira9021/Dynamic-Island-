package com.akira.island.data

import com.akira.island.core.NoticeStyle
import org.junit.Assert.*
import org.junit.Test

class AppearanceStyleTest {
    @Test fun inheritedCardUsesAllCapsuleEdgesAndOpacity() {
        val s = IslandSettings(outline = true, outlineColor = 0xFFFF0000, outlineOpacity = 45,
            outlineWidth = 2f, outlineLeftWidth = 4f)
        assertEquals(s.resolveOutline(false), s.resolveOutline(true))
        assertEquals(listOf(2f, 2f, 2f, 4f), s.resolveOutline(true).edges)
    }
    @Test fun ownCardColorIsNeverOverriddenByActivityColor() {
        val s = IslandSettings(outline = true, outlineFromArt = true, expandedOutlineWidth = 2f, expandedOutlineColor = 0xFF00FF00)
        assertTrue(s.resolveOutline(false).fromActivity)
        assertFalse(s.resolveOutline(true).fromActivity)
        assertEquals(0xFF00FF00, s.resolveOutline(true).color)
    }
    @Test fun disablingCardBorderAlsoDisablesSavedPerEdgeWidths() {
        val s = IslandSettings(outline = true, expandedOutlineWidth = 2f, expandedOutlineLeftWidth = 5f)
            .withBorderMode(true, BorderMode.NONE)
        assertEquals(BorderMode.NONE, s.borderMode(true))
        assertFalse(s.resolveOutline(true).visible)
        assertTrue(s.resolveOutline(false).visible)
    }
    @Test fun inheritedOffCapsuleCannotProducePhantomCardBorder() {
        val s = IslandSettings(outline = false, expandedOutlineWidth = 3f).withBorderMode(true, BorderMode.INHERIT)
        assertEquals(BorderMode.INHERIT, s.borderMode(true))
        assertFalse(s.resolveOutline(true).visible)
    }
    @Test fun uniformModeClearsHiddenEdgeOverrides() {
        val s = IslandSettings(outline = true, outlineWidth = 2f, outlineLeftWidth = 5f)
            .withBorderWidths(false, BorderWidths.UNIFORM)
        assertEquals(BorderWidths.UNIFORM, s.borderWidths(false))
        assertEquals(listOf(2f, 2f, 2f, 2f), s.resolveOutline(false).edges)
    }
    @Test fun switchingToEdgesKeepsAppearanceUntilAnEdgeIsEdited() {
        val s = IslandSettings(outline = true, outlineWidth = 3f)
        assertEquals(s.resolveOutline(false), s.withBorderWidths(false, BorderWidths.EDGES).resolveOutline(false))
    }
    @Test fun eventSurfaceDoesNotDependOnNotificationStyle() {
        val s = IslandSettings(messageStyle = MessageStyle.ICON)
        assertTrue(s.usesCardSurface(false, NoticeStyle.WIDE))
        assertFalse(s.usesCardSurface(false, NoticeStyle.MESSAGE))
        assertFalse(s.copy(messageStyle = MessageStyle.CARD).usesCardSurface(false, NoticeStyle.PILL))
        assertTrue(s.usesCardSurface(false, NoticeStyle.MESSAGE, detail = true))
    }
    @Test fun legacyPlayerAndGlobalVisibilityHaveOneUnambiguousChoice() {
        val s = IslandSettings(hideWhenPlayerOpen = true, hideWhenSourceOpen = true)
        assertEquals(SourceVisibility.HIDE_ALL, s.sourceVisibility)
        val show = s.withSourceVisibility(SourceVisibility.SHOW)
        assertFalse(show.hideWhenSourceOpen)
        assertFalse(show.hideWhenPlayerOpen)
    }
    @Test fun swipeModesNeverLeaveCompetingActionsEnabled() {
        val s = IslandSettings(swipeActivities = true, swipeTracks = true).withSideSwipe(SideSwipe.TRACKS)
        assertFalse(s.swipeActivities)
        assertTrue(s.swipeTracks)
        assertEquals(SideSwipe.TRACKS, s.sideSwipe)
    }
    @Test fun clockAndHiddenCapsuleCannotCompete() {
        val hidden = IslandSettings(hideIdle = true, idleClockBattery = true)
        assertEquals(EmptyCapsule.HIDDEN, hidden.emptyCapsule)
        val clock = hidden.withEmptyCapsule(EmptyCapsule.CLOCK)
        assertFalse(clock.hideIdle)
        assertTrue(clock.idleClockBattery)
        val plain = clock.withEmptyCapsule(EmptyCapsule.PLAIN)
        assertFalse(plain.hideIdle)
        assertFalse(plain.idleClockBattery)
    }

}
