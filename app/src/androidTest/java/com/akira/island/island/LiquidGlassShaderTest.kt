package com.akira.island.island

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.MainActivity
import com.akira.island.ui.screens.SettingsPreview
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Renders the real AGSL program through the activity's hardware canvas.
 *
 * This deliberately avoids the Compose test rule: its Espresso version cannot
 * talk to API 36 (`InputManager.getInstance` was removed). ActivityScenario and
 * PixelCopy give us the same rendered window without Espresso.
 *
 * The striped backdrop only appears in the output if the shader really sampled
 * and displaced the captured frame; the fallback material would just tint it.
 */
@RunWith(AndroidJUnit4::class)
class LiquidGlassShaderTest {

    @After
    fun clearBackdrop() {
        GlassBackdropState.clear()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    fun shaderRefractsTheRealBackdrop() {
        assumeTrue("AGSL needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val captured = CountDownLatch(1)
            var screenshot: Bitmap? = null
            var width = 0
            var height = 0
            var density = 0f

            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                width = decor.width
                height = decor.height
                density = activity.resources.displayMetrics.density
                require(width > 0 && height > 0) { "the test window must be laid out" }

                val sharp = stripedBackdrop(width, height)
                val small = Bitmap.createScaledBitmap(sharp, width / 24, height / 24, true)
                val blurred = Bitmap.createScaledBitmap(small, width, height, true)
                small.recycle()
                GlassBackdropState.publish(
                    GlassBackdropFrame(sharp, blurred, android.graphics.Rect(0, 0, width, height), 1L),
                )
                activity.setContent { ShaderSurface() }

                // Two animation frames: composition, then the actual draw.
                decor.postOnAnimation {
                    decor.postOnAnimation {
                        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        PixelCopy.request(
                            activity.window,
                            output,
                            { result ->
                                if (result == PixelCopy.SUCCESS) screenshot = output else output.recycle()
                                captured.countDown()
                            },
                            Handler(Looper.getMainLooper()),
                        )
                    }
                }
            }

            assertTrue("the window must be captured", captured.await(10, TimeUnit.SECONDS))
            val image = screenshot ?: error("PixelCopy failed")
            runCatching {
                val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
                File(directory, "liquid-glass-shader.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }

            // The island is 260 x 130 dp, centred in the window.
            val halfW = 130f * density
            val halfH = 65f * density
            val cx = width / 2f
            val cy = height / 2f
            var minR = 255
            var maxR = 0
            var minB = 255
            var maxB = 0
            var fringe = 0
            var samples = 0
            val left = (cx - halfW * 0.55f).toInt().coerceAtLeast(0)
            val right = (cx + halfW * 0.55f).toInt().coerceAtMost(width - 1)
            val top = (cy - halfH * 0.30f).toInt().coerceAtLeast(0)
            val bottom = (cy + halfH * 0.30f).toInt().coerceAtMost(height - 1)
            for (y in top..bottom step 3) {
                for (x in left..right) {
                    val color = image.getPixel(x, y)
                    val r = (color shr 16) and 0xFF
                    val b = color and 0xFF
                    minR = minOf(minR, r)
                    maxR = maxOf(maxR, r)
                    minB = minOf(minB, b)
                    maxB = maxOf(maxB, b)
                    if (abs(r - b) > 12) fringe++
                    samples++
                }
            }
            image.recycle()

            assertTrue("expected samples inside the island", samples > 20)
            assertTrue("red variance min=$minR max=$maxR", maxR - minR > 12)
            assertTrue("blue variance min=$minB max=$maxB", maxB - minB > 12)
            assertTrue("dispersion fringes=$fringe", fringe > 0)
        } finally {
            scenario.close()
            GlassBackdropState.clear()
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    fun settingsPreviewRendersTheShader() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val captured = CountDownLatch(1)
            var screenshot: Bitmap? = null
            var width = 0
            var height = 0
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                width = decor.width
                height = decor.height
                require(width > 0 && height > 0)
                SettingsRepo.init(activity)
                SettingsRepo.update {
                    copy(liquidGlass = true, glassShader = true, glassOpacity = 60, shadow = false)
                }
                activity.setContent {
                    Box(Modifier.fillMaxSize().background(ComposeColor(0xFF151419))) {
                        SettingsPreview(scene = PreviewScene.MUSIC)
                    }
                }
                decor.postOnAnimation {
                    decor.postOnAnimation {
                        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        PixelCopy.request(
                            activity.window,
                            output,
                            { result ->
                                if (result == PixelCopy.SUCCESS) screenshot = output else output.recycle()
                                captured.countDown()
                            },
                            Handler(Looper.getMainLooper()),
                        )
                    }
                }
            }
            assertTrue("the window must be captured", captured.await(10, TimeUnit.SECONDS))
            val image = screenshot ?: error("PixelCopy failed")
            runCatching {
                val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
                File(directory, "liquid-glass-preview.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            var colourful = 0
            for (y in 0 until image.height step 8) {
                for (x in 0 until image.width step 8) {
                    val color = image.getPixel(x, y)
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF
                    if (abs(r - g) > 20 || abs(r - b) > 20) colourful++
                }
            }
            image.recycle()
            assertTrue("the preview must show the coloured shader backdrop", colourful > 20)
        } finally {
            scenario.close()
        }
    }

    @Composable
    private fun ShaderSurface() {
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize().background(ComposeColor.Black), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(260.dp, 130.dp)
                    .islandSurface(
                        IslandSettings(liquidGlass = true, glassShader = true, glassOpacity = 55, shadow = false),
                        card = false,
                        shape = RoundedCornerShape(65.dp),
                        cornerPx = { with(density) { 65.dp.toPx() } },
                    ),
            )
        }
    }

    private fun stripedBackdrop(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val cell = 28
        var y = 0
        var rowRed = true
        while (y < height) {
            var x = 0
            var red = rowRed
            while (x < width) {
                paint.color = if (red) Color.rgb(230, 30, 40) else Color.rgb(20, 40, 230)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + cell).toFloat(), (y + cell).toFloat(), paint)
                x += cell
                red = !red
            }
            y += cell
            rowRed = !rowRed
        }
        return bitmap
    }
}
