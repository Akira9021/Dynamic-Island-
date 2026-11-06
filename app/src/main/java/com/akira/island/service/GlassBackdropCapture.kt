package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.akira.island.island.GlassBackdropFrame
import com.akira.island.island.GlassBackdropState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Captures the window that sits under the island and turns it into a small,
 * pre-blurred bitmap for [com.akira.island.island.LIQUID_GLASS_AGSL].
 *
 * Why the accessibility screenshot and not a normal screen capture:
 *  - the island is drawn in an accessibility overlay, so a plain screenshot would
 *    photograph the island itself and feed it back into the glass;
 *  - `takeScreenshotOfWindow` (Android 14+) returns the app window *below* the
 *    overlay, so the refracted pixels are genuinely the content behind it.
 *
 * The screenshot API is throttled by the system (~3 requests per second), which
 * is enough for a status-bar island: the background there is mostly static. The
 * last frame is kept while the screen is off or power saving is on.
 */
class GlassBackdropCapture(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) {
    private val density = service.resources.displayMetrics.density
    private val mutex = Mutex()
    private val generation = AtomicLong(0)
    private var job: Job? = null
    private var failures = 0
    private var lastSignature = Long.MIN_VALUE
    private var lastRect = Rect()

    @Volatile private var rect = Rect()
    @Volatile private var enabled = false

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    runCatching { captureOnce() }
                }
                delay(if (failures >= 3) 2_000L else 380L)
            }
        }
    }

    /** The settings/screen state gate. The last frame stays when it turns off. */
    fun setEnabled(on: Boolean) {
        enabled = on
        if (on) failures = 0
    }

    /** Island bounds in screen pixels. */
    fun updateRect(next: Rect) {
        if (next != rect) rect = Rect(next)
    }

    fun stop() {
        job?.cancel()
        job = null
        enabled = false
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun captureOnce() = mutex.withLock {
        val area = rect
        if (area.isEmpty) return@withLock
        if (!service.let { runCatching { it.windows }.isSuccess }) return@withLock

        val window = findWindow(area) ?: return@withLock
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return@withLock

        val shot = screenshot(window.id)
        if (shot == null) {
            failures++
            if (failures == 1 || failures % 10 == 0) Log.w(TAG, "screenshot of window ${window.id} failed ($failures)")
            return@withLock
        }
        val frame = try {
            withContext(Dispatchers.Default) { buildFrame(shot, bounds, area) }
        } finally {
            runCatching { shot.hardwareBuffer.close() }
        }
        if (frame == null) {
            failures++
            return@withLock
        }
        failures = 0

        // Do not rebuild the shader for a pixel-identical backdrop. The island
        // moves constantly during animations, so the rect is part of the key.
        val signature = signature(frame.sharp) xor area.hashCode().toLong()
        if (signature == lastSignature && area == lastRect) return@withLock
        lastSignature = signature
        lastRect = Rect(area)
        withContext(Dispatchers.Main) { GlassBackdropState.publish(frame) }
    }

    /**
     * Topmost application window that covers the island centre. The accessibility
     * overlay itself has TYPE_ACCESSIBILITY_OVERLAY and is ignored. The active app
     * wins; otherwise the smallest covering window is used.
     */
    private fun findWindow(area: Rect): AccessibilityWindowInfo? {
        val windows = runCatching { service.windows }.getOrNull() ?: return null
        val covering = windows.filter { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@filter false
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            Rect.intersects(bounds, area)
        }
        if (covering.isEmpty()) return null
        return covering.firstOrNull { it.isActive } ?: covering.maxByOrNull { window ->
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            val overlap = Rect(bounds)
            if (!overlap.intersect(area)) 0L else overlap.width().toLong() * overlap.height().toLong()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun screenshot(windowId: Int): AccessibilityService.ScreenshotResult? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                service.takeScreenshotOfWindow(
                    windowId,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            if (continuation.isActive) continuation.resume(result)
                            else runCatching { result.hardwareBuffer.close() }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun buildFrame(
        shot: AccessibilityService.ScreenshotResult,
        windowBounds: Rect,
        island: Rect,
    ): GlassBackdropFrame? {
        val hardware = Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace) ?: return null
        val source = if (hardware.config == Bitmap.Config.HARDWARE) {
            hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
        } else hardware
        if (source == null || source.width <= 0 || source.height <= 0) return null

        val scaleX = source.width.toFloat() / windowBounds.width().coerceAtLeast(1)
        val scaleY = source.height.toFloat() / windowBounds.height().coerceAtLeast(1)

        // Extra margin lets the shader sample the refracted pixels outside the lens.
        val margin = (28f * density).roundToInt()
        val wanted = Rect(island)
        wanted.inset(-margin, -margin)

        val left = ((wanted.left - windowBounds.left) * scaleX).roundToInt().coerceIn(0, source.width - 1)
        val top = ((wanted.top - windowBounds.top) * scaleY).roundToInt().coerceIn(0, source.height - 1)
        val right = ((wanted.right - windowBounds.left) * scaleX).roundToInt().coerceIn(left + 1, source.width)
        val bottom = ((wanted.bottom - windowBounds.top) * scaleY).roundToInt().coerceIn(top + 1, source.height)

        val piece = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        source.recycle()

        val sharp = downscale(piece)
        if (sharp !== piece) piece.recycle()
        // The shader addresses the bitmap in raw pixels; keep density from scaling it.
        sharp.density = Bitmap.DENSITY_NONE
        val blurred = blur(sharp)
        blurred.density = Bitmap.DENSITY_NONE
        val screenRect = Rect(
            windowBounds.left + (left / scaleX).roundToInt(),
            windowBounds.top + (top / scaleY).roundToInt(),
            windowBounds.left + (right / scaleX).roundToInt(),
            windowBounds.top + (bottom / scaleY).roundToInt(),
        )
        return GlassBackdropFrame(sharp, blurred, screenRect, generation.incrementAndGet())
    }

    /** Keeps the texture small enough for a 60 fps shader on mid-range phones. */
    private fun downscale(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= MAX_TEXTURE) return source
        val scale = MAX_TEXTURE.toFloat() / longest
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    /** Downscale + three box passes, then bilinear upscale: cheap frosted glass. */
    private fun blur(source: Bitmap): Bitmap {
        val w = max(1, source.width / BLUR_SCALE)
        val h = max(1, source.height / BLUR_SCALE)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val radius = max(2, min(w, h) / 60)
        repeat(3) { boxBlur(pixels, w, h, radius) }
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        val out = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()
        return out
    }

    /** Separable box blur with a sliding window; O(width * height) per pass. */
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

    private fun signature(bitmap: Bitmap): Long {
        var hash = 1_125_899_906_842_628_619L
        val stepX = max(1, bitmap.width / 16)
        val stepY = max(1, bitmap.height / 16)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                hash = hash * 31 + bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    private companion object {
        const val TAG = "GlassBackdrop"
        const val MAX_TEXTURE = 900
        const val BLUR_SCALE = 2
    }
}
