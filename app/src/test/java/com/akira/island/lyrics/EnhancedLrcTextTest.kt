package com.akira.island.lyrics

import org.junit.Assert.*
import org.junit.Test

class EnhancedLrcTextTest {
    @Test fun realWordTagsDoNotLeakIntoTheDisplayedPortugueseLyrics() {
        assertEquals("Dizem que sou louco", enhancedLrcText("<00:08.880>Dizem <00:09.760>que <00:10.520>sou <00:11.040>louco"))
    }
    @Test fun wordFragmentsJoinWithoutInventingSpacesOrRemovingOrdinaryText() {
        assertEquals("hello <3 <em>world</em> <00:99.00>",
            enhancedLrcText("<00:01.00>hel<00:01.500>lo <3 <em>world</em> <00:99.00>"))
    }
}
