package com.akira.island.island

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.GlassTilt
import com.akira.island.data.AppLanguage
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.IslandTheme
import com.akira.island.ui.MainActivity
import com.akira.island.update.UpdateChecker
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GlassOpticsRenderingTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var savedSettings: IslandSettings
    private val seed = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            instrumentation.runOnMainSync {
                SettingsRepo.init(context); savedSettings = SettingsRepo.current
                SettingsRepo.update { IslandSettings(language = AppLanguage.RU, autoUpdateCheck = false,
                    lastSeenVersion = UpdateChecker.currentVersion(context)) }
            }
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { SettingsRepo.update { savedSettings } }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)
    private val marker = Color(0xFF8A2BE2)

    private fun content(block: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent { IslandTheme { block() } } }
        compose.waitForIdle()
    }

    @Composable
    private fun Surface(
        s: IslandSettings, backdrop: Color = Color(0xFF244060), shape: Shape = RoundedCornerShape(30.dp),
        width: Dp = 240.dp, height: Dp = 120.dp, rawOpacity: Float? = null,
        tilt: () -> GlassTilt = { GlassTilt.Zero }, sheen: () -> Float = { -1f },
        layouts: AtomicInteger? = null,
        patternedBackdrop: Boolean = false,
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF151419)), contentAlignment = Alignment.Center) {
            val pattern = if (patternedBackdrop) Modifier.drawWithCache {
                val gradient = Brush.linearGradient(listOf(Color(0xFF193772), Color(0xFFDA4B78), Color(0xFF35A995)))
                onDrawBehind {
                    drawRect(gradient)
                    drawCircle(Color(0xFF9EDDC4), size.height * 0.28f, Offset(size.width * 0.72f, size.height * 0.6f))
                }
            } else Modifier
            Box(Modifier.size(width, height).testTag("optics-surface").background(backdrop).then(pattern)) {
                val material = if (rawOpacity == null) Modifier.islandSurface(s, false, shape, tilt = tilt, sheen = sheen)
                    else Modifier.glassSurface(shape, Color(s.color), rawOpacity, tilt, sheen)
                Box(Modifier.fillMaxSize().then(material)) {
                    Box(Modifier.align(Alignment.Center).size(12.dp).background(marker))
                    IslandTextTheme(s) {
                        Text("Glass", fontSize = 18.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).testTag("optics-label"),
                            onTextLayout = { layouts?.incrementAndGet() })
                    }
                }
            }
        }
    }

    private fun image() = compose.onNodeWithTag("optics-surface").captureToImage().toPixelMap()
    private fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
    private fun differences(a: PixelMap, b: PixelMap): Int {
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        var changed = 0
        for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) if (distance(a[x, y], b[x, y]) > 0.005f) changed++
        return changed
    }
    private fun markerSurvives(pixels: PixelMap) { assertTrue(distance(marker, pixels[pixels.width / 2, pixels.height / 2]) < 0.005f) }
    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "glass-optics-previews").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("optics-surface").captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun analyticalLensCompilesAndDefaultDensityTransmitsTheRealBackground() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        if (Build.VERSION.SDK_INT >= 33) assertNotNull(GlassLensShader())
        val backdrop = mutableStateOf(Color.Red)
        val pattern = mutableStateOf(false)
        content { Surface(IslandSettings(liquidGlass = true, reduceEffects = true), backdrop.value,
            patternedBackdrop = pattern.value) }
        val red = image()
        compose.runOnIdle { backdrop.value = Color.Blue }
        val blue = image()
        assertTrue("Default optical density must clearly transmit the actual backdrop",
            distance(red[red.width / 4, red.height * 2 / 3], blue[blue.width / 4, blue.height * 2 / 3]) > 0.95f)
        markerSurvives(red); markerSurvives(blue)
        compose.runOnIdle { pattern.value = true }
        screenshot("lens-pattern-default")
    }

    @Test fun densityAndUserColorsKeepTheirMeaningWithoutTintingForeground() {
        val settings = mutableStateOf(IslandSettings(liquidGlass = true, shadow = false, glassOpacity = 100))
        val backdrop = mutableStateOf(Color.Red)
        content { Surface(settings.value, backdrop.value) }
        val opaqueRed = image()
        compose.runOnIdle { backdrop.value = Color.Blue }
        val opaqueBlue = image()
        val x = opaqueRed.width / 4; val y = opaqueRed.height * 2 / 3
        assertTrue(distance(opaqueRed[x, y], opaqueBlue[x, y]) < 0.005f)
        markerSurvives(opaqueRed); markerSurvives(opaqueBlue)
        compose.runOnIdle { settings.value = settings.value.copy(glassOpacity = 40) }
        val blue = image()
        compose.runOnIdle { backdrop.value = Color.Red }
        val red = image()
        assertTrue("40% density must transmit the real backdrop", distance(blue[x, y], red[x, y]) > 0.7f)
        compose.runOnIdle {
            settings.value = settings.value.copy(color = 0xFFF3F4F7, textColorEnabled = true, textColor = 0xFF101018,
                outline = true, outlineFromArt = false, outlineColor = 0xFFFFA000, outlineWidth = 2f)
        }
        val light = image(); markerSurvives(light)
        compose.onNodeWithTag("optics-label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
            val layouts = mutableListOf<TextLayoutResult>(); assertTrue(get(layouts))
            assertEquals(Color(0xFF101018), layouts.single().layoutInput.style.color)
        }
        val goldEdge = (0 until minOf(8, light.height)).any { y ->
            val edge = light[light.width / 2, y]
            edge.red > 0.9f && edge.green > 0.45f && edge.blue < 0.2f
        }
        assertTrue("The user's own outline must stay above the material at any pixel density", goldEdge)
        screenshot("light-user-colors")
    }

    @Test fun cachedLightingMovesWithoutRemeasuringText() {
        val direction = mutableStateOf(GlassTilt.Zero)
        val layouts = AtomicInteger()
        content { Surface(IslandSettings(liquidGlass = true, shadow = false), tilt = { direction.value }, layouts = layouts) }
        val neutral = image(); val initialLayouts = layouts.get()
        assertTrue(initialLayouts > 0)
        compose.runOnIdle { direction.value = GlassTilt(-1f, 1f) }
        val left = image(); screenshot("light-left")
        compose.runOnIdle { direction.value = GlassTilt(1f, -1f) }
        val right = image(); screenshot("light-right")
        assertTrue("Cached shader must react to changed light", differences(neutral, left) > 50)
        assertTrue(differences(left, right) > 50)
        assertEquals("Light movement should only invalidate drawing", initialLayouts, layouts.get())
        markerSurvives(left); markerSurvives(right)
    }

    @Test fun aSweepChangesOnlyTheRimAndHasNoEndpointFlash() {
        val phase = mutableStateOf(-1f)
        content { Surface(IslandSettings(liquidGlass = true, shadow = false), sheen = { phase.value }) }
        val stopped = image()
        compose.runOnIdle { phase.value = 0f }; assertEquals(0, differences(stopped, image()))
        compose.runOnIdle { phase.value = 1f }; assertEquals(0, differences(stopped, image()))
        compose.runOnIdle { phase.value = 0.5f }
        val middle = image()
        assertTrue("A travelling edge reflection must be visible", differences(stopped, middle) > 20)
        val x = middle.width / 4; val y = middle.height * 2 / 3
        assertTrue("The center must not flash white", distance(stopped[x, y], middle[x, y]) < 0.005f)
        markerSurvives(middle)
        compose.runOnIdle { phase.value = -1f }; assertEquals(0, differences(stopped, image()))
    }

    @Test fun circleAndCameraNotchStayClippedAndInvalidOpacityUsesTheSameFallback() {
        val shape = mutableStateOf<Shape>(CircleShape)
        val opacity = mutableStateOf(0.68f)
        val backdrop = Color(0xFF406790)
        content { Surface(IslandSettings(liquidGlass = true), backdrop, shape.value, 160.dp, 160.dp, rawOpacity = opacity.value) }
        val reference = image()
        assertTrue(distance(reference[0, 0], backdrop) < 0.005f)
        compose.runOnIdle { opacity.value = Float.NaN }; assertEquals(0, differences(reference, image()))
        compose.runOnIdle { opacity.value = Float.POSITIVE_INFINITY }; assertEquals(0, differences(reference, image()))
        compose.runOnIdle { opacity.value = 0.4f }
        val minimum = image()
        compose.runOnIdle { opacity.value = -42f }; assertEquals(0, differences(minimum, image()))
        compose.runOnIdle { shape.value = islandShape(32.dp, IslandSettings(notch = true, notchTopRadius = 8)); opacity.value = 0.68f }
        val notch = image()
        assertTrue(distance(notch[0, 0], backdrop) < 0.005f)
        assertTrue(distance(notch[notch.width / 2, 0], backdrop) > 0.05f)
        markerSurvives(notch)
        screenshot("notch")
    }

    @Test fun disabledGlassAndReducedEffectsNeverReadAnimatedLight() {
        val settings = mutableStateOf(IslandSettings(liquidGlass = false, shadow = false))
        content { Surface(settings.value, tilt = { error("Animated light must be gated") }, sheen = { error("Sweep must be gated") }) }
        markerSurvives(image())
        compose.runOnIdle { settings.value = settings.value.copy(liquidGlass = true, reduceEffects = true) }
        val static = image(); markerSurvives(static)
        compose.runOnIdle { settings.value = settings.value.copy(bottomGradient = 20) }
        markerSurvives(image())
    }

    @Test fun translucentShadowsLeaveTheInteriorUnchanged() {
        val settings = mutableStateOf(IslandSettings(liquidGlass = true, shadow = false, glassOpacity = 40,
            color = 0xFFF3F4F7, textColorEnabled = true, textColor = 0xFF101018))
        content { Surface(settings.value, Color.Red) }
        val normal = image(); screenshot("shadow-normal")
        compose.runOnIdle { settings.value = settings.value.copy(reduceEffects = true) }
        val reduced = image(); screenshot("shadow-reduced")
        markerSurvives(normal); markerSurvives(reduced)
        fun maximumInteriorJump(pixels: PixelMap): Float {
            val margin = (compose.activity.resources.displayMetrics.density * 8).toInt().coerceAtLeast(2)
            val y = pixels.height * 3 / 4
            var maximum = 0f
            for (x in margin until pixels.width - margin) maximum = maxOf(maximum, distance(pixels[x - 1, y], pixels[x, y]))
            return maximum
        }
        val normalJump = maximumInteriorJump(normal)
        val reducedJump = maximumInteriorJump(reduced)
        compose.runOnIdle {
            settings.value = settings.value.copy(liquidGlass = false, shadow = true, islandOpacity = 40, reduceEffects = false)
        }
        val flat = image(); screenshot("shadow-flat-normal")
        compose.runOnIdle { settings.value = settings.value.copy(reduceEffects = true) }
        val flatReduced = image(); screenshot("shadow-flat-reduced")
        markerSurvives(flat); markerSurvives(flatReduced)
        val flatJump = maximumInteriorJump(flat)
        val flatReducedJump = maximumInteriorJump(flatReduced)
        fun assertSameInterior(a: PixelMap, b: PixelMap) {
            for (y in a.height / 4 until a.height * 3 / 4 step 2)
                for (x in a.width / 8 until a.width * 7 / 8 step 2)
                    assertTrue("A shadow must never tint the transparent interior at $x,$y",
                        distance(a[x, y], b[x, y]) < 0.015f)
        }
        assertSameInterior(normal, reduced)
        assertSameInterior(flat, flatReduced)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "glass-optics-previews").apply { mkdirs() }
        File(dir, "shadow-metrics.json").writeText("{\"normal_max_neighbor_delta\":$normalJump,\"reduced_max_neighbor_delta\":$reducedJump,\"flat_max_neighbor_delta\":$flatJump,\"flat_reduced_max_neighbor_delta\":$flatReducedJump}")
        assertEquals("The former rectangular boundary must disappear", reducedJump, normalJump, 0.015f)
        assertEquals(flatReducedJump, flatJump, 0.015f)
    }

    @Composable
    private fun ShadowFrame(s: IslandSettings, shape: Shape, width: Dp = 200.dp, height: Dp = 80.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width + 48.dp, height + 48.dp).background(Color(0xFFB9CBE0))
                .testTag("shadow-frame"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width, height).testTag("shadow-body").islandSurface(s, false, shape),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).background(marker))
                }
            }
        }
    }

    private fun shadowImage() = compose.onNodeWithTag("shadow-frame").captureToImage().toPixelMap()

    private fun assertExternalShadow(normal: PixelMap, reduced: PixelMap) {
        val margin = (24 * compose.activity.resources.displayMetrics.density).toInt()
        val right = normal.width - margin
        val mid = normal.height / 2
        val near = distance(normal[right + 1, mid], reduced[right + 1, mid])
        val farX = normal.width - 2
        assertTrue("A soft shadow must remain visible outside the body", near > 0.015f)
        assertTrue("The shadow must fade before the frame boundary",
            distance(normal[farX, mid], reduced[farX, mid]) < 0.005f)
        markerSurvives(normal); markerSurvives(reduced)
    }

    @Test fun shadowSettingsPreserveTransparentOpaqueAndReducedModes() {
        val variants = listOf(
            IslandSettings(liquidGlass = true, glassOpacity = 40, shadow = false),
            IslandSettings(liquidGlass = true, glassOpacity = 100, shadow = false),
            IslandSettings(shadow = true, islandOpacity = 20),
            IslandSettings(shadow = true, islandOpacity = 100),
            IslandSettings(liquidGlass = true, glassOpacity = 100, color = 0x80000000),
        )
        val settings = mutableStateOf(variants.first())
        content { ShadowFrame(settings.value, RoundedCornerShape(30.dp)) }
        for (variant in variants) {
            compose.runOnIdle { settings.value = variant }
            val bounds = compose.onNodeWithTag("shadow-body").getUnclippedBoundsInRoot()
            val normal = shadowImage()
            compose.runOnIdle { settings.value = variant.copy(reduceEffects = true) }
            val reduced = shadowImage()
            assertExternalShadow(normal, reduced)
            assertEquals("A drawing effect must not change the body or touch bounds", bounds,
                compose.onNodeWithTag("shadow-body").getUnclippedBoundsInRoot())
        }
        compose.runOnIdle { settings.value = IslandSettings(shadow = false, islandOpacity = 20) }
        val disabled = shadowImage()
        compose.runOnIdle { settings.value = settings.value.copy(reduceEffects = true) }
        assertEquals("The ordinary shadow switch must still disable it", 0, differences(disabled, shadowImage()))
    }

    @Test fun externalShadowFollowsCapsuleBubbleNotchAndResizing() {
        val shapes = listOf(RoundedCornerShape(50), CircleShape,
            islandShape(32.dp, IslandSettings(notch = true, notchTopRadius = 8)), RoundedCornerShape(12.dp))
        val sizes = listOf(200.dp to 40.dp, 48.dp to 48.dp, 216.dp to 120.dp, 144.dp to 44.dp)
        val index = mutableStateOf(0)
        val reduced = mutableStateOf(false)
        content {
            ShadowFrame(IslandSettings(liquidGlass = true, glassOpacity = 40, reduceEffects = reduced.value),
                shapes[index.value], sizes[index.value].first, sizes[index.value].second)
        }
        for (i in shapes.indices) {
            compose.runOnIdle { index.value = i; reduced.value = false }
            val normal = shadowImage()
            compose.runOnIdle { reduced.value = true }
            assertExternalShadow(normal, shadowImage())
        }
    }
}
