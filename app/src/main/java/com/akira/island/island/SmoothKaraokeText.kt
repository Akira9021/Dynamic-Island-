package com.akira.island.island

import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.data.IslandSettings
import com.akira.island.lyrics.animateLyricSweep
import com.akira.island.lyrics.lyricSweepFraction

@Composable
internal fun SmoothKaraokeText(
    text: String, positionMs: Long, startMs: Long, endMs: Long,
    playing: Boolean, current: Boolean, settings: IslandSettings,
) {
    val wave = !settings.reduceEffects && endMs > startMs
    val fraction = if (wave) lyricSweepFraction(positionMs, startMs, endMs) else 1f
    val progress = remember { Animatable(fraction) }
    val previousPosition = remember { longArrayOf(positionMs) }
    LaunchedEffect(positionMs, playing, current, wave) {
        if (!current) return@LaunchedEffect // Keep the outgoing line's own last illumination.
        val animate = wave && animateLyricSweep(previousPosition[0], positionMs, playing)
        previousPosition[0] = positionMs
        if (animate) progress.animateTo(fraction, tween(100, easing = LinearEasing)) else progress.snapTo(fraction)
    }
    val mask = remember { LyricSweepMask() }
    Text(text, color = Color.White, fontSize = 14.5.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold.island, maxLines = 2, overflow = TextOverflow.Ellipsis,
        onTextLayout = { mask.layout = it },
        modifier = Modifier.fillMaxWidth().then(if (!wave) Modifier else Modifier
            // Only the glyphs enter this small layer; the card and controls are never masked.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val value = progress.value // Animation invalidates drawing, not text measurement.
                if (value < 1f) drawIntoCanvas { mask.draw(it.nativeCanvas, value, 24.dp.toPx()) }
            }))
}

/** Reuse shader, paint and matrix. The soft front moves in pixels, never UTF-16 characters. */
private class LyricSweepMask {
    var layout: TextLayoutResult? = null
    private val gradient = LinearGradient(0f, 0f, 1f, 0f, android.graphics.Color.WHITE,
        0x52FFFFFF, Shader.TileMode.CLAMP)
    private val transform = Matrix()
    private val paint = Paint().apply {
        shader = gradient
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun draw(canvas: NativeCanvas, fraction: Float, feather: Float) {
        val text = layout ?: return
        var total = 0f
        for (i in 0 until text.lineCount) total += (text.getLineRight(i) - text.getLineLeft(i)).coerceAtLeast(0f)
        if (total <= 0f) return
        var remaining = fraction.coerceIn(0f, 1f) * total
        for (i in 0 until text.lineCount) {
            val left = text.getLineLeft(i)
            val right = text.getLineRight(i)
            val width = (right - left).coerceAtLeast(0f)
            if (width <= 0f) continue
            val part = (remaining / width).coerceIn(0f, 1f)
            remaining -= width
            val band = feather.coerceAtMost(width / 4f).coerceAtLeast(1f)
            val rtl = text.getBidiRunDirection(text.getLineStart(i)) == ResolvedTextDirection.Rtl
            transform.setScale(if (rtl) -band else band, 1f)
            transform.postTranslate(if (rtl) right + band - part * (width + band)
                else left - band + part * (width + band), 0f)
            gradient.setLocalMatrix(transform)
            canvas.drawRect(0f, text.getLineTop(i), text.size.width.toFloat(), text.getLineBottom(i), paint)
        }
    }
}
