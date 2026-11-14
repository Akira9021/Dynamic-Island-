package com.akira.island.service

import android.app.Dialog
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.akira.island.R
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun backdropAllowed(requested: Boolean, enabled: Boolean, visible: Boolean,
    screenOn: Boolean, locked: Boolean, saving: Boolean, reduced: Boolean, shape: Boolean): Boolean =
    requested && enabled && visible && screenOn && !locked && !saving && !reduced && shape

internal fun backdropTransmits(s: com.akira.island.data.IslandSettings, card: Boolean = true): Boolean {
    val color = if (card && s.expandedColorEnabled) s.expandedColor else s.color
    val alpha = ((color ushr 24) and 255).toFloat() / 255f
    val density = if (s.liquidGlass) com.akira.island.core.glassBodyDensity(s.glassOpacity / 100f)
        else s.islandOpacity.coerceIn(20, 100) / 100f
    return alpha * density < 1f
}

/** Public attached sub-window: its negative media sub-layer keeps it below its parent. */
@SuppressLint("RtlHardcoded") // Bounds are physical parent-window coordinates, not leading-edge offsets.
internal class IslandBackdropBlur(private val context: Context, private val parent: View) : AutoCloseable {
    companion object {
        private val rejection = MutableStateFlow(false)
        val windowRejected = rejection.asStateFlow()
    }
    private val manager = context.getSystemService(WindowManager::class.java)
    private var dialog: Dialog? = null
    private val background = GradientDrawable().apply { setColor(Color.argb(1, 255, 255, 255)) }
    private var bounds = Rect()
    private var corner = 0f
    private var topCorner = 0f
    private var blurStrength = 50
    private fun requestedBlurRadius(): Int =
        (glassBlurRadiusDp(blurStrength) * context.resources.displayMetrics.density).toInt()
    private var wanted = false
    private var failed = false
    private var closed = false
    private var listening = false
    private var corrections = 0
    private var appliedBlurRadius = -1
    private var positionBiasX = 0
    private var positionBiasY = 0
    private fun setBlurRadius(window: android.view.Window, radius: Int) {
        if (Build.VERSION.SDK_INT >= 31 && appliedBlurRadius != radius) {
            window.setBackgroundBlurRadius(radius)
            appliedBlurRadius = radius
        }
    }
    // WindowManager may apply a different parent/content origin to an accessibility sub-window.
    // Keep native blur off until the *actual* frame agrees with the Compose card on screen.
    private val align = ViewTreeObserver.OnPreDrawListener {
        val w = dialog?.window
        if (w != null && wanted && !closed && Build.VERSION.SDK_INT >= 31) {
            val screen = IntArray(2); val local = IntArray(2); val actual = IntArray(2)
            parent.getLocationOnScreen(screen); parent.getLocationInWindow(local)
            w.decorView.getLocationOnScreen(actual)
            positionBiasX = actual[0] - (screen[0] - local[0]) - w.attributes.x
            positionBiasY = actual[1] - (screen[1] - local[1]) - w.attributes.y
            val dx = screen[0] - local[0] + bounds.left - actual[0]
            val dy = screen[1] - local[1] + bounds.top - actual[1]
            val sized = w.decorView.width == bounds.width() && w.decorView.height == bounds.height()
            if (dx == 0 && dy == 0 && sized) {
                setBlurRadius(w, requestedBlurRadius())
            } else {
                setBlurRadius(w, 0)
                if (corrections++ < 2) w.attributes = w.attributes.apply {
                    x += dx; y += dy; width = bounds.width(); height = bounds.height()
                }
            }
        }
        true
    }
    private val listener = Consumer<Boolean> { render() }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = render()
        override fun onViewDetachedFromWindow(v: View) {
            runCatching { dialog?.dismiss() }; dialog = null
        }
    }

    init {
        rejection.value = false
        parent.addOnAttachStateChangeListener(attach)
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener) }
                .onSuccess { listening = true }
        }
    }

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 31 && !failed &&
        runCatching { manager.isCrossWindowBlurEnabled }.getOrDefault(false)
    val showing: Boolean get() = dialog?.isShowing == true && dialog?.window?.decorView?.visibility == View.VISIBLE

    fun update(area: Rect, cornerPx: Float, enabled: Boolean, topCornerPx: Float = cornerPx, strengthPercent: Int = 50) {
        if (closed) return
        val bottom = cornerPx.coerceAtLeast(0f)
        val top = topCornerPx.coerceAtLeast(0f)
        val horizontalInset = backdropHorizontalInset(area.width(), area.height(), bottom, top)
        val target = Rect(area).apply { inset(horizontalInset, 0) }
        val clippedBottom = if (horizontalInset > 0) 0f else bottom
        val clippedTop = if (horizontalInset > 0) 0f else top
        val strength = strengthPercent.coerceIn(0, 100)
        if (bounds == target && corner == clippedBottom && topCorner == clippedTop && wanted == enabled && blurStrength == strength) return
        bounds.set(target); corner = clippedBottom; topCorner = clippedTop; wanted = enabled; blurStrength = strength
        render()
    }

    private fun render() {
        if (closed) return
        if (!wanted || blurStrength == 0 || bounds.isEmpty || !supported || !parent.isAttachedToWindow) {
            dialog?.hide(); return
        }
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            val d = dialog ?: Dialog(context, R.style.IslandBackdrop).also {
                appliedBlurRadius = -1
                val w = checkNotNull(it.window)
                w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA)
                w.setBackgroundDrawable(background)
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                it.setContentView(View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS })
                w.decorView.viewTreeObserver.addOnPreDrawListener(align)
                dialog = it
            }
            val w = checkNotNull(d.window)
            // Public Android blur accepts only a round-rect outline. The larger corner
            // inscribes its blur in a notch's silhouette instead of spilling outside it.
            background.cornerRadius = maxOf(topCorner, corner)
            corrections = 0
            w.attributes = w.attributes.apply {
                token = parent.windowToken
                gravity = Gravity.TOP or Gravity.LEFT
                width = bounds.width(); height = bounds.height(); x = bounds.left - positionBiasX; y = bounds.top - positionBiasY
                format = PixelFormat.TRANSLUCENT
                title = "DynamicIslandBackdrop"
            }
            // Attached windows must not apply a second status/navigation-bar inset to parent-local bounds.
            w.setDecorFitsSystemWindows(false)
            w.decorView.setPadding(0, 0, 0, 0)
            w.attributes = w.attributes.apply {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            // Blur is the initial material, not a second effect enabled after the glass appears.
            // The pre-draw alignment guard still suppresses it if the platform places a frame wrong.
            setBlurRadius(w, requestedBlurRadius())
            d.show()
        }.onFailure {
            failed = true
            rejection.value = true
            runCatching { dialog?.dismiss() }; dialog = null
        }
    }

    fun hide() { wanted = false; dialog?.hide() }

    override fun close() {
        if (closed) return
        closed = true
        parent.removeOnAttachStateChangeListener(attach)
        if (Build.VERSION.SDK_INT >= 31 && listening) runCatching { manager.removeCrossWindowBlurEnabledListener(listener) }
        runCatching { dialog?.dismiss() }; dialog = null
    }
}
