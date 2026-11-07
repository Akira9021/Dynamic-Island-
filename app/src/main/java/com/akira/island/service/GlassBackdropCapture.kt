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
import com.akira.island.island.blurGlassBackdrop
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
 * is enough for a status-bar island: the background there is mostly static. In
 * Battery Saver the capture cadence is reduced, but the glass remains rendered.
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

    @Volatile private var rect = Rect()
    @Volatile private var enabled = false

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    runCatching { captureOnce() }
                }
                // The accessibility screenshot API is rate-limited to roughly
                // three captures per second; 340 ms is the fastest sustainable
                // cadence, so the backdrop updates in near real time.
                delay(if (failures >= 3) 2_000L else 340L)
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
        if (!runCatching { service.windows }.isSuccess) return@withLock

        val window = findWindow(area) ?: return@withLock
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return@withLock

        val shot = screenshot(window.id)
        if (shot == null) {
            failures++
            if (failures == 1 || failures % 10 == 0) {
                Log.w(TAG, "screenshot of window ${window.id} failed ($failures); caps=${service.serviceInfo?.capabilities}")
            }
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

        // Dynamic content must never be suppressed by a sparse bitmap hash:
        // a small icon can move under the lens while missing all hash samples.
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
        val blurred = blurGlassBackdrop(sharp)
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

    private companion object {
        const val TAG = "GlassBackdrop"
        const val MAX_TEXTURE = 900
    }
}
