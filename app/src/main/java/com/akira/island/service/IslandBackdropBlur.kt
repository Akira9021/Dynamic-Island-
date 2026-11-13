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
import android.view.WindowManager
import com.akira.island.R
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun backdropAllowed(requested: Boolean, enabled: Boolean, visible: Boolean, expanded: Boolean,
    screenOn: Boolean, locked: Boolean, saving: Boolean, reduced: Boolean, shape: Boolean): Boolean =
    requested && enabled && visible && expanded && screenOn && !locked && !saving && !reduced && shape

internal fun backdropTransmits(s: com.akira.island.data.IslandSettings): Boolean {
    val color = if (s.expandedColorEnabled) s.expandedColor else s.color
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
    private var wanted = false
    private var failed = false
    private var closed = false
    private var listening = false
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

    fun update(area: Rect, cornerPx: Float, enabled: Boolean) {
        if (closed) return
        if (bounds == area && corner == cornerPx && wanted == enabled) return
        bounds.set(area); corner = cornerPx.coerceAtLeast(0f); wanted = enabled
        render()
    }

    private fun render() {
        if (closed) return
        if (!wanted || bounds.isEmpty || !supported || !parent.isAttachedToWindow) {
            dialog?.hide(); return
        }
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            val d = dialog ?: Dialog(context, R.style.IslandBackdrop).also {
                val w = checkNotNull(it.window)
                w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA)
                w.setBackgroundDrawable(background)
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                it.setContentView(View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS })
                dialog = it
            }
            val w = checkNotNull(d.window)
            background.cornerRadius = corner
            w.attributes = w.attributes.apply {
                token = parent.windowToken
                gravity = Gravity.TOP or Gravity.LEFT
                width = bounds.width(); height = bounds.height(); x = bounds.left; y = bounds.top
                format = PixelFormat.TRANSLUCENT
                title = "DynamicIslandBackdrop"
            }
            // Attached windows must not apply a second status/navigation-bar inset to parent-local bounds.
            w.setDecorFitsSystemWindows(false)
            w.decorView.setPadding(0, 0, 0, 0)
            w.attributes = w.attributes.apply { setFitInsetsTypes(0) }
            w.setBackgroundBlurRadius((28 * context.resources.displayMetrics.density).toInt())
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
