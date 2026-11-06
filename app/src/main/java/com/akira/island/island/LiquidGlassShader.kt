package com.akira.island.island

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.akira.island.core.GlassTilt
import kotlin.math.PI
import kotlin.math.min

/**
 * AGSL port of the optical model used by Apple's Liquid Glass and by the
 * liquid-glass-studio reference (MIT):
 *
 *  - a superellipse signed distance field defines the lens,
 *  - Snell's law bends the backdrop near the edge (thickness profile),
 *  - R/G/B are sampled with slightly different refraction (dispersion),
 *  - a Fresnel rim and a directional glare catch the light,
 *  - the centre is frosted, the rim is sharp.
 *
 * The shader samples a real screenshot of the window under the island. When no
 * capture is available the old Compose material is drawn instead.
 */
const val LIQUID_GLASS_AGSL = """
uniform shader uSharp;
uniform shader uBlur;

uniform float2 uSize;
uniform float2 uOrigin;
uniform float2 uBackOrigin;
uniform float2 uBackSize;

uniform float uRadius;
uniform float uRoundness;
uniform float uDensity;
uniform float uThickness;
uniform float uDistance;
uniform float uDispersion;
uniform float uIOR;
uniform float uFresnelRange;
uniform float uFresnelFactor;
uniform float uFresnelHardness;
uniform float uGlareRange;
uniform float uGlareFactor;
uniform float uGlareHardness;
uniform float uGlareConvergence;
uniform float uGlareOpposite;
uniform float uGlareAngle;
uniform float2 uTilt;
uniform float uSheen;
uniform float3 uTint;
uniform float uVeil;
uniform float uFreeze;

const float PI = 3.141592653589793;

float superellipseCorner(float2 p, float r, float n) {
    p = abs(p);
    return pow(pow(p.x, n) + pow(p.y, n), 1.0 / n) - r;
}

float shapeSDF(float2 p, float2 halfSize, float r, float n) {
    float2 d = abs(p) - halfSize;
    float cr = max(r, 0.001);
    if (d.x > -cr && d.y > -cr) {
        float2 cc = sign(p) * (halfSize - float2(cr));
        return superellipseCorner(p - cc, cr, n);
    }
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float sdfAt(float2 p) {
    return shapeSDF(p - uSize * 0.5, uSize * 0.5, uRadius, uRoundness);
}

float2 sdfNormal(float2 p) {
    float e = max(0.6, min(uSize.x, uSize.y) * 0.002);
    float dx = sdfAt(p + float2(e, 0.0)) - sdfAt(p - float2(e, 0.0));
    float dy = sdfAt(p + float2(0.0, e)) - sdfAt(p - float2(0.0, e));
    float2 g = float2(dx, dy) / (2.0 * e);
    float l = length(g);
    return l > 0.0001 ? g / l : float2(0.0);
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float d = shapeSDF(fragCoord - halfSize, halfSize, uRadius, uRoundness);
    float inside = max(-d, 0.0);

    // Thickness profile of the bevel: 0 at the outer edge, 1 deeper inside.
    float thickness = max(uThickness, 0.5);
    float t = clamp(inside / thickness, 0.0, 1.0);
    float xR = 1.0 - t;
    float thetaI = asin(clamp(pow(xR, 2.0), 0.0, 1.0));
    float thetaT = asin(clamp(sin(thetaI) / max(uIOR, 1.001), 0.0, 1.0));
    float bend = -tan(thetaT - thetaI) * smoothstep(0.0, 1.5, inside);

    float2 n = sdfNormal(fragCoord);
    float2 disp = -n * bend * uDistance;

    // Chromatic dispersion: red bends a little more, blue a little less.
    float2 uvR = uOrigin + fragCoord + disp * (1.0 + uDispersion) - uBackOrigin;
    float2 uvG = uOrigin + fragCoord + disp - uBackOrigin;
    float2 uvB = uOrigin + fragCoord + disp * (1.0 - uDispersion) - uBackOrigin;

    // The bevel is a sharp lens; the body is frosted.
    float sharpMix = (1.0 - smoothstep(0.0, 1.0, t)) * 0.85;

    float2 lo = float2(0.5);
    float2 hi = max(uBackSize - 0.5, lo + 1.0);
    uvR = clamp(uvR, lo, hi);
    uvG = clamp(uvG, lo, hi);
    uvB = clamp(uvB, lo, hi);

    half3 sharp = half3(uSharp.eval(uvR).r, uSharp.eval(uvG).g, uSharp.eval(uvB).b);
    half3 blur = half3(uBlur.eval(uvR).r, uBlur.eval(uvG).g, uBlur.eval(uvB).b);
    half3 col = mix(blur, sharp, half(sharpMix));

    // Body tint: at 100% the substrate is opaque, at 40% the backdrop shows.
    half3 tint = half3(uTint);
    col = mix(col, tint, half(clamp(uDensity, 0.0, 1.0)));

    // A faint vertical veil keeps the material readable over busy content.
    float vy = clamp(fragCoord.y / max(uSize.y, 1.0), 0.0, 1.0);
    col = mix(col, half3(1.0), half((1.0 - vy) * 0.05 * uVeil));
    col = mix(col, half3(0.0), half(smoothstep(0.55, 1.0, vy) * 0.05 * uVeil));

    // Fresnel: a wide soft halo plus a thin bright lip, like a thick glass bevel.
    float fresWide = pow(clamp(1.0 + d / max(uFresnelRange * 2.2, 0.5) + uFresnelHardness, 0.0, 1.0), 3.0);
    col = mix(col, half3(1.0), half(fresWide * uFresnelFactor * 0.22));
    float fres = pow(clamp(1.0 + d / max(uFresnelRange, 0.5) + uFresnelHardness, 0.0, 1.0), 5.0);
    fres *= clamp(uFresnelFactor, 0.0, 1.0);
    col = mix(col, half3(1.0), half(fres * 0.7));

    // Directional glare that follows the device tilt.
    float tilt = uFreeze > 0.5 ? 0.0 : (uTilt.x * 0.55 + uTilt.y * 0.35);
    float na = atan(n.y, n.x);
    if (na < 0.0) na += 2.0 * PI;
    float ga = (na - PI * 0.25 + uGlareAngle + tilt) * 2.0;
    bool farSide = (ga > PI * 1.5 && ga < PI * 3.5) || ga < -PI * 0.5;
    float gaFactor = (0.5 + sin(ga) * 0.5) * (farSide ? 1.2 * uGlareOpposite : 1.2) * uGlareFactor;
    gaFactor = clamp(pow(clamp(gaFactor, 0.0, 1.0), 0.1 + uGlareConvergence * 2.0), 0.0, 1.0);
    float glareGeo = pow(clamp(1.0 + d / max(uGlareRange, 0.5) + uGlareHardness, 0.0, 1.0), 5.0);
    col = mix(col, half3(1.0), half(gaFactor * glareGeo * 0.9));

    // A narrow specular sweep when the content changes.
    if (uSheen >= 0.0 && uFreeze < 0.5) {
        float sx = mix(-0.25, 1.25, uSheen);
        float band = exp(-pow((fragCoord.x / max(uSize.x, 1.0) - sx) / 0.13, 2.0));
        float fade = 1.0 - abs(uSheen * 2.0 - 1.0);
        col += half3(band * fade * 0.16);
    }

    return half4(col, 1.0);
}
"""

