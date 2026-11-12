package com.akira.island.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Optical density: clear midrange, with a genuinely opaque 100% endpoint. */
internal fun glassBodyDensity(value: Float): Float {
    val density = if (value.isFinite()) value.coerceIn(0.4f, 1f) else 0.68f
    return density * density
}

/** Geometry for our glass lighting. Reused by a surface; update does not allocate a frame object. */
internal class GlassOptics(widthPx: Float, heightPx: Float, unitPx: Float = 1f) {
    private fun dimension(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1_000_000f) else 0f
    val width = dimension(widthPx)
    val height = dimension(heightPx)
    private val unit = if (unitPx.isFinite() && unitPx > 0f) unitPx.coerceIn(0.01f, 64f) else 1f
    val valid = width > 0f && height > 0f
    val softRim = min(5.5f * unit, height * 0.22f).coerceAtLeast(0.5f)
    val innerRim = min(2.4f * unit, height * 0.10f).coerceAtLeast(0.5f)
    val lip = min(1.1f * unit, height * 0.05f).coerceAtLeast(0.4f)
    val reflectionRadius = max(height * 1.3f, width * 0.4f).coerceAtLeast(1f)
    val causticRadius = max(height * 0.86f, width * 0.30f).coerceAtLeast(1f)
    val sheenDx = (width * 0.34f).coerceAtLeast(1f)
    val sheenDy = height * 0.22f
    val lensWidth = min(height * 0.20f, 8f * unit).coerceAtLeast(0.5f)
    var rimX = 0f; private set
    var rimY = 0f; private set
    var rimDx = 1f; private set
    var rimDy = 0f; private set
    var reflectionX = 0f; private set
    var reflectionY = 0f; private set
    var causticX = 0f; private set
    var causticY = 0f; private set
    var sheenX = 0f; private set
    var sheenStrength = 0f; private set

    init { update(0f, 0f, -1f) }

    fun update(tiltX: Float, tiltY: Float, sheen: Float) {
        val x = if (tiltX.isFinite()) tiltX.coerceIn(-1f, 1f) else 0f
        val y = if (tiltY.isFinite()) tiltY.coerceIn(-1f, 1f) else 0f
        val dx = 0.45f - 0.8f * x
        val dy = 0.9f - 0.45f * y
        val length = sqrt(dx * dx + dy * dy)
        val ax = dx / length
        val ay = dy / length
        // Project the real bounds onto the light axis: both wide capsules and cards keep their rim.
        val span = (abs(ax) * width + abs(ay) * height).coerceAtLeast(1f)
        rimDx = ax * span
        rimDy = ay * span
        rimX = width * (0.5f + x * 0.06f) - rimDx * 0.5f
        rimY = height * (0.5f + y * 0.04f) - rimDy * 0.5f
        reflectionX = width * (0.32f + x * 0.22f)
        reflectionY = height * (-0.34f + y * 0.18f)
        causticX = width * (0.74f - x * 0.20f)
        causticY = height * (1.05f + y * 0.05f)
        sheenX = width * (-0.48f + (if (sheen.isFinite()) sheen.coerceIn(0f, 1f) else 0f) * 1.7f)
        sheenStrength = glassSheenStrength(sheen)
    }
}
