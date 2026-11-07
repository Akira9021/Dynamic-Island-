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
import com.akira.island.data.LiquidGlassMode
import kotlin.math.min

/**
 * AGSL port of QWEA0/Liquid-Glass-Android's GlassLensRenderer (MIT):
 *
 *  - rounded-box signed distance field and its numeric screen-space normal,
 *  - inverse-power bevel falloff with inward edge-compression sampling,
 *  - physical RGB dispersion along the normal,
 *  - vibrancy, Regular/Custom tint, and symmetric normal-lit specular lobes.
 *
 * The shader samples a real screenshot of the window under the island. When no
 * capture is available the old Compose material is drawn instead.
 */
const val LIQUID_GLASS_AGSL = """
uniform shader uBlur;

uniform float2 uSize;
uniform float2 uOrigin;
uniform float2 uBackOrigin;
uniform float2 uBackSize;
uniform float2 uBackScale;
uniform float uRadius;

uniform float uBevel;
uniform float uRefractHeight;
uniform float uFalloff;
uniform float uDispersion;
uniform float2 uLightDir;
uniform float uSpecStrength;
uniform float uRimBandMax;
uniform float4 uTint;
uniform float uAdaptiveTint;
uniform float uSaturation;

float sdRoundedBox(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

float shapeSDF(float2 p, float2 halfSize, float radius) {
    return sdRoundedBox(p, halfSize, radius);
}

float sdfAt(float2 p) {
    return shapeSDF(p - uSize * 0.5, uSize * 0.5, uRadius);
}

float2 sdfNormal(float2 p) {
    float2 n = float2(
        sdfAt(p + float2(1.0, 0.0)) - sdfAt(p - float2(1.0, 0.0)),
        sdfAt(p + float2(0.0, 1.0)) - sdfAt(p - float2(0.0, 1.0))
    );
    float len = length(n);
    return len > 0.0001 ? n / len : float2(0.0, -1.0);
}

half4 main(float2 coord) {
    float2 p = coord - uSize * 0.5;
    float d = shapeSDF(p, uSize * 0.5, uRadius);
    float coverage = clamp(0.5 - d / 1.5, 0.0, 1.0);
    if (coverage <= 0.004) return half4(0.0);

    float2 n = sdfNormal(coord);
    float t = clamp(-d / max(uBevel, 1.0), 0.0, 1.0);
    float slope;
    if (uFalloff > 0.001) {
        float edgeBase = pow(5.0, -uFalloff);
        slope = (pow(1.0 + 4.0 * t, -uFalloff) - edgeBase) / (1.0 - edgeBase);
    } else {
        float edge = 1.0 - t;
        slope = edge * edge;
    }

    // QWEA0 Liquid Glass 2.0 lens: inward SDF sampling folds the inner scene
    // back into a narrow, optically dense compression ring at the rim.
    float2 offset = n * (-slope * uRefractHeight);
    float2 screen = (uOrigin + coord - uBackOrigin) * uBackScale;
    float2 bitmapOffset = offset * uBackScale;
    float2 lo = float2(0.5);
    float2 hi = max(uBackSize - 0.5, lo + 1.0);
    float2 coordR = clamp(screen + bitmapOffset * (1.0 - uDispersion * slope), lo, hi);
    float2 coordG = clamp(screen + bitmapOffset, lo, hi);
    float2 coordB = clamp(screen + bitmapOffset * (1.0 + uDispersion * slope), lo, hi);

    half3 col = half3(uBlur.eval(coordR).r, uBlur.eval(coordG).g, uBlur.eval(coordB).b);

    // Upstream vibrancy curve (default saturation = 140): protect already-rich
    // and near-white pixels while lifting low-saturation backdrop details.
    float3 c = float3(col);
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    float room = 1.0 - smoothstep(0.2, 0.85, sat);
    float highlight = 1.0 - smoothstep(0.75, 0.98, lum);
    float amount = 1.0 + (uSaturation - 1.0) * mix(0.3, 1.0, room * highlight);
    c = clamp(mix(float3(lum), c, amount), float3(0.0), float3(1.0));

    // Upstream hero uses per-pixel Regular adaptive tint. It shifts highlights
    // toward white and shadows toward black; it never borrows the island fill.
    if (uAdaptiveTint > 0.5) {
        float localLum = dot(c, float3(0.2126, 0.7152, 0.0722));
        float e = smoothstep(0.35, 0.75, localLum);
        c = mix(c, float3(1.0 - e), 0.14 + 0.08 * e);
    }

    // Custom color is modeled as a lightly absorbing / scattering medium.
    if (uTint.a > 0.002) {
        float tintLum = dot(c, float3(0.2126, 0.7152, 0.0722));
        float3 absorbed = c * mix(float3(1.0), float3(uTint.rgb), 0.85);
        float3 scattered = float3(uTint.rgb) * (0.38 * (1.0 - tintLum));
        c = mix(c, clamp(absorbed + scattered, float3(0.0), float3(1.0)), uTint.a);
    }

    // The library's symmetric front/back specular lobes, driven by surface
    // normals and the gravity-mapped world light; no directionless outline.
    float2 lightDir = normalize(uLightDir);
    float facing = dot(n, -lightDir);
    float lobeFront = pow(max(facing, 0.0), 4.5);
    float lobeBack = pow(max(-facing, 0.0), 4.5);
    float bandWidth = clamp(uBevel * 0.3, 2.0, max(uRimBandMax, 2.0));
    float glowIn = clamp((-d - 1.0) / 2.0, 0.0, 1.0);
    float glow = glowIn * pow(clamp(1.0 - (-d - 3.0) / bandWidth, 0.0, 1.0), 1.5) * coverage;
    float hair = clamp(1.0 - abs(d + 1.0) / 2.0, 0.0, 1.0) * coverage;
    float spec = (hair * 0.70 * (lobeFront + lobeBack) + glow * 0.10 * lobeFront) * uSpecStrength;
    c = clamp(c + float3(spec), float3(0.0), float3(1.0));

    return half4(half3(c), 1.0);
}
"""

