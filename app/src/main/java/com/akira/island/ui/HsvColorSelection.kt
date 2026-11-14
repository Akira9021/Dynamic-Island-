package com.akira.island.ui

import kotlin.math.floor
import kotlin.math.roundToInt

/** Opaque or translucent ARGB remains unchanged until the user confirms a selection. */
internal data class HsvColorSelection(val hue: Float, val saturation: Float, val value: Float, val alpha: Int = 255) {
    fun toArgb(): Long {
        val h = hue.takeIf { it.isFinite() }?.let { (it % 360f + 360f) % 360f } ?: 0f
        val s = saturation.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val v = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val sector = h / 60f
        val c = v * s
        val x = c * (1f - kotlin.math.abs(sector % 2f - 1f))
        val m = v - c
        val rgb = when (floor(sector).toInt()) {
            0 -> floatArrayOf(c, x, 0f)
            1 -> floatArrayOf(x, c, 0f)
            2 -> floatArrayOf(0f, c, x)
            3 -> floatArrayOf(0f, x, c)
            4 -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }.map { ((it + m) * 255f).roundToInt().coerceIn(0, 255).toLong() }
        return (alpha.coerceIn(0, 255).toLong() shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
    }

    companion object {
        fun fromArgb(argb: Long): HsvColorSelection {
            val r = ((argb ushr 16) and 255) / 255f
            val g = ((argb ushr 8) and 255) / 255f
            val b = (argb and 255) / 255f
            val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
            val hue = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            return HsvColorSelection((hue + 360f) % 360f, if (max == 0f) 0f else delta / max,
                max, ((argb ushr 24) and 255).toInt())
        }
    }
}
