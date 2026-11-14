package com.akira.island.lyrics

import org.junit.Assert.*
import org.junit.Test

/** Fails before the proposed parser integration; preserves real line timing and existing offset. */
class EnhancedLrcRegressionTest {
    @Test fun enhancedLyricsKeepTheirLineClockWithoutDisplayingWordMetadata() {
        val line = LyricTiming.parse("[offset:+500]\n[00:08.880]<00:08.880>Dizem <00:09.760>que").single()
        assertEquals(8380L, line.timeMs)
        assertEquals("Dizem que", line.text)
    }
    @Test fun plainLyricsRemoveMetadataWithoutClaimingWordSynchronization() {
        val line = LyricTiming.plain("<00:08.880>Dizem <00:09.760>que").single()
        assertEquals(0L, line.timeMs)
        assertEquals("Dizem que", line.text)
    }
}
