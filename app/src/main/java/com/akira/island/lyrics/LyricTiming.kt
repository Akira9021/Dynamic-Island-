package com.akira.island.lyrics

import com.akira.island.core.LyricLine
import java.util.Locale

/** LRC timestamps belong to the recording; plain lyrics must never invent a clock. */
internal object LyricTiming {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) return@forEach
            val text = enhancedLrcText(raw.substring(tags.last().range.last + 1)).trim()
            tags.forEach tag@ { m ->
                val minutes = m.groupValues[1].toLongOrNull() ?: return@tag
                val seconds = m.groupValues[2].replace(':', '.').toDoubleOrNull() ?: return@tag
                if (seconds !in 0.0..<60.0) return@tag
                // Positive LRC offset makes a line occur earlier relative to the player clock.
                out += LyricLine((minutes * 60_000 + (seconds * 1000).toLong() - offset).coerceAtLeast(0L), text)
            }
        }
        return out.distinct().sortedBy { it.timeMs }
    }

    fun plain(text: String): List<LyricLine> = text.lineSequence().map(::enhancedLrcText).map(String::trim)
        .filter(String::isNotEmpty).map { LyricLine(0, it) }.toList()

    fun identity(title: String, artist: String, album: String?, durationMs: Long): String =
        listOf(title, artist, album.orEmpty(), durationMs.coerceAtLeast(0).toString())
            .joinToString("\u001f") { it.trim().lowercase(Locale.ROOT) }

    fun normalized(text: String): String = text.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    /** Exact titles preserve live/remix/instrumental versions rather than matching a substring. */
    fun matchesTitle(wanted: String, candidate: String): Boolean =
        normalized(wanted).isNotEmpty() && normalized(wanted) == normalized(candidate)
}
