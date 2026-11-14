package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class BackdropClipTest {
    @Test fun uniformCapsuleUsesTheWholeWindow() {
        assertEquals(0,backdropHorizontalInset(160,32,16f,16f))
        assertEquals(0,backdropHorizontalInset(160,32,60f,60f))
    }
    @Test fun ordinaryNotchKeepsFullWidth() {
        assertEquals(0,backdropHorizontalInset(160,40,20f,0f))
        assertEquals(0,backdropHorizontalInset(350,250,44f,8f))
    }
    @Test fun oversizedBottomNeverUsesAnIncorrectSmallerNativeRadius() {
        assertEquals(35,backdropHorizontalInset(160,40,35f,0f))
    }
    @Test fun oversizedTopAlsoUsesAnInscribedRegion() {
        assertEquals(36,backdropHorizontalInset(160,40,12f,35.5f))
    }
    @Test fun impossibleNarrowRegionMayBecomeEmptyInsteadOfLeaking() {
        assertEquals(20,backdropHorizontalInset(40,32,60f,0f))
    }
}
