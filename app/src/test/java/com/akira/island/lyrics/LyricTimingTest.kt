package com.akira.island.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricTimingTest {
    @Test fun `timestamps preserve instrumental intro and blank interludes`() {
        val lines = LyricTiming.parse("[00:18.50]First verse\n[00:45.00]\n[01:00.10]Second verse")
        assertEquals(listOf(18_500L, 45_000L, 60_100L), lines.map { it.timeMs })
        assertEquals("", lines[1].text)
    }

    @Test fun `offset is applied to all tags independent of header position`() {
        val lines = LyricTiming.parse("[00:05.00][00:10.00]Repeated\n[offset:+500]")
        assertEquals(listOf(4500L, 9500L), lines.map { it.timeMs })
    }

    @Test fun `negative offset delays lyric and never produces negative time`() {
        assertEquals(1500L, LyricTiming.parse("[offset:-500]\n[00:01]Verse").single().timeMs)
        assertEquals(0L, LyricTiming.parse("[offset:5000]\n[00:01]Verse").single().timeMs)
    }

    @Test fun `invalid seconds and duplicate tags are ignored`() {
        val lines = LyricTiming.parse("[00:70]Invalid\n[00:04.50]Verse\n[00:04.50]Verse\n[by:author]")
        assertEquals(1, lines.size)
        assertEquals(4500L, lines.single().timeMs)
    }

    @Test fun `plain text is retained without fabricated synchronization`() {
        val lines = LyricTiming.plain("\nFirst\n\n Second \n")
        assertEquals(listOf("First", "Second"), lines.map { it.text })
        assertTrue(lines.all { it.timeMs == 0L })
    }

    @Test fun `recording versions cannot share a cache entry`() {
        val studio = LyricTiming.identity("Song", "Artist", "Studio", 180_000)
        assertNotEquals(studio, LyricTiming.identity("Song", "Artist", "Live", 180_000))
        assertNotEquals(studio, LyricTiming.identity("Song", "Artist", "Studio", 190_000))
        assertEquals(studio, LyricTiming.identity(" SONG ", "artist", "studio", 180_000))
    }

    @Test fun `similar titles do not select a different recording`() {
        assertTrue(LyricTiming.matchesTitle("Hello, World!", "hello world"))
        assertFalse(LyricTiming.matchesTitle("Song", "Song (Live)"))
        assertFalse(LyricTiming.matchesTitle("Song", "Song (Instrumental)"))
        assertFalse(LyricTiming.matchesTitle("", ""))
    }
}
