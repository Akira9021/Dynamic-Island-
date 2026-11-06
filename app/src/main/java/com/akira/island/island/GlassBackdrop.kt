package com.akira.island.island

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
)

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
