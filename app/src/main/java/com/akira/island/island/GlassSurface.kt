package com.akira.island.island

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader.TileMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.akira.island.core.GlassTilt
import com.akira.island.core.GlassOptics
import com.akira.island.core.glassBodyDensity
import kotlin.math.sqrt

internal val LocalGlassTilt = staticCompositionLocalOf<GlassTilt?> { null }

/** A translucent surface must not expose Android's rectangular shadow occluder. */
internal fun Modifier.outsideSurfaceShadow(shape: Shape, elevation: Dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    val radius = elevation.toPx().coerceAtLeast(0f)
    // Wide, faint strokes blend into a stronger contact shadow. All resources are cached.
    val alpha = floatArrayOf(0.006f, 0.009f, 0.012f, 0.018f, 0.026f, 0.038f, 0.052f, 0.064f)
    val strokes = Array(alpha.size) { Stroke(width = radius * 2f * (1f - it / alpha.size.toFloat())) }
    onDrawWithContent {
        if (radius > 0f && size.width > 0f && size.height > 0f) {
            clipPath(path, clipOp = ClipOp.Difference) {
                translate(top = radius * 0.25f) {
                    for (i in strokes.indices) drawOutline(outline, Color.Black, alpha = alpha[i], style = strokes[i])
                }
            }
        }
        drawContent()
    }
}

/**
 * A tinted optical surface: quiet center, a convex rim and coherent two-axis lighting.
 * This does not sample other apps' windows. All light is behind the content; it never
 * blurs text or washes out controls. The path also clips circles and the camera notch.
 */
