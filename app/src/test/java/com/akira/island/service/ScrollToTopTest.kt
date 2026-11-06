package com.akira.island.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScrollToTopTest {
    private class Target(var jump: Boolean = false, var upwardActions: Int = Int.MAX_VALUE) : ScrollTopTarget {
        var current = true
        var jumps = 0
        var scrolls = 0
        var closed = false
        override fun isCurrent() = current
        override fun jumpToStart(): Boolean { jumps++; return jump }
        override fun scrollUp(): Boolean { scrolls++; return scrolls <= upwardActions }
        override fun close() { closed = true }
    }

    @Test fun supportedPositionActionDoesNotSendExtraPageActions() = runBlocking {
        val target = Target(jump = true)
        assertEquals(ScrollTopResult.REQUESTED, scrollToTop(target, pause = { fail("No retry after accepted position action") }))
        assertEquals(1, target.jumps)
        assertEquals(0, target.scrolls)
        assertTrue(target.closed)
    }

    @Test fun rejectedPositionFallsBackAndStopsWhenUpwardScrollingStops() = runBlocking {
        val target = Target(upwardActions = 2)
        assertEquals(ScrollTopResult.REQUESTED, scrollToTop(target, pause = {}))
        assertEquals(3, target.scrolls)
        assertTrue(target.closed)
        val unsupported = Target(upwardActions = 0)
        assertEquals(ScrollTopResult.UNSUPPORTED, scrollToTop(unsupported, pause = {}))
        assertTrue(unsupported.closed)
    }

    @Test fun anAppOrWindowChangePreventsTheNextAction() = runBlocking {
        val target = Target()
        assertEquals(ScrollTopResult.CANCELLED, scrollToTop(target, pause = { target.current = false }))
        assertEquals(1, target.scrolls)
        assertTrue(target.closed)
    }

    @Test fun aNonStoppingAppCannotCreateAnUnboundedScrollLoop() = runBlocking {
        val target = Target()
        assertEquals(ScrollTopResult.LIMIT_REACHED, scrollToTop(target, maxSteps = Int.MAX_VALUE, pause = {}))
        assertEquals(16, target.scrolls)
        assertTrue(target.closed)
    }

    @Test fun cancellationReleasesTheAccessibilityTarget() = runBlocking {
        val target = Target()
        try {
            scrollToTop(target, pause = { throw CancellationException() })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, target.scrolls)
            assertTrue(target.closed)
        }
    }

    @Test fun genericHorizontalOrUnknownContainersAreRejected() {
        assertFalse(isVerticalScroll("android.widget.HorizontalScrollView", 1, 50, false, false))
        assertFalse(isVerticalScroll("androidx.recyclerview.widget.RecyclerView", 1, 50, false, false))
        assertFalse(isVerticalScroll("android.view.View", 0, 0, false, false))
        assertTrue(isVerticalScroll("android.widget.ScrollView", 0, 0, false, false))
        assertTrue(isVerticalScroll("androidx.recyclerview.widget.RecyclerView", 50, 1, false, false))
        assertTrue(isVerticalScroll("android.view.View", 0, 0, true, false))
    }
}
