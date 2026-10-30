package com.akira.island.ui

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.akira.island.core.IslandBus
import com.akira.island.data.IslandLook

/**
 * Цвета One UI для острова. Акцент берётся из темы телефона: на Samsung One UI (и на Android 12+
 * с Material You) это цветовая палитра обоев, а если её прочитать нельзя — обои через WallpaperManager.
 * Совсем без цвета темы остров становится серым, как и просили.
 */
data class ThemePalette(
    /** Светлый акцент темы — для обводки, кнопок и свечения */
    val accent: Color,
    /** Верх и низ тёмной подложки One UI, слегка тонированные цветом темы */
    val surfaceTop: Color,
    val surfaceBottom: Color,
    /** Кромка вокруг острова */
    val rim: Color,
    /** Мягкое свечение у верхней кромки */
    val glow: Color,
    /** Цвет действительно взят из темы, а не серый запасной */
    val fromTheme: Boolean,
) {
    val hex: String get() = "#%06X".format(0xFFFFFF and accent.toArgb())
}

/** Серый запасной вариант — если тема недоступна или выбран именно серый стиль. */
val GrayPalette = ThemePalette(
    accent = Color(0xFFD6D6DC),
    surfaceTop = Color(0xFF232327),
    surfaceBottom = Color(0xFF0E0E10),
    rim = Color(0xFF75757F),
    glow = Color(0xFFBDBDC8),
    fromTheme = false,
)

/** One UI ли это: AUTO — на Samsung, ONE_UI и ONE_UI_GRAY — всегда, IPHONE — никогда. */
fun IslandLook.isOneUi(): Boolean = when (this) {
    IslandLook.AUTO -> Build.MANUFACTURER.equals("samsung", true) || Build.BRAND.equals("samsung", true)
    IslandLook.ONE_UI, IslandLook.ONE_UI_GRAY -> true
    IslandLook.IPHONE -> false
}

/**
 * Акцентный цвет темы. Сначала системная палитра Android 12+ (на One UI она повторяет цветовую
 * палитру Samsung), потом основной цвет обоев. null — цвета нет, нужен серый стиль.
 */
fun themeAccent(context: Context): Color? = themeColorInt(context)?.let { Color(readableAccent(it)) }

/**
 * Палитра One UI для острова. Если цвета темы нет, возвращается [GrayPalette].
 * Для IPHONE-стиля палитра не используется — там остаётся прежняя чёрная капсула.
 */
fun themePalette(context: Context, look: IslandLook): ThemePalette {
    if (look == IslandLook.ONE_UI_GRAY) return GrayPalette
    val raw = themeColorInt(context) ?: return GrayPalette
    return paletteFrom(raw)
}

/** Палитра, которая сама перечитывается при смене обоев/темы и при включении экрана. */
@Composable
fun rememberThemePalette(look: IslandLook): ThemePalette {
    val context = LocalContext.current
    val screenOn by IslandBus.screenOn.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                revision++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        // Системные broadcast'ы доходят и до получателя, зарегистрированного как неэкспортируемый.
        // На редких прошивках флаг не принимается — тогда регистрируемся по-старому, но без падения острова
        val registered = runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess || runCatching {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return remember(look, revision, screenOn) { themePalette(context, look) }
}

/** Подложка One UI: тёмный градиент цвета темы, мягкое свечение сверху и тонкая акцентная кромка. */
fun Modifier.oneUiSurface(shape: Shape, palette: ThemePalette): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val body = Brush.verticalGradient(
        0f to palette.surfaceTop,
        0.6f to palette.surfaceTop,
        1f to palette.surfaceBottom,
    )
    // Свет как у One UI: цвет темы проявляется у верхней кромки и тает к середине
    val glow = Brush.radialGradient(
        0f to palette.glow.copy(alpha = 0.22f),
        0.55f to palette.glow.copy(alpha = 0.06f),
        1f to Color.Transparent,
        center = Offset(size.width * 0.5f, 0f),
        radius = size.width.coerceAtLeast(size.height) * 0.9f,
    )
    val rim = Stroke(width = 1.dp.toPx())
    onDrawWithContent {
        drawOutline(outline, body)
        drawOutline(outline, glow)
        drawOutline(outline, palette.rim.copy(alpha = 0.5f), style = rim)
        drawContent()
    }
}

private fun themeColorInt(context: Context): Int? {
    // 1. Палитра темы Android 12+/One UI: на Samsung меняется вместе с цветовой палитрой
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ids = intArrayOf(
            android.R.color.system_accent1_200,
            android.R.color.system_accent1_300,
            android.R.color.system_accent1_500,
        )
        for (id in ids) {
            runCatching { context.getColor(id) }.getOrNull()?.let { return it }
        }
    }
    // 2. Основной цвет обоев — если системная палитра недоступна или пуста
    val manager = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
    if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val flags = intArrayOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)
        for (flag in flags) {
            val colors = runCatching { manager.getWallpaperColors(flag) }.getOrNull() ?: continue
            val color = colors.primaryColor ?: colors.secondaryColor ?: colors.tertiaryColor ?: continue
            return color.toArgb()
        }
    }
    return null
}

/** Светлый, читаемый на тёмной подложке вариант цвета темы. */
private fun readableAccent(color: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    if (hsv[1] < 0.1f) {
        // Монохромная тема: оставляем серый, но достаточно светлый
        hsv[1] = 0.03f
        hsv[2] = hsv[2].coerceIn(0.72f, 0.9f)
    } else {
        hsv[1] = hsv[1].coerceIn(0.28f, 0.72f)
        hsv[2] = hsv[2].coerceIn(0.72f, 0.92f)
    }
    return android.graphics.Color.HSVToColor(hsv)
}

/** Тёмная подложка в том же тоне, что и акцент: цвет темы виден, но текст остаётся белым. */
private fun paletteFrom(raw: Int): ThemePalette {
    val accent = readableAccent(raw)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent, hsv)
    val hue = hsv[0]
    val tint = hsv[1]
    fun tone(saturation: Float, value: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, (tint * saturation).coerceAtMost(0.4f), value)))
    return ThemePalette(
        accent = Color(accent),
        surfaceTop = tone(0.55f, 0.16f),
        surfaceBottom = tone(0.35f, 0.07f),
        rim = Color(accent),
        glow = Color(accent),
        fromTheme = true,
    )
}
