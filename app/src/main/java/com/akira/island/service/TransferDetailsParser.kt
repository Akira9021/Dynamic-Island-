package com.akira.island.service

import com.akira.island.core.TransferDetails
import com.akira.island.core.TransferDirection
import java.util.Locale

internal object TransferDetailsParser {
    private val rate = Regex("(?<![\\p{L}\\p{N}.,−-])(\\d{1,9}(?:[.,]\\d{1,3})?)\\s*(KiB|MiB|KB|MB|КиБ|МиБ|КБ|МБ|B|Б)\\s*/(?:sec|s|с)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val eta = Regex("(?<![\\p{L}\\p{N}])(?:ETA|remaining|осталось)\\s*[:=]?\\s*(\\d{1,6})\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|сек(?:унд[аы]?)?|с|мин(?:ут[аы]?)?|м|час(?:а|ов)?|ч)(?![\\p{L}])|(?<![\\p{L}\\p{N}−-])(\\d{1,6})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|сек(?:унд[аы]?)?|мин(?:ут[аы]?)?|час(?:а|ов)?)\\s*(?:left|remaining|осталось)(?![\\p{L}])", RegexOption.IGNORE_CASE)

    fun parse(text: String): TransferDetails? {
        if (text.length > 4096) return null
        val matches = rate.findAll(text).take(2).toList()
        // Two speeds may describe different operations/directions; never guess which belongs here.
        val match = matches.singleOrNull()?.takeUnless {
            text.take(it.range.first).takeLast(2).matches(Regex("\\d[ \\u00a0]"))
        }
        val speed = match?.let {
            val suppliedUnit = it.groupValues[2]
            if (!suppliedUnit.endsWith("B") && !suppliedUnit.endsWith("Б")) return@let null
            val unit = suppliedUnit.lowercase(Locale.ROOT)
            val multiplier = when (unit) {
                "kib", "киб" -> 1024.0
                "mib", "миб" -> 1048576.0
                "kb", "кб" -> 1000.0
                "mb", "мб" -> 1000000.0
                else -> 1.0
            }
            it.groupValues[1].replace(',', '.').toDoubleOrNull()?.times(multiplier)?.takeIf { n -> n.isFinite() && n in 0.0..1e12 }
        }
        val direction = match?.let {
            val prefix = text.substring((it.range.first - 24).coerceAtLeast(0), it.range.first).lowercase(Locale.ROOT)
            when {
                Regex("(?:↓|download(?:ing)?|скачивание|загрузка)\\s*[:=]?\\s*$").containsMatchIn(prefix) -> TransferDirection.DOWN
                Regex("(?:↑|upload(?:ing)?|отправка|выгрузка)\\s*[:=]?\\s*$").containsMatchIn(prefix) -> TransferDirection.UP
                else -> null
            }
        }
        val estimates = eta.findAll(text).take(2).toList()
        val seconds = estimates.singleOrNull()?.let {
            if (Regex("^\\s*\\d").containsMatchIn(text.substring(it.range.last + 1))) return@let null
            val start = text.lastIndexOf('\n', it.range.first).let { i -> if (i < 0) 0 else i + 1 }
            val end = text.indexOf('\n', it.range.last).let { i -> if (i < 0) text.length else i }
            val line = text.substring(start, end)
            // Compound durations are deliberately unsupported until parsed as a whole.
            val durationTokens = Regex("\\d+\\s*(?:hours?|hrs?|h|час(?:а|ов)?|ч|minutes?|mins?|m|мин(?:ут[аы]?)?|м|seconds?|secs?|s|сек(?:унд[аы]?)?|с)(?![\\p{L}])", RegexOption.IGNORE_CASE)
            if (durationTokens.findAll(line).take(2).count() > 1) return@let null
            val amount = it.groupValues[1].ifEmpty { it.groupValues[3] }.toLongOrNull() ?: return@let null
            val unit = it.groupValues[2].ifEmpty { it.groupValues[4] }.lowercase(Locale.ROOT)
            val factor = when { unit.startsWith("h") || unit.startsWith("ч") -> 3600L
                unit.startsWith("m") || unit.startsWith("м") -> 60L; else -> 1L }
            (amount * factor).takeIf { n -> n in 0..604800 }
        }
        return if (speed == null && seconds == null) null else TransferDetails(speed, direction.takeIf { speed != null }, seconds)
    }
}
