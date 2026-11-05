package com.akira.island.service

import java.util.Locale

/** Pure payment identity logic: currencies and decimal zeros must not merge different amounts. */
internal object PaymentRules {
    fun amountKey(amount: String): String {
        val raw = amount.lowercase(Locale.ROOT).replace('\u00a0', ' ').replace('\u202f', ' ')
        val currency = when {
            Regex("byn|бел.*руб").containsMatchIn(raw) -> "BYN"
            Regex("rub|rur|руб|₽|(?:^|\\s)р\\.?(?:$|\\s)").containsMatchIn(raw) -> "RUB"
            "usd" in raw || "$" in raw && "r$" !in raw -> "USD"
            "eur" in raw || "€" in raw -> "EUR"
            "brl" in raw || "r$" in raw -> "BRL"
            "idr" in raw || "rp" in raw -> "IDR"
            "kzt" in raw || "₸" in raw -> "KZT"
            "gbp" in raw || "£" in raw -> "GBP"
            else -> raw.filter(Char::isLetter).uppercase(Locale.ROOT).ifEmpty { "?" }
        }
        val firstDigit = raw.indexOfFirst(Char::isDigit)
        val lastDigit = raw.indexOfLast(Char::isDigit)
        if (firstDigit < 0) return "unknown:$raw"
        // A period in the currency abbreviation (“руб.”) is not a decimal separator.
        val number = raw.substring(firstDigit, lastDigit + 1).filter { it.isDigit() || it == ',' || it == '.' }
        val last = maxOf(number.lastIndexOf(','), number.lastIndexOf('.'))
        val decimals = if (last >= 0) number.length - last - 1 else 0
        val normalized = if (last >= 0 && decimals in 1..2) {
            number.substring(0, last).filter(Char::isDigit) + "." + number.substring(last + 1)
        } else number.filter(Char::isDigit)
        val value = normalized.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: return "unknown:$raw"
        return "$currency:$value"
    }

    fun followsReady(postTime: Long, readyWallTime: Long, nowElapsed: Long, readyElapsed: Long, window: Long): Boolean =
        readyElapsed > 0 && nowElapsed >= readyElapsed && nowElapsed - readyElapsed < window &&
            postTime >= readyWallTime
}

/** Correlates screen and notification reports without swallowing a second same-price purchase. */
internal class PaymentDeduplicator {
    private data class Seen(val amount: String, val event: String, val session: Long, val at: Long)
    private val seen = ArrayDeque<Seen>()

    fun duplicate(amount: String, event: String, session: Long, now: Long): Boolean {
        while (seen.isNotEmpty() && now - seen.first().at > 180_000) seen.removeFirst()
        val key = PaymentRules.amountKey(amount)
        val duplicate = seen.any {
            (it.event == event && it.amount == key) ||
                (session > 0 && session == it.session && (it.amount == key || amount.isBlank() || it.amount.startsWith("unknown:")))
        }
        if (!duplicate) {
            seen.addLast(Seen(key, event, session, now))
            while (seen.size > 32) seen.removeFirst()
        }
        return duplicate
    }
}
