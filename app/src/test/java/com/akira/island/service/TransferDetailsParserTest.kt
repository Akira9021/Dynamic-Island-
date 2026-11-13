package com.akira.island.service

import com.akira.island.core.TransferDirection
import org.junit.Assert.*
import org.junit.Test

class TransferDetailsParserTest {
    @Test fun englishRateAndEta() {
        val d = TransferDetailsParser.parse("Download: 1.5 MB/s · 2 min left")!!
        assertEquals(1_500_000.0, d.bytesPerSecond!!, 0.0)
        assertEquals(120L, d.etaSeconds)
        assertEquals(TransferDirection.DOWN, d.direction)
    }
    @Test fun russianCommaAndUpload() {
        val d = TransferDetailsParser.parse("Отправка: 2,5 МБ/с\nОсталось 3 минуты")!!
        assertEquals(2_500_000.0, d.bytesPerSecond!!, 0.0)
        assertEquals(180L, d.etaSeconds)
        assertEquals(TransferDirection.UP, d.direction)
    }
    @Test fun binaryAndDecimalUnitsAreNotMixed() {
        assertEquals(1024.0, TransferDetailsParser.parse("1 KiB/s")!!.bytesPerSecond!!, 0.0)
        assertEquals(1000.0, TransferDetailsParser.parse("1 KB/s")!!.bytesPerSecond!!, 0.0)
        assertEquals(1048576.0, TransferDetailsParser.parse("1 MiB/s")!!.bytesPerSecond!!, 0.0)
    }
    @Test fun unknownDirectionIsNotInferredFromAFileOrAppName() {
        assertNull(TransferDetailsParser.parse("Movie 8 MB/s")!!.direction)
        assertEquals(TransferDirection.UP, TransferDetailsParser.parse("↑ 8 MB/s")!!.direction)
    }
    @Test fun rejectsNegativeScientificAmbiguousAndHugeInputs() {
        for (s in listOf("-2 MB/s", "−2 MB/s", "1e3 MB/s", "NaN MB/s", "1 000 MB/s", "1 MB/s 2 MB/s", "x".repeat(5000)))
            assertNull(s, TransferDetailsParser.parse(s))
    }
    @Test fun etaNeedsAnExplicitLabelAndSingleBoundedDuration() {
        for (s in listOf("12:30", "2 min", "ETA -2 min", "ETA 999999 hours", "ETA 1h 20min"))
            assertNull(s, TransferDetailsParser.parse(s))
        assertEquals(0L, TransferDetailsParser.parse("ETA 0 sec")!!.etaSeconds)
        assertEquals(7200L, TransferDetailsParser.parse("remaining 2 hours")!!.etaSeconds)
    }
    @Test fun bitsAreNotMisreportedAsBytes() {
        assertNull(TransferDetailsParser.parse("8 Mb/s"))
        assertNull(TransferDetailsParser.parse("8 mb/s"))
        assertEquals(8_000_000.0, TransferDetailsParser.parse("8 MB/s")!!.bytesPerSecond!!, 0.0)
    }
    @Test fun embeddedLabelsAndIncompleteCompoundDurationsAreNotEta() {
        for (s in listOf("beta2min.mp4", "2 min leftovers", "ETA 2min30"))
            assertNull(s, TransferDetailsParser.parse(s))
    }
}
