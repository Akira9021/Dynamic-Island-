package com.akira.island.island

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.akira.island.core.GlassTilt
import com.akira.island.core.glassSheenStrength

internal val LocalGlassTilt = staticCompositionLocalOf<GlassTilt?> { null }

/**
 * A tinted optical surface: quiet center, a thin specular lip and soft inward caustics.
 * This does not sample other apps' windows. All light is behind the content; it never
 * blurs text or washes out controls. The path also clips circles and the camera notch.
 */
fun Modifier.glassSurface(
    shape: Shape,
    base: Color,
    opacity: Float,
    tilt: () -> GlassTilt = { GlassTilt.Zero },
    sheen: () -> Float = { -1f },
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val dp = 1.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    val density = if (opacity.isFinite()) opacity.coerceIn(0.4f, 1f) else 0.68f
    val lightFill = base.luminance() > 0.5f
    // Density controls the body, independently of the decorative reflections.
    val body = base.copy(alpha = base.alpha * density)
    val veil = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (lightFill) 0.07f else 0.075f),
        0.42f to Color.White.copy(alpha = 0.012f),
        1f to Color.Black.copy(alpha = if (lightFill) 0.12f else 0.08f),
    )
    val edgeSoft = Stroke(width = 7.dp.toPx().coerceAtMost(h * 0.18f))
    val edgeInner = Stroke(width = 3.dp.toPx().coerceAtMost(h * 0.09f))
    val edgeLip = Stroke(width = 1.4f * dp)
    val darkEdge = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)))

    onDrawWithContent {
        if (w <= 0f || h <= 0f) {
            drawContent()
            return@onDrawWithContent
        }
        val direction = tilt()
        val tx = direction.x.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        val ty = direction.y.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        val shine = sheen()
        val strength = glassSheenStrength(shine)
        clipPath(path) {
            drawRect(body)
            drawRect(veil)
            // Broad reflections are deliberately faint: the bevel, not a white stripe,
            // catches most of the light. Radius follows height on wide capsules.
            drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.065f), Color.Transparent),
                center = Offset(w * (0.27f + tx * 0.17f), -h * (0.25f - ty * 0.18f)),
                radius = maxOf(h * 1.35f, w * 0.32f),
            ))
            val rim = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.80f),
                0.23f to Color.White.copy(alpha = 0.23f),
                0.48f to Color.White.copy(alpha = 0.035f),
                0.76f to Color.White.copy(alpha = 0.14f),
                1f to Color(0xFFE6F0FF).copy(alpha = 0.60f),
                start = Offset(w * (-0.12f + tx * 0.18f), h * (-0.15f + ty * 0.2f)),
                end = Offset(w * (1.05f + tx * 0.18f), h * (1.1f + ty * 0.2f)),
            )
            drawOutline(outline, darkEdge, style = edgeInner)
            drawOutline(outline, rim, alpha = 0.09f, style = edgeSoft)
            drawOutline(outline, rim, alpha = 0.18f, style = edgeInner)
            drawOutline(outline, rim, style = edgeLip)
            // A second, concentrated reflection at the opposite edge suggests lens depth.
            val caustic = Brush.radialGradient(
                listOf(Color(0xFFE3EDFF).copy(alpha = 0.24f), Color.Transparent),
                center = Offset(w * (0.76f - tx * 0.15f), h * (1.04f - ty * 0.08f)),
                radius = maxOf(h * 0.9f, w * 0.25f),
            )
            drawOutline(outline, caustic, style = edgeInner)
            if (strength > 0f) {
                val x = w * (-0.35f + shine * 1.7f)
                val reflection = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.32f * strength),
                    1f to Color.Transparent,
                    start = Offset(x - w * 0.16f, 0f), end = Offset(x + w * 0.16f, h),
                )
                drawOutline(outline, reflection, style = edgeInner)
            }
        }
        drawContent()
    }
}
