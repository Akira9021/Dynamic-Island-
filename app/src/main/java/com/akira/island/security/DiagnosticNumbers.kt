package com.akira.island.security

private val uninterruptedNumber = Regex("\\d{9,}")
private val separatedNumber = Regex("(?<!\\d)(?:\\d[ \\u00A0\\u202F()\\-]{0,3}){8,18}\\d(?!\\d)")

/** Hide long account/card/phone numbers, including grouped digits; retain ordinary amounts. */
internal fun redactDiagnosticNumbers(text: String): String = text.take(4096)
    .replace(uninterruptedNumber, "…").replace(separatedNumber, "…")