/** Geometry of the current island, including the round secondary activity. */
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
    glassMode: LiquidGlassMode,
    customTint: Color,
    customTintStrength: Float,
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
    val darkTint = Color(0xFF111318)
    val fallbackBase = when (glassMode) {
        LiquidGlassMode.DARK -> darkTint
        LiquidGlassMode.CUSTOM -> customTint
        LiquidGlassMode.FULL -> base
    }
    val fallbackDensity = when (glassMode) {
        LiquidGlassMode.DARK -> customTintStrength.coerceIn(0f, 0.85f)
        LiquidGlassMode.CUSTOM -> customTintStrength.coerceIn(0f, 0.85f)
        LiquidGlassMode.FULL -> 0.68f
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
                    if (size.width > 0f && size.height > 0f) drawGlassFallback(shape, fallbackBase, fallbackDensity, tilt, sheen)
                    drawContent()
                }
            } else {
                var cachedGeneration = Long.MIN_VALUE
                var blurShader: BitmapShader? = null
                val identity = Matrix()
                onDrawWithContent {
                    val frame = backdrop()
                    if (frame == null || size.width <= 0f || size.height <= 0f) {
                        drawGlassFallback(shape, fallbackBase, fallbackDensity, tilt, sheen)
                    } else {
                        if (frame.generation != cachedGeneration) {
                            blurShader = BitmapShader(frame.blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                                .linear().apply { setLocalMatrix(identity) }
                            cachedGeneration = frame.generation
                        }
                        val blur = blurShader
                        if (blur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val w = size.width
                            val h = size.height
                            val radius = if (geometry.circle) min(w, h) * 0.5f
                            else geometry.cornerPx().coerceIn(0f, min(w, h) * 0.5f)
                            val shortSide = min(w, h)
                            val densityScale = this.density
                            // Upstream defaults: bevel=48px / refraction=160px, adaptively
                            // clamped for small controls so a pill isn't all bevel.
                            val bevel = min(56f, shortSide * 0.30f).coerceAtLeast(2f)
                            val refraction = min(
                                200f,
                                0.70f * shortSide * min(1f, shortSide / (110f * densityScale)),
                            )
                            val rimBand = min(6f, shortSide * 0.05f).coerceAtLeast(2f)
                            shader.setInputShader("uBlur", blur)
                            shader.setFloatUniform("uSize", w, h)
                            shader.setFloatUniform("uOrigin", screenRect.left, screenRect.top)
                            val backOriginX = if (frame.local) screenRect.left else frame.screenRect.left.toFloat()
                            val backOriginY = if (frame.local) screenRect.top else frame.screenRect.top.toFloat()
                            shader.setFloatUniform("uBackOrigin", backOriginX, backOriginY)
                            shader.setFloatUniform("uBackSize", frame.sharp.width.toFloat(), frame.sharp.height.toFloat())
                            shader.setFloatUniform(
                                "uBackScale",
                                frame.sharp.width / frame.screenRect.width().coerceAtLeast(1).toFloat(),
                                frame.sharp.height / frame.screenRect.height().coerceAtLeast(1).toFloat(),
                            )
                            shader.setFloatUniform("uRadius", radius)
                            shader.setFloatUniform("uBevel", bevel)
                            shader.setFloatUniform("uRefractHeight", refraction)
                            shader.setFloatUniform("uFalloff", 2f)
                            shader.setFloatUniform("uDispersion", 0.16f)
                            shader.setFloatUniform("uRimBandMax", rimBand)
                            shader.setFloatUniform("uSaturation", 1.5f)
                            val direction = if (reduceEffects) GlassTilt.Zero else tilt()
                            val lightX = 0.866f + if (reduceEffects) 0f else direction.x * 0.35f
                            val lightY = 0.5f - if (reduceEffects) 0f else direction.y * 0.35f
                            val lightLength = kotlin.math.sqrt(lightX * lightX + lightY * lightY).coerceAtLeast(0.001f)
                            shader.setFloatUniform("uLightDir", lightX / lightLength, lightY / lightLength)
                            shader.setFloatUniform("uSpecStrength", if (reduceEffects) 0f else 1f)
                            val tint = when (glassMode) {
                                LiquidGlassMode.DARK -> darkTint.copy(alpha = customTintStrength.coerceIn(0f, 0.85f))
                                LiquidGlassMode.CUSTOM -> customTint.copy(alpha = customTintStrength.coerceIn(0f, 1f))
                                LiquidGlassMode.FULL -> Color.Transparent
                            }
                            shader.setFloatUniform("uTint", tint.red, tint.green, tint.blue, tint.alpha)
                            shader.setFloatUniform("uAdaptiveTint", if (glassMode == LiquidGlassMode.DARK) 0f else 1f)
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
