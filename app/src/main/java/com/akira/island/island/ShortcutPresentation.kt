package com.akira.island.island

import java.util.Locale

internal fun shortcutInitials(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }.take(2).mapNotNull { word ->
        word.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst().orElse(-1)
            .takeIf { it >= 0 }?.let { String(Character.toChars(it)) }
    }.joinToString("").uppercase(Locale.ROOT).ifEmpty { "?" }
