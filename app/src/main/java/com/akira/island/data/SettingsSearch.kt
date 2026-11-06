package com.akira.island.data

import java.text.Normalizer
import java.util.Locale

private val diacritics = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")

internal fun normalizeSettingsSearch(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ё', 'е'), Normalizer.Form.NFD)
    .replace(diacritics, "").trim()

internal fun matchesSettingsSearch(query: String, text: String): Boolean {
    val words = normalizeSettingsSearch(query).split(whitespace).filter { it.isNotEmpty() }
    val normalized = normalizeSettingsSearch(text)
    return words.all { it in normalized }
}