/** Geometry of the current island: a capsule/card or the round second activity. */
internal class GlassGeometry(val cornerPx: () -> Float, val roundness: Float, val circle: Boolean)

private fun BitmapShader.linear(): BitmapShader = apply {
    // Linear sampling was added in Android 14; on 13 the blur bitmap is already
    // smooth enough at full size.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        runCatching { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
    }
}

/**
 * Draws the real shader over [shape] when a [GlassBackdropFrame] is available.
 * The fallback material is used on Android 12 and older, in previews and when
 * the accessibility screenshot is unavailable.
 */
@Composable
internal fun Modifier.liquidGlassSurface(
    shape: Shape,
    base: Color,
    density: Float,
    tilt: () -> GlassTilt,
    sheen: () -> Float,
    backdrop: () -> GlassBackdropFrame?,
    geometry: GlassGeometry,
    reduceEffects: Boolean,
): Modifier {
    val view = LocalView.current
    var screenRect by remember { mutableStateOf(Rect.Zero) }
    val location = remember { IntArray(2) }
    // Compiling AGSL is expensive: keep one program for the lifetime of the island.
    // drawWithCache is rebuilt on every size change during the expand animation.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LIQUID_GLASS_AGSL) }.getOrNull()
        } else null
    }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    }
    return this
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            view.getLocationOnScreen(location)
            screenRect = Rect(
                bounds.left + location[0],
                bounds.top + location[1],
                bounds.right + location[0],
                bounds.bottom + location[1],
            )
        }
        .drawWithCache {
            val path = shape.toAndroidPath(size, layoutDirection, this)
            if (shader == null) {
                onDrawWithContent {
                    if (size.width > 0f && size.height > 0f) drawGlassFallback(shape, base, density, tilt, sheen)
                    drawContent()
                }
            } else {
                var cachedGeneration = Long.MIN_VALUE
                var sharpShader: BitmapShader? = null
                var blurShader: BitmapShader? = null
                val identity = Matrix()
                onDrawWithContent {
                    val frame = backdrop()
                    if (frame == null || size.width <= 0f || size.height <= 0f) {
                        drawGlassFallback(shape, base, density, tilt, sheen)
                    } else {
                        if (frame.generation != cachedGeneration) {
                            sharpShader = BitmapShader(frame.sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                                .linear().apply { setLocalMatrix(identity) }
                            blurShader = BitmapShader(frame.blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                                .linear().apply { setLocalMatrix(identity) }
                            cachedGeneration = frame.generation
                        }
                        val sharp = sharpShader
                        val blur = blurShader
                        if (sharp != null && blur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val w = size.width
                            val h = size.height
                            val radius = if (geometry.circle) min(w, h) * 0.5f
                            else geometry.cornerPx().coerceIn(0f, min(w, h) * 0.5f)
                            // A capsule needs circular end caps; cards use a squircle.
                            val roundness = if (geometry.circle || radius >= min(w, h) * 0.49f) 2f else geometry.roundness
                            val thickness = (h * 0.42f).coerceIn(5.dp.toPx(), 22.dp.toPx())
                            val rim = min(h * 0.34f, 18.dp.toPx())
                            shader.setInputShader("uSharp", sharp)
                            shader.setInputShader("uBlur", blur)
                            shader.setFloatUniform("uSize", w, h)
                            shader.setFloatUniform("uOrigin", screenRect.left, screenRect.top)
                            val backOriginX = if (frame.local) screenRect.left else frame.screenRect.left.toFloat()
                            val backOriginY = if (frame.local) screenRect.top else frame.screenRect.top.toFloat()
                            shader.setFloatUniform("uBackOrigin", backOriginX, backOriginY)
                            shader.setFloatUniform("uBackSize", frame.sharp.width.toFloat(), frame.sharp.height.toFloat())
                            shader.setFloatUniform("uRadius", radius)
                            shader.setFloatUniform("uRoundness", roundness)
                            shader.setFloatUniform("uDensity", density.coerceIn(0.4f, 1f))
                            shader.setFloatUniform("uThickness", thickness)
                            shader.setFloatUniform("uDistance", thickness * 0.75f)
                            shader.setFloatUniform("uDispersion", 0.10f)
                            shader.setFloatUniform("uIOR", 1.50f)
                            shader.setFloatUniform("uFresnelRange", rim)
                            shader.setFloatUniform("uFresnelFactor", 0.42f)
                            shader.setFloatUniform("uFresnelHardness", 0.12f)
                            shader.setFloatUniform("uGlareRange", rim)
                            shader.setFloatUniform("uGlareFactor", 0.85f)
                            shader.setFloatUniform("uGlareHardness", 0.12f)
                            shader.setFloatUniform("uGlareConvergence", 0.5f)
                            shader.setFloatUniform("uGlareOpposite", 0.8f)
                            shader.setFloatUniform("uGlareAngle", (-45.0 * PI / 180.0).toFloat())
                            val direction = if (reduceEffects) GlassTilt.Zero else tilt()
                            shader.setFloatUniform("uTilt", direction.x, direction.y)
                            shader.setFloatUniform("uSheen", if (reduceEffects) -1f else sheen())
                            shader.setFloatUniform("uTint", base.red, base.green, base.blue)
                            shader.setFloatUniform("uVeil", if (reduceEffects) 0.7f else 1.2f)
                            shader.setFloatUniform("uFreeze", if (reduceEffects) 1f else 0f)
                            paint.shader = shader
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                val saved = native.save()
                                native.clipPath(path)
                                native.drawRect(0f, 0f, w, h, paint)
                                native.restoreToCount(saved)
                            }
                        }
                    }
                    drawContent()
                }
            }
        }
}

private fun Shape.toAndroidPath(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
): android.graphics.Path {
    val outline = createOutline(size, layoutDirection, density)
    val path = when (outline) {
        is androidx.compose.ui.graphics.Outline.Generic -> outline.path
        is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is androidx.compose.ui.graphics.Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    return path.asAndroidPath()
}
