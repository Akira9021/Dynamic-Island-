package com.akira.island.island

import java.util.Locale

internal const val SHORTCUT_PAGE_SIZE = 6

internal fun shortcutPageCount(count: Int): Int = (count.coerceAtLeast(0) + SHORTCUT_PAGE_SIZE - 1) / SHORTCUT_PAGE_SIZE

internal fun shortcutInitials(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }.take(2).mapNotNull { word ->
        word.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst().orElse(-1)
            .takeIf { it >= 0 }?.let { String(Character.toChars(it)) }
    }.joinToString("").uppercase(Locale.ROOT).ifEmpty { "?" }

internal fun useShortcutOrbit(widthDp: Float, fontScale: Float): Boolean = widthDp >= 270f && fontScale <= 1.15f

/** Physical centers for six slots; dimension and tile size use the same units. */
internal fun shortcutOrbitCenters(count: Int, diameter: Float, tile: Float): List<Pair<Float, Float>> {
    val radius = (diameter - tile).coerceAtLeast(0f) / 2f
    return (0 until count.coerceIn(0, SHORTCUT_PAGE_SIZE)).map { index ->
        val angle = Math.toRadians(-90.0 + index * 60)
        diameter / 2f + radius * kotlin.math.cos(angle).toFloat() to
            diameter / 2f + radius * kotlin.math.sin(angle).toFloat()
    }
}
