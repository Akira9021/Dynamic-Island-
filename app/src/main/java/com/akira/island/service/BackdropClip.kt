package com.akira.island.service

import kotlin.math.ceil

/** Public native blur cannot clip unequal corner radii. Oversized asymmetric corners need
 * a central rectangle: a normalized native round-rect could otherwise protrude at the large end. */
internal fun backdropHorizontalInset(width: Int, height: Int, bottom: Float, top: Float): Int {
    val largest = maxOf(bottom, top).coerceAtLeast(0f)
    if (bottom == top || largest <= minOf(width, height)/2f) return 0
    return ceil(largest.coerceAtMost(width.coerceAtLeast(0)/2f)).toInt()
}
