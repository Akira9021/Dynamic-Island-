package com.akira.island.island

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.max
import kotlin.math.min

/**
 * A strip of the screen behind the island, captured through the accessibility
 * service. [sharp] keeps the edge crisp, [blurred] is the frosted body, and
 * [screenRect] tells the shader where the bitmap sits in screen coordinates.
 *
 * Bitmaps are deliberately not recycled when a newer frame arrives: the render
 * thread may still hold the previous shader for a frame. They are small and the
 * GC collects them once the frame is replaced.
 */
class GlassBackdropFrame(
    val sharp: Bitmap,
    val blurred: Bitmap,
    val screenRect: Rect,
    val generation: Long,
    /** Preview frames are addressed in island-local pixels, not screen pixels. */
    val local: Boolean = false,
)

/** Synthetic backdrop for the settings previews; the real overlay uses [GlassBackdropState]. */
val LocalGlassPreviewBackdrop = staticCompositionLocalOf<GlassBackdropFrame?> { null }

/**
 * Shared approximation of the library's Regular blur. The upstream View pipeline
 * uses a content-adaptive RenderEffect radius (~12 px on dark / ~28 px on light
 * scenes). Three separable box passes at half resolution give similar softness
 * without turning the compressed lens ring into a flat smudge.
 */
internal fun blurGlassBackdrop(source: Bitmap): Bitmap {
    val w = max(1, source.width / 2)
    val h = max(1, source.height / 2)
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    val light = meanBackdropLuminance(source) >= 0.52f
    val radius = min(if (light) max(2, min(w, h) / 48) else max(2, min(w, h) / 100), min(w, h) / 2)
        .coerceAtLeast(1)
    repeat(3) { boxBlur(pixels, w, h, radius) }
    small.setPixels(pixels, 0, w, 0, 0, w, h)
    val result = Bitmap.createScaledBitmap(small, source.width, source.height, true)
    small.recycle()
    result.density = Bitmap.DENSITY_NONE
    return result
}

private fun meanBackdropLuminance(bitmap: Bitmap): Float {
    var sum = 0.0
    var count = 0
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 16)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            sum += (0.2126 * ((pixel shr 16) and 0xFF) +
                0.7152 * ((pixel shr 8) and 0xFF) + 0.0722 * (pixel and 0xFF)) / 255.0
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count == 0) 0f else (sum / count).toFloat()
}

private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
    val window = radius * 2 + 1
    val temp = IntArray(pixels.size)
    for (y in 0 until h) {
        val row = y * w
        var r = 0
        var g = 0
        var b = 0
        for (x in -radius..radius) {
            val c = pixels[row + x.coerceIn(0, w - 1)]
            r += (c ushr 16) and 0xFF
            g += (c ushr 8) and 0xFF
            b += c and 0xFF
        }
        for (x in 0 until w) {
            temp[row + x] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val out = pixels[row + (x - radius).coerceIn(0, w - 1)]
            val into = pixels[row + (x + radius + 1).coerceIn(0, w - 1)]
            r += ((into ushr 16) and 0xFF) - ((out ushr 16) and 0xFF)
            g += ((into ushr 8) and 0xFF) - ((out ushr 8) and 0xFF)
            b += (into and 0xFF) - (out and 0xFF)
        }
    }
    for (x in 0 until w) {
        var r = 0
        var g = 0
        var b = 0
        for (y in -radius..radius) {
            val c = temp[y.coerceIn(0, h - 1) * w + x]
            r += (c ushr 16) and 0xFF
            g += (c ushr 8) and 0xFF
            b += c and 0xFF
        }
        for (y in 0 until h) {
            pixels[y * w + x] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val out = temp[(y - radius).coerceIn(0, h - 1) * w + x]
            val into = temp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            r += ((into ushr 16) and 0xFF) - ((out ushr 16) and 0xFF)
            g += ((into ushr 8) and 0xFF) - ((out ushr 8) and 0xFF)
            b += (into and 0xFF) - (out and 0xFF)
        }
    }
}

/** Last captured backdrop, shared by the overlay and the local previews. */
object GlassBackdropState {
    var frame: GlassBackdropFrame? by mutableStateOf(null)
        private set

    fun publish(next: GlassBackdropFrame) {
        frame = next
    }

    fun clear() {
        frame = null
    }
}
