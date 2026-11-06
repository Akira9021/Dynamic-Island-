package com.akira.island.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Notice
import com.akira.island.core.GlassTilt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.AppearancePreview
import com.akira.island.island.GlassBackdropFrame
import com.akira.island.island.IslandType
import com.akira.island.island.LocalGlassPreviewBackdrop
import com.akira.island.island.PreviewScene
import com.akira.island.island.islandSurface
import com.akira.island.ui.Ui

/** Pinned local examples do not post events or change any live playback/notification state. */
@Composable
fun SettingsPreview(
    card: Boolean = false,
    scene: PreviewScene = PreviewScene.MUSIC,
    onScene: ((PreviewScene) -> Unit)? = null,
    event: Notice? = null,
    onExpand: (() -> Unit)? = null,
    eventShown: Boolean = true,
    notificationStyle: Boolean = false,
    twoActivities: Boolean = false,
    respectVisibility: Boolean = false,
) {
    val s by SettingsRepo.state.collectAsState()
    val context = LocalContext.current
    var light by remember { mutableStateOf(GlassTilt.Zero) }
    val lightX by animateFloatAsState(light.x, spring(dampingRatio = 1f, stiffness = 250f), label = "glassLightX")
    val lightY by animateFloatAsState(light.y, spring(dampingRatio = 1f, stiffness = 250f), label = "glassLightY")
    LaunchedEffect(s.liquidGlass, s.reduceEffects) { light = GlassTilt.Zero }
    LaunchedEffect(s) { IslandType.apply(context, s) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF464361), Color(0xFF262B3A)))).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Живой пример", "Live preview"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (onExpand != null) TextButton(onClick = onExpand, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (card) tr("Свернуть", "Collapse") else tr("Раскрыть", "Expand"), color = Color.White, fontSize = 12.sp)
            } else Text(if (event != null) tr("Пример события", "Sample event") else if (twoActivities && scene != PreviewScene.IDLE && scene != PreviewScene.MESSAGE) tr("Музыка + таймер", "Music + timer") else if (card) tr("Раскрытая карточка", "Expanded card") else tr("Капсула", "Capsule"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(if (card) 155.dp else 85.dp), contentAlignment = Alignment.Center) {
            // Preview the real dimensions, then scale down only to fit the sample board.
            if (!eventShown || respectVisibility && scene == PreviewScene.IDLE && s.hideIdle) Text(tr("Пример скрыт", "Preview hidden"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            else Layout(content = {
                CompositionLocalProvider(LocalGlassPreviewBackdrop provides PreviewGlassBackdrop) {
                    AppearancePreview(s, card, scene, event, notificationStyle, twoActivities,
                        light = GlassTilt(lightX, lightY),
                        onLight = if (s.liquidGlass && !s.reduceEffects) ({ light = it }) else null)
                }
            }) { measurables, constraints ->
                val max = 600.dp.roundToPx()
                val p = measurables.single().measure(Constraints(maxWidth = max, maxHeight = 600.dp.roundToPx()))
                val k = minOf(1f, constraints.maxWidth.toFloat() / p.width.coerceAtLeast(1), constraints.maxHeight.toFloat() / p.height.coerceAtLeast(1))
                val w = (p.width * k).toInt().coerceAtLeast(1)
                val h = (p.height * k).toInt().coerceAtLeast(1)
                layout(w, h) { p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) } }
            }
        }
        if (onScene != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(PreviewScene.IDLE, PreviewScene.MUSIC, PreviewScene.MESSAGE, PreviewScene.TIMER).forEach { candidate ->
                TextButton(onClick = { onScene(candidate) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text(when (candidate) {
                        PreviewScene.IDLE -> tr("Часы", "Clock")
                        PreviewScene.MUSIC -> tr("Музыка", "Music")
                        PreviewScene.MESSAGE -> tr("Сообщение", "Message")
                        PreviewScene.TIMER -> tr("Таймер", "Timer")
                    }, color = if (scene == candidate) Ui.Primary else Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
        if (s.liquidGlass) Text(
            if (s.reduceEffects) tr("Статичное стекло: включено «Уменьшить эффекты»", "Static glass: Reduce effects is on")
            else tr("Проведите по стеклу — блики следуют за пальцем", "Drag across the glass to move the reflections"),
            color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, lineHeight = 14.sp,
        )
    }
}

/** A large, colourful sample of the material, closer to the Apple reference shots. */
@Composable
fun LiquidGlassDemo() {
    val density = LocalDensity.current
    val settings = remember {
        IslandSettings(liquidGlass = true, glassShader = true, glassOpacity = 60, shadow = false)
    }
    CompositionLocalProvider(LocalGlassPreviewBackdrop provides PreviewGlassBackdrop) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF151419))) {
            Text(
                "Liquid Glass",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
            Box(Modifier.fillMaxWidth().height(195.dp).clip(RoundedCornerShape(24.dp))) {
                Image(
                    bitmap = PreviewGlassBackdrop.sharp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopStart,
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(250.dp, 130.dp)
                        .islandSurface(
                            settings,
                            card = false,
                            shape = RoundedCornerShape(36.dp),
                            cornerPx = { with(density) { 36.dp.toPx() } },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Liquid Glass", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * The settings preview must show the shader, not the old Compose material. A
 * synthetic gradient with bright bars gives the lens something to bend without
 * depending on the accessibility screenshot.
 */
private val PreviewGlassBackdrop: GlassBackdropFrame by lazy { buildPreviewGlassBackdrop() }

private fun buildPreviewGlassBackdrop(): GlassBackdropFrame {
    val w = 1024
    val h = 512
    val sharp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sharp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(
            0xFFBFD8FF.toInt(),
            0xFF4C7DFF.toInt(),
            0xFF1D2B8F.toInt(),
            0xFFB23A48.toInt(),
        ),
        floatArrayOf(0f, 0.35f, 0.7f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    fun blob(cx: Float, cy: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(color, color and 0x00FFFFFF),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    // Distinct soft colour blobs give the lens something to bend.
    blob(w * 0.15f, h * 0.35f, w * 0.30f, 0xCCFF6B4A.toInt())
    blob(w * 0.72f, h * 0.20f, w * 0.28f, 0xCC8B5CF6.toInt())
    blob(w * 0.55f, h * 0.80f, w * 0.34f, 0xCC4CC9F0.toInt())
    blob(w * 0.90f, h * 0.65f, w * 0.22f, 0xCCFFD166.toInt())
    blob(w * 0.35f, h * 0.12f, w * 0.20f, 0x99FFFFFF.toInt())

    // A soft diagonal light band, not a hard stripe.
    paint.shader = LinearGradient(
        0f, h.toFloat(), w.toFloat(), 0f,
        intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF),
        floatArrayOf(0.25f, 0.5f, 0.75f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    sharp.density = Bitmap.DENSITY_NONE
    val small = Bitmap.createScaledBitmap(sharp, w / 8, h / 8, true)
    val blurred = Bitmap.createScaledBitmap(small, w, h, true)
    small.recycle()
    blurred.density = Bitmap.DENSITY_NONE
    return GlassBackdropFrame(sharp, blurred, android.graphics.Rect(0, 0, w, h), 1L, local = true)
}
