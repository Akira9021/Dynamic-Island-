package com.akira.island.island

import com.akira.island.core.ProgressInfo
import org.junit.Assert.*
import org.junit.Test

class GroupedProgressTest {
    private fun download(key: String, progress: Int, max: Int, time: Long, unknown: Boolean = false) =
        ProgressInfo(key, "example.download", key, null, progress, max, unknown, time)

    @Test fun emptyAndSingleKeepTheirMeaning() {
        assertNull(groupedProgress(emptyList()))
        val one = download("one", 10, 20, 1)
        assertEquals(one, groupedProgress(listOf(one)))
    }

    @Test fun multipleDownloadsHaveStableIdentityAndNormalizedProgress() {
        val a = download("a", 50, 100, 1)
        val b = download("b", 1000, 1000, 2)
        val first = groupedProgress(listOf(a, b))!!
        val reordered = groupedProgress(listOf(b.copy(postTime = 99), a))!!
        assertEquals("downloads", first.key)
        assertEquals(first, reordered)
        assertEquals(0.75f, first.fraction, 0.001f)
    }

    @Test fun unknownSizesDoNotInventAnOverallPercentage() {
        val result = groupedProgress(listOf(download("a", 20, 100, 1), download("b", 0, 0, 2, true)))!!
        assertTrue(result.indeterminate)
    }
}
