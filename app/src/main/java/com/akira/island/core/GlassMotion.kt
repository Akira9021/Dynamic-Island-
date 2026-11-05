package com.akira.island.core

import kotlin.math.exp
import kotlin.math.sin

/** Normalized light direction, in the island's own coordinates. */
data class GlassTilt(val x: Float = 0f, val y: Float = 0f) {
    companion object { val Zero = GlassTilt() }
}

private fun Float.safeUnit(): Float = if (isFinite()) coerceIn(-1f, 1f) else 0f

/** Display quarter turns and the island's rotation cancel when it stays beside the camera. */
fun glassTiltFromGravity(x: Float, y: Float, displayTurns: Int, contentDegrees: Int): GlassTilt {
    val gx = (x / 9.81f).safeUnit()
    val gy = (y / 9.81f).safeUnit()
    return when (Math.floorMod(displayTurns + contentDegrees / 90, 4)) {
        1 -> GlassTilt(gy, -gx)
        2 -> GlassTilt(-gx, -gy)
        3 -> GlassTilt(-gy, gx)
        else -> GlassTilt(gx, gy)
    }
}

/** Time-based smoothing feels the same with gravity sensors running at different rates. */
class GlassTiltFilter {
    var value = GlassTilt.Zero
        private set
    private var timestampNs = 0L

    fun update(target: GlassTilt, timestamp: Long): GlassTilt {
        val dt = if (timestampNs == 0L) 1f / 60f else ((timestamp - timestampNs) / 1_000_000_000f).coerceIn(0f, 0.25f)
        timestampNs = timestamp
        val weight = 1f - exp(-dt / 0.12f)
        value = GlassTilt(value.x + (target.x.safeUnit() - value.x) * weight, value.y + (target.y.safeUnit() - value.y) * weight)
        return value
    }

    fun reset() { timestampNs = 0L; value = GlassTilt.Zero }
}

/** No flash when a sheen starts, finishes or is cancelled. */
fun glassSheenStrength(progress: Float): Float =
    if (progress.isFinite() && progress > 0f && progress < 1f) sin(Math.PI.toFloat() * progress).coerceIn(0f, 1f) else 0f