fun Modifier.glassSurface(
    shape: Shape,
    base: Color,
    opacity: Float,
    tilt: () -> GlassTilt = { GlassTilt.Zero },
    sheen: () -> Float = { -1f },
): Modifier = composed {
    // Keep compilation outside drawWithCache: resizing must never compile a shader per frame.
    val lens = remember {
        if (Build.VERSION.SDK_INT >= 33) try { GlassLensShader() } catch (_: IllegalArgumentException) { null }
        else null
    }
    drawWithCache {
        val w = size.width
        val h = size.height
        val optics = GlassOptics(w, h, 1.dp.toPx())
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        val density = glassBodyDensity(opacity)
        if (Build.VERSION.SDK_INT >= 33 && lens != null && outline !is Outline.Generic) {
            lens.configure(size, outline, base, density, optics.lensWidth, optics.lip)
            val softwareBody = base.copy(alpha = base.alpha * density)
            val softwareEdge = Color.White.copy(alpha = 0.22f)
            val softwareStroke = Stroke(width = optics.lip)
            return@drawWithCache onDrawWithContent {
                if (optics.valid) {
                    val direction = tilt()
                    optics.update(direction.x, direction.y, sheen())
                    clipPath(path) {
                        if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                            lens.update(optics)
                            drawRect(lens)
                        } else {
                            // Bitmap/software canvases cannot draw RuntimeShader.
                            drawRect(softwareBody)
                            drawOutline(outline, softwareEdge, style = softwareStroke)
                        }
                    }
                }
                drawContent()
            }
        }
        val lightFill = base.luminance().coerceIn(0f, 1f)
        // Density controls the body, independently of the decorative reflections.
        val body = base.copy(alpha = base.alpha * density)
        val veil = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.045f - lightFill * 0.01f),
            0.42f to Color.White.copy(alpha = 0.005f),
            1f to Color.Black.copy(alpha = 0.08f + lightFill * 0.025f),
        )
        val edgeSoft = Stroke(width = optics.softRim)
        val edgeInner = Stroke(width = optics.innerRim)
        val edgeLip = Stroke(width = optics.lip)
        val darkEdge = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f + lightFill * 0.04f)))
        // Each native gradient and its transform are allocated once for this geometry/color.
        val rim = GlassGradient(LinearGradient(0f, 0f, 1f, 0f, intArrayOf(
            Color.White.copy(alpha = 0.78f - lightFill * 0.24f).toArgb(),
            Color.White.copy(alpha = 0.26f - lightFill * 0.06f).toArgb(),
            Color.White.copy(alpha = 0.035f - lightFill * 0.015f).toArgb(),
            Color.White.copy(alpha = 0.14f - lightFill * 0.04f).toArgb(),
            Color(0xFFE0EDFF).copy(alpha = 0.50f - lightFill * 0.16f).toArgb(),
        ), floatArrayOf(0f, 0.22f, 0.50f, 0.78f, 1f), TileMode.CLAMP))
        val reflection = GlassGradient(RadialGradient(0f, 0f, 1f,
            Color.White.copy(alpha = 0.045f).toArgb(), Color.Transparent.toArgb(), TileMode.CLAMP))
        val caustic = GlassGradient(RadialGradient(0f, 0f, 1f,
            Color(0xFFDCEAFF).copy(alpha = 0.24f).toArgb(), Color.Transparent.toArgb(), TileMode.CLAMP))
        val sweep = GlassGradient(LinearGradient(0f, 0f, 1f, 0f,
            intArrayOf(Color.Transparent.toArgb(), Color.White.copy(alpha = 0.26f).toArgb(), Color.Transparent.toArgb()),
            floatArrayOf(0f, 0.5f, 1f), TileMode.CLAMP))
        onDrawWithContent {
            if (!optics.valid) {
                drawContent()
                return@onDrawWithContent
            }
            val direction = tilt()
            optics.update(direction.x, direction.y, sheen())
            rim.linear(optics.rimX, optics.rimY, optics.rimDx, optics.rimDy)
            reflection.radial(optics.reflectionX, optics.reflectionY, optics.reflectionRadius)
            caustic.radial(optics.causticX, optics.causticY, optics.causticRadius)
            clipPath(path) {
                drawRect(body)
                drawRect(veil)
                drawRect(reflection)
                drawOutline(outline, darkEdge, style = edgeInner)
                drawOutline(outline, rim, alpha = 0.14f, style = edgeSoft)
                drawOutline(outline, rim, alpha = 0.32f, style = edgeInner)
                drawOutline(outline, rim, alpha = 0.90f, style = edgeLip)
                drawOutline(outline, caustic, style = edgeInner)
                if (optics.sheenStrength > 0f) {
                    sweep.linear(optics.sheenX, -h * 0.1f, optics.sheenDx, optics.sheenDy)
                    drawOutline(outline, sweep, alpha = optics.sheenStrength, style = edgeInner)
                }
            }
            drawContent()
        }
    }
}

/**
 * Original analytical light model, not a backdrop sampler. The real window remains visible
 * through premultiplied transparency. Rounded bevel normals bend the incident light;
 * Fresnel reflection and a focused opposite rim give depth without touching foreground.
 * Generic outlines retain the clipped Canvas material; API 26–32 use that path too.
 */
@RequiresApi(33)
internal class GlassLensShader : ShaderBrush() {
    private val lens = RuntimeShader(SOURCE)
    override fun createShader(size: Size): Shader = lens

    fun configure(size: Size, outline: Outline, base: Color, density: Float, bevel: Float, lip: Float) {
        lens.setFloatUniform("bounds", size.width, size.height)
        val r = (outline as? Outline.Rounded)?.roundRect
        lens.setFloatUniform("corners", r?.topLeftCornerRadius?.x ?: 0f, r?.topRightCornerRadius?.x ?: 0f,
            r?.bottomRightCornerRadius?.x ?: 0f, r?.bottomLeftCornerRadius?.x ?: 0f)
        lens.setFloatUniform("tint", base.red, base.green, base.blue, base.alpha * density)
        lens.setFloatUniform("bevel", bevel)
        lens.setFloatUniform("edgeWidth", lip)
    }

    fun update(optics: GlassOptics) {
        // Normalize once per draw, rather than for every pixel. 0.7619393 = 1/sqrt(1+0.85^2).
        val scale = 0.7619393f / sqrt(optics.rimDx * optics.rimDx + optics.rimDy * optics.rimDy)
        lens.setFloatUniform("lightVector", -optics.rimDx * scale, -optics.rimDy * scale, 0.64764845f)
        lens.setFloatUniform("sweep", optics.sheenX, optics.sheenDx, optics.sheenDy, optics.sheenStrength)
    }

