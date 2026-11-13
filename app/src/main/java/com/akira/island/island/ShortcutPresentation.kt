package com.akira.island.island

import java.util.Locale

internal const val SHORTCUT_PAGE_SIZE = 12

internal fun shortcutPageCount(count: Int): Int = (count.coerceAtLeast(0) + SHORTCUT_PAGE_SIZE - 1) / SHORTCUT_PAGE_SIZE

internal fun shortcutInitials(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }.take(2).mapNotNull { word ->
        word.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst().orElse(-1)
            .takeIf { it >= 0 }?.let { String(Character.toChars(it)) }
    }.joinToString("").uppercase(Locale.ROOT).ifEmpty { "?" }

internal fun useShortcutOrbit(widthDp: Float, fontScale: Float, count: Int = SHORTCUT_PAGE_SIZE): Boolean = widthDp >= (if (count <= 6) 176f else 216f) && fontScale > 0f && fontScale <= 1.2f

/** Physical centers for twelve compact slots; dimension and tile size use the same units. */
internal fun shortcutOrbitCenters(count: Int, diameter: Float, tile: Float): List<Pair<Float, Float>> {
    if (count <= 6) {
        val half = diameter / 2f
        val edge = tile / 2f
        return listOf(half to edge, diameter-edge to diameter*.375f,
            diameter-edge to diameter*.75f, half to diameter-edge,
            edge to diameter*.75f, edge to diameter*.375f).take(count.coerceAtLeast(0))
    }
    // Original layout based on the owner's reference: staggered icons around an off-center lens.
    val slots = listOf(76.5f to 27.9f, 27.9f to 50.4f, 125.1f to 50.4f,
        76.5f to 72f, 171f to 81.9f, 27.9f to 99f, 126f to 104.4f,
        171f to 130.5f, 27f to 158.4f, 125.1f to 161.1f, 76.5f to 190.8f, 171f to 185.4f)
    return slots.take(count.coerceIn(0, SHORTCUT_PAGE_SIZE)).map { (x,y) -> x*diameter/216f to y*diameter/216f }
}

internal fun shortcutCloudCenter(count: Int, diameter: Float): Pair<Float,Float> =
    if(count<=6) diameter/2 to diameter/2 else diameter*76.5f/216f to diameter*131.4f/216f
