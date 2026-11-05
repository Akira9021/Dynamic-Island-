package com.akira.island.island

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.GlassTilt
import com.akira.island.core.IslandBus
import com.akira.island.data.AppLanguage
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.MainActivity
import com.akira.island.ui.screens.SettingsPreview
import com.akira.island.update.UpdateChecker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LiquidGlassRenderingTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                SettingsRepo.init(context)
                SettingsRepo.update { IslandSettings(language = AppLanguage.RU, lastSeenVersion = UpdateChecker.currentVersion(context)) }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent(content = block) }
        compose.waitForIdle()
    }

    private fun image() = compose.onNodeWithTag("glass-surface").captureToImage().toPixelMap()
    private fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    @Test fun densityReallyControlsTransparencyAndReflectionsNeverWashOutContent() {
        val backdrop = mutableStateOf(Color.Red)
        val density = mutableStateOf(100)
        val direction = mutableStateOf(GlassTilt.Zero)
        val sheen = mutableStateOf(-1f)
        content {
            Box(Modifier.fillMaxSize().background(Color(0xFF111116)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(240.dp, 120.dp).testTag("glass-surface").background(backdrop.value)) {
                    Box(Modifier.fillMaxSize().islandSurface(
                        IslandSettings(liquidGlass = true, glassOpacity = density.value, shadow = false), false,
                        RoundedCornerShape(32.dp), tilt = { direction.value }, sheen = { sheen.value }),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(12.dp).background(Color.White))
                    }
                }
            }
        }
        val opaqueRed = image()
        // Compare backdrops with identical lighting: a moving reflection changes the tint itself.
        compose.runOnIdle { backdrop.value = Color.Blue }
        val opaqueBlue = image()
        val x = opaqueRed.width / 4
        val y = opaqueRed.height / 2
        assertTrue("Opaque glass must hide the app underneath: ${opaqueRed[x, y]} vs ${opaqueBlue[x, y]}",
            distance(opaqueRed[x, y], opaqueBlue[x, y]) < 0.01f)
        assertEquals(Color.White, opaqueRed[opaqueRed.width / 2, y])
        assertEquals(Color.White, opaqueBlue[opaqueBlue.width / 2, y])
        compose.runOnIdle { direction.value = GlassTilt(1f, -1f); sheen.value = 0.5f }
        val reflected = image()
        assertEquals(Color.White, reflected[reflected.width / 2, reflected.height / 2])
        compose.runOnIdle { density.value = 40; direction.value = GlassTilt.Zero; sheen.value = -1f }
        val transparentBlue = image()
        compose.runOnIdle { backdrop.value = Color.Red }
        val transparentRed = image()
        assertTrue("Lower density must reveal the real backdrop", distance(transparentRed[x, y], transparentBlue[x, y]) > 0.7f)
    }

    @Test fun staticGlassIgnoresMotionAndKeepsCircleAndNotchClipped() {
        val shape = mutableStateOf<Shape>(CircleShape)
        val light = mutableStateOf(GlassTilt.Zero)
        val backdrop = Color(0xFF406790)
        content {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).testTag("glass-surface").background(backdrop)) {
                    Box(Modifier.fillMaxSize().islandSurface(IslandSettings(liquidGlass = true, reduceEffects = true),
                        false, shape.value, tilt = { light.value }, sheen = { if (light.value.x > 0) 0.5f else -1f }))
                }
            }
        }
        val still = image()
        compose.runOnIdle { light.value = GlassTilt(1f, 1f) }
        val moved = image()
        assertTrue(distance(still[0, 0], backdrop) < 0.01f)
        assertTrue(distance(still[still.width / 2, still.height / 2], backdrop) > 0.1f)
        for (y in 0 until still.height step 5) for (x in 0 until still.width step 5) {
            assertTrue("Reduce effects must freeze optical motion", distance(still[x, y], moved[x, y]) < 0.01f)
        }
        compose.runOnIdle { shape.value = islandShape(32.dp, IslandSettings(notch = true, notchTopRadius = 8)) }
        val notch = image()
        assertTrue(distance(notch[0, 0], backdrop) < 0.01f)
        assertTrue(distance(notch[notch.width / 2, 0], backdrop) > 0.1f)
    }

    @Test fun previewLightFollowsFingerWithoutChangingPhoneTilt() {
        compose.runOnIdle { SettingsRepo.update { copy(liquidGlass = true, reduceEffects = false) }; IslandBus.glassTilt.value = GlassTilt.Zero }
        content {
            Box(Modifier.fillMaxSize().background(Color(0xFF151419)).padding(16.dp), contentAlignment = Alignment.Center) {
                SettingsPreview(scene = PreviewScene.IDLE)
            }
        }
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        val before = compose.onNodeWithTag("appearance-preview").captureToImage().toPixelMap()
        compose.onNodeWithTag("appearance-preview").performTouchInput { down(center); moveTo(Offset(width * 0.9f, height * 0.2f)) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("appearance-preview").captureToImage().toPixelMap()
        var difference = 0f
        for (y in 0 until before.height step 3) for (x in 0 until before.width step 3) difference += distance(before[x, y], after[x, y])
        assertTrue("The rendered reflections must respond to the drag", difference > 1f)
        compose.onNodeWithTag("appearance-preview").performTouchInput { up() }
        compose.runOnIdle { assertEquals(GlassTilt.Zero, IslandBus.glassTilt.value) }
    }

    @Test fun captureActualCapsuleMessageAndTimer() {
        compose.runOnIdle { SettingsRepo.update { copy(liquidGlass = true, reduceEffects = true) } }
        content {
            Column(Modifier.fillMaxSize().background(Color(0xFF151419)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(30.dp))
                Text("Liquid Glass · 1.7.0", color = Color.White, fontSize = 23.sp)
                SettingsPreview(scene = PreviewScene.MUSIC)
                SettingsPreview(card = true, scene = PreviewScene.MESSAGE)
                SettingsPreview(card = true, scene = PreviewScene.TIMER)
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "glass-previews").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "liquid-glass.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onAllNodesWithTag("appearance-preview").assertCountEquals(3)
    }
}
