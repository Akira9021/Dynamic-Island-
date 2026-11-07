package com.akira.island.island

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.akira.island.core.GlassTilt
import com.akira.island.data.IslandSettings
import com.akira.island.data.resolveOutline

/** Shared by the overlay and local previews: fill, glass, gradient, border and shadow. */
@Composable
fun Modifier.islandSurface(
    s: IslandSettings,
    card: Boolean,
    shape: Shape,
    accent: Color? = null,
    tilt: () -> GlassTilt = { GlassTilt.Zero },
    sheen: () -> Float = { -1f },
    circle: Boolean = false,
    cornerPx: () -> Float = { 0f },
): Modifier {
    val preview = LocalIslandPreview.current
    val previewBackdrop = LocalGlassPreviewBackdrop.current
    val outline = s.resolveOutline(card)
    val base = Color(if (card && s.expandedColorEnabled) s.expandedColor else s.color)
    val fill = base.copy(alpha = base.alpha * s.islandOpacity.coerceIn(20, 100) / 100f)
    val color = (if (outline.fromActivity) accent ?: Color(outline.color) else Color(outline.color)).let {
        it.copy(alpha = it.alpha * outline.opacity / 100f)
    }
    return this
        // Match LiquidGlassView's default: the optical rim supplies depth; a
        // separate drop shadow is opt-in, not forced by the Full material.
        .then(if (s.shadow && !s.reduceEffects) Modifier.shadow(10.dp, shape, clip = false) else Modifier)
        .clip(shape)
        .then(
            if (s.liquidGlass) Modifier.liquidGlassSurface(
                shape = shape,
                base = base,
                glassMode = s.glassMode,
                customTint = Color(s.glassCustomTint),
                customTintStrength = s.glassOpacity / 100f,
                tilt = { if (s.reduceEffects) GlassTilt.Zero else tilt() },
                sheen = { if (s.reduceEffects) -1f else sheen() },
                backdrop = {
                    if (!s.glassShader) null
                    else previewBackdrop ?: if (!preview) GlassBackdropState.frame else null
                },
                geometry = GlassGeometry(
                    cornerPx = cornerPx,
                    roundness = if (circle) 2f else 4f,
                    circle = circle,
                ),
                reduceEffects = s.reduceEffects,
            ) else Modifier.background(fill),
        )
        .then(if (s.bottomGradient > 0) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = s.bottomGradient.coerceIn(0, 80) / 100f)))) else Modifier)
        .then(if (outline.visible) Modifier.islandEdgeOutline(shape, color, outline.edges[0].dp, outline.edges[1].dp, outline.edges[2].dp, outline.edges[3].dp) else Modifier)
}
