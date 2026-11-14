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

    @Test fun islandWindowFlagsShowInitialBlurWithinFourFrames() {
        checkAttachedWindow(true)
    }

    @Test fun bubbleAndCardBlurTheirOwnRegionsWithoutABridge() {
        checkAttachedWindow(true, secondBubble = true)
    }

    private fun checkAttachedWindow(screenLayout: Boolean, secondBubble: Boolean = false) {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var foreground: Dialog? = null
        var blur: IslandBackdropBlur? = null
        var bubbleBlur: IslandBackdropBlur? = null
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
                    window!!.setLayout(if (secondBubble) 640 else 480, 280)
                    window!!.attributes = window!!.attributes.apply { gravity = Gravity.TOP or Gravity.LEFT; x = 80; y = if(screenLayout) 40 else 300 }
                }
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(1500)
            val before = instrumentation.uiAutomation.takeScreenshot()
            activity.scenario.onActivity { a ->
                blur = IslandBackdropBlur(a, foreground!!.window!!.decorView).also { it.update(Rect(0, 0, 480, 280), 48f, true) }
                if (secondBubble) bubbleBlur = IslandBackdropBlur(a, foreground!!.window!!.decorView).also {
                    // Dialog fixtures are below SystemUI, unlike an accessibility overlay.
                    // Sample actual background below the status bar, not its foreground glyphs.
                    it.update(Rect(500, 120, 560, 180), 30f, true)
                }
            }
            // Observe the material after four frames, not a late 1.5-second enablement.
            val frames = java.util.concurrent.CountDownLatch(1)
            activity.scenario.onActivity {
                android.view.Choreographer.getInstance().postFrameCallback(object : android.view.Choreographer.FrameCallback {
                    var remaining = 4
                    override fun doFrame(time: Long) {
                        if (--remaining == 0) frames.countDown()
                        else android.view.Choreographer.getInstance().postFrameCallback(this)
                    }
                })
            }
            assertTrue("Frame scheduling must finish", frames.await(15, java.util.concurrent.TimeUnit.SECONDS))
            // The legacy generic Dialog has different inset/placement rules and may need
            // calibration. Its existing functional test is not a first-frame latency test.
            // The production island flags above keep the strict four-frame check.
            if (!screenLayout) SystemClock.sleep(1500)
            instrumentation.waitForIdleSync()
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
            if (secondBubble) {
                fun section(bitmap: android.graphics.Bitmap, from: Int, to: Int): Long {
                    val y = origin[1] + 150
                    return (origin[0]+from until origin[0]+to).sumOf { x ->
                        kotlin.math.abs(Color.red(bitmap.getPixel(x,y))-Color.red(bitmap.getPixel(x+1,y))).toLong()
                    }
                }
                assertTrue("Circle blurs its own background", section(after,516,544) < section(before,516,544)/2)
                val gap=section(before,482,498)
                assertTrue("Gap fixture has contrast",gap>0)
                assertTrue("No blurred bridge between independent activities",section(after,482,498)>=gap*.95)
            }
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
            if (screenLayout && !secondBubble) {
                // Same owner and geometry: changing only strength must invalidate the window.
                activity.scenario.onActivity {
                    blur!!.update(Rect(0, 0, 480, 280), 48f, true, strengthPercent = 0)
                    assertFalse("Zero strength removes native blur", blur!!.showing)
                }
                instrumentation.waitForIdleSync(); SystemClock.sleep(500)
                val clear = instrumentation.uiAutomation.takeScreenshot()
                assertTrue("Zero restores the sharp background", contrast(clear) >= contrast(before) * .95)
                activity.scenario.onActivity {
                    blur!!.update(Rect(0, 0, 480, 280), 48f, true, strengthPercent = 100)
                    assertTrue("Changing strength recreates blur at unchanged bounds", blur!!.showing)
                }
                instrumentation.waitForIdleSync(); SystemClock.sleep(500)
                val strong = instrumentation.uiAutomation.takeScreenshot()
                assertTrue("Maximum strength blurs the actual background", contrast(strong) < contrast(before) / 2)
                clear.recycle(); strong.recycle()
            }
        } finally {
            activity.scenario.onActivity { blur?.close(); bubbleBlur?.close(); foreground?.dismiss() }
        }
    }
}