    private companion object {
        // No background image, time uniform, loops, textures, or screen capture.
        const val SOURCE = """
            uniform float2 bounds;
            uniform float4 corners;
            uniform float4 tint;
            uniform float bevel;
            uniform float edgeWidth;
            uniform float3 lightVector;
            uniform float4 sweep;
            half4 main(float2 xy) {
                float2 p = xy - bounds * 0.5;
                float radius = p.x < 0.0 ? (p.y < 0.0 ? corners.x : corners.w)
                                             : (p.y < 0.0 ? corners.y : corners.z);
                float2 q = abs(p) - bounds * 0.5 + radius;
                float2 corner = max(q, 0.0);
                float cornerLength = length(corner);
                float distance = cornerLength + min(max(q.x, q.y), 0.0) - radius;
                float2 edgeNormal = cornerLength > 0.001 ? corner / cornerLength
                    : (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
                edgeNormal *= float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
                float wall = 1.0 - smoothstep(0.0, bevel, max(-distance, 0.0));
                float lip = 1.0 - smoothstep(0.0, edgeWidth, max(-distance, 0.0));
                float slope = wall * 0.97;
                float3 normal = float3(edgeNormal * slope, sqrt(max(1.0 - slope * slope, 0.0)));
                float3 light = lightVector;
                float facing = max(dot(normal, light), 0.0);
                float grazing = 1.0 - normal.z;
                float fresnel = 0.025 + 0.975 * grazing * grazing * grazing;
                // Snell's law for the light ray (air -> glass, refractive index 1.46).
                float3 bentLight = refract(-light, normal, 0.68493);
                float focus = max(dot(edgeNormal, -light.xy), 0.0);
                float inner = smoothstep(0.08, 0.38, wall) * (1.0 - smoothstep(0.55, 0.96, wall));
                float caustic = inner * focus * focus * (0.3 + 0.7 * abs(bentLight.z));
                float specular = facing * facing; specular *= specular * specular;
                float streak = 1.0 - smoothstep(0.0, 0.5,
                    abs((xy.x - sweep.x) / max(sweep.y, 1.0) + xy.y * sweep.z / max(sweep.y * sweep.y, 1.0)));
                float reflection = clamp(fresnel * (0.20 + specular * 0.60)
                    + wall * specular * 0.36 + caustic * 0.34 + lip * (0.22 + specular * 0.45)
                    + streak * wall * sweep.w * 0.24, 0.0, 0.86);
                float dispersion = wall * (1.0 - wall) * 0.12;
                float3 shine = mix(float3(1.0, 0.98, 0.95), float3(0.72, 0.86, 1.0), focus);
                shine += float3(dispersion, 0.0, -dispersion) * edgeNormal.x;
                float shadow = inner * (1.0 - focus) * 0.045;
                float alpha = tint.a + (1.0 - tint.a) * reflection;
                float3 rgb = tint.rgb * tint.a * (1.0 - reflection) * (1.0 - shadow)
                    + clamp(shine, 0.0, 1.0) * reflection;
                return half4(rgb, alpha);
            }
        """
    }
}

/** Compose can cache the brush while the native shader reuses one local transform. */
private class GlassGradient(private val shader: Shader) : ShaderBrush() {
    private val transform = Matrix()
    private val values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    override fun createShader(size: Size): Shader = shader

    fun linear(x: Float, y: Float, dx: Float, dy: Float) {
        values[0] = dx; values[1] = -dy; values[2] = x
        values[3] = dy; values[4] = dx; values[5] = y
        transform.setValues(values)
        shader.setLocalMatrix(transform)
    }

    fun radial(x: Float, y: Float, radius: Float) {
        transform.setScale(radius, radius)
        transform.postTranslate(x, y)
        shader.setLocalMatrix(transform)
    }
}
