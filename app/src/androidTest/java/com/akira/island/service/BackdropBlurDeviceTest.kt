package com.akira.island.service

import android.app.Dialog
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.R
import com.akira.island.ui.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SuppressLint("RtlHardcoded") // Test fixtures use explicit physical screen coordinates.
class BackdropBlurDeviceTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun attachedWindowBlursBackgroundWithoutCoveringItsParent() {
        checkAttachedWindow(false)
    }

    @Test fun screenLayoutParentWithOffsetKeepsBlurInsideTheCard() {
        checkAttachedWindow(true)
    }

    private fun checkAttachedWindow(screenLayout: Boolean) {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var foreground: Dialog? = null
        var blur: IslandBackdropBlur? = null
        try {
            activity.scenario.onActivity { a ->
                assumeTrue(a.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled)
                a.setContentView(object : View(a) {
                    val paint = Paint()
                    override fun onDraw(canvas: Canvas) {
                        for (x in 0 until width step 8) {
                            paint.color = if (x % 16 == 0) Color.WHITE else Color.BLACK
                            canvas.drawRect(x.toFloat(), 0f, x + 8f, height.toFloat(), paint)
                        }
                    }
                })
                foreground = Dialog(a, R.style.IslandBackdrop).apply {
                    setContentView(TextView(a).apply {
                        text = "FOREGROUND"; textSize = 22f; setTextColor(Color.RED)
                        setBackgroundColor(Color.TRANSPARENT)
                    })
                    window!!.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    if (screenLayout) {
                        window!!.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                        window!!.setDecorFitsSystemWindows(false)
                        window!!.attributes = window!!.attributes.apply {
                            setFitInsetsTypes(0)
                            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        }
                    }
                    show()
                    window!!.setLayout(480, 280)
                    window!!.attributes = window!!.attributes.apply { gravity = Gravity.TOP or Gravity.LEFT; x = 80; y = if(screenLayout) 40 else 300 }
                }
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(1500)
            val before = instrumentation.uiAutomation.takeScreenshot()
            activity.scenario.onActivity { a ->
                blur = IslandBackdropBlur(a, foreground!!.window!!.decorView).also { it.update(Rect(0, 0, 480, 280), 48f, true) }
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(1500)
            val after = instrumentation.uiAutomation.takeScreenshot()
            val origin = IntArray(2)
            activity.scenario.onActivity {
                assertTrue("Attached blur window must be accepted", blur!!.showing)
                foreground!!.window!!.decorView.getLocationOnScreen(origin)
            }
            fun contrast(bitmap: android.graphics.Bitmap): Long {
                var total = 0L
                val y = (origin[1] + 180).coerceIn(0, bitmap.height - 1)
                for (x in origin[0] + 80 until minOf(origin[0] + 400, bitmap.width - 1))
                    total += kotlin.math.abs(Color.red(bitmap.getPixel(x, y)) - Color.red(bitmap.getPixel(x + 1, y)))
                return total
            }
            val dir = File(instrumentation.targetContext.filesDir, "backdrop-probe").apply { mkdirs() }
            for ((name, bitmap) in listOf("before" to before, "after" to after))
                File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "result.txt").writeText("before=${contrast(before)} after=${contrast(after)} origin=${origin.toList()}")
            assertTrue("Actual background stripes must lose contrast", contrast(after) < contrast(before) / 2)
            // A separate blurred strip below the card is a regression even if its interior looks right.
            val below = origin[1] + 292
            if (below < before.height) {
                fun outsideContrast(bitmap: android.graphics.Bitmap): Long {
                    var total = 0L
                    for (x in origin[0] + 80 until minOf(origin[0] + 400, bitmap.width - 1))
                        total += kotlin.math.abs(Color.red(bitmap.getPixel(x, below)) - Color.red(bitmap.getPixel(x + 1, below)))
                    return total
                }
                val clear = outsideContrast(before)
                assertTrue("Outside fixture must contain visible background stripes", clear > 1000)
                assertTrue("Blur must stay inside its card instead of leaving a band below it", outsideContrast(after) >= clear * .95)
            }
            var redBefore = 0; var redAfter = 0
            var peak = 0
            for (y in origin[1] until minOf(origin[1] + 100, before.height))
                for (x in origin[0] until minOf(origin[0] + 480, before.width)) {
                    val pixel = before.getPixel(x, y)
                    if (Color.green(pixel) == 0 && Color.blue(pixel) == 0) peak = maxOf(peak, Color.red(pixel))
                }
            for (y in origin[1] until minOf(origin[1] + 100, before.height))
                for (x in origin[0] until minOf(origin[0] + 480, before.width)) {
                    val pixel = before.getPixel(x, y)
                    if (Color.red(pixel) == peak && Color.green(pixel) == 0 && Color.blue(pixel) == 0) {
                        redBefore++
                        if (after.getPixel(x, y) == pixel) redAfter++
                    }
                }
            assertTrue("Foreground fixture must contain sharp red text", redBefore > 100)
            assertEquals("Blur must not cover the foreground window", redBefore, redAfter)
        } finally {
            activity.scenario.onActivity { blur?.close(); foreground?.dismiss() }
        }
    }
}
