package com.akira.island.island

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.akira.island.data.IslandFont
import com.akira.island.data.IslandSettings
import com.akira.island.ui.Inter
import java.io.File

/**
 * Шрифт и толщина текста острова из «Оформления → Шрифт»: Inter (по умолчанию), шрифт системы или свой файл.
 * Значения — состояние Compose: сменили шрифт в настройках — остров перерисовывается сам.
 */
object IslandType {
    var family: FontFamily by mutableStateOf(Inter)
        private set

    /** На сколько ступеней (по 100) толще или тоньше весь текст острова */
    var shift: Int by mutableIntStateOf(0)
        private set

    private var key: String? = null

    /** Применить настройки. Зовут служба острова и экран настроек при каждой их смене — повтор ничего не делает. */
    fun apply(context: Context, s: IslandSettings) {
        val k = "${s.islandFont}|${s.customFontFile}"
        if (k != key) {
            key = k
            family = when (s.islandFont) {
                IslandFont.INTER -> Inter
                // Шрифт системы — тот, что выбран в настройках телефона (у многих оболочек — свой)
                IslandFont.SYSTEM -> FontFamily.Default
                IslandFont.CUSTOM -> customFamily(context, s.customFontFile) ?: Inter
            }
        }
        shift = s.fontWeightShift.coerceIn(-1, 2)
    }

    /** Папка своих шрифтов: в личной папке приложения, выбранный файл после копирования не нужен. */
    fun fontsDir(context: Context) = File(context.filesDir, "fonts")

    /** Читается ли файл как шрифт: проверяем при выборе, чтобы не сломать остров. */
    fun isFont(file: File): Boolean = runCatching { Typeface.createFromFile(file) != null }.getOrDefault(false)

    private fun customFamily(context: Context, name: String): FontFamily? {
        if (name.isEmpty()) return null
        val f = File(fontsDir(context), name)
        if (!f.isFile || !isFont(f)) return null
        return runCatching { FontFamily(Font(f)) }.getOrNull()
    }
}

/** Толщина с поправкой из настроек: «Жирнее» делает обычный текст средним, средний — полужирным и т. д. */
val FontWeight.island: FontWeight
    get() {
        val shift = IslandType.shift
        return if (shift == 0) this else FontWeight((weight + shift * 100).coerceIn(100, 900))
    }

private fun TextStyle.islandText(family: FontFamily): TextStyle =
    copy(fontFamily = family, fontWeight = (fontWeight ?: FontWeight.Normal).island)

/** Стили Material 3 для острова: выбранный шрифт и толщина. */
private fun islandTypography(family: FontFamily): Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.islandText(family),
        displayMedium = t.displayMedium.islandText(family),
        displaySmall = t.displaySmall.islandText(family),
        headlineLarge = t.headlineLarge.islandText(family),
        headlineMedium = t.headlineMedium.islandText(family),
        headlineSmall = t.headlineSmall.islandText(family),
        titleLarge = t.titleLarge.islandText(family),
        titleMedium = t.titleMedium.islandText(family),
        titleSmall = t.titleSmall.islandText(family),
        bodyLarge = t.bodyLarge.islandText(family),
        bodyMedium = t.bodyMedium.islandText(family),
        bodySmall = t.bodySmall.islandText(family),
        labelLarge = t.labelLarge.islandText(family),
        labelMedium = t.labelMedium.islandText(family),
        labelSmall = t.labelSmall.islandText(family),
    )
}

/** Тема острова: тёмная, с его шрифтом. Ей обёрнуты окно острова и пример острова в настройках. */
@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    val family = IslandType.family
    val shift = IslandType.shift
    val typography = remember(family, shift) { islandTypography(family) }
    MaterialTheme(colorScheme = darkColorScheme(), typography = typography, content = content)
}

/**
 * Размер текста и значков из «Оформления» ([percent], 100 — как задумано). Меняется плотность, а не места:
 * ширина, которую дали снаружи, остаётся той же, внутри всё рисуется крупнее или мельче.
 */
@Composable
fun Scaled(percent: Int, content: @Composable () -> Unit) {
    if (percent == 100) {
        content()
        return
    }
    val d = LocalDensity.current
    val k = percent.coerceIn(70, 130) / 100f
    CompositionLocalProvider(LocalDensity provides Density(d.density * k, d.fontScale), content = content)
}

/**
 * Остров «У камеры» в альбомной ориентации: всё внутри раскладывается как в портрете (ширина — вдоль края
 * с камерой) и поворачивается на [degrees] (−90 — камера слева, 90 — справа). Касания и границы для окна
 * касаний Compose пересчитывает с поворотом сам.
 */
@Composable
fun Rotated(degrees: Int, content: @Composable () -> Unit) {
    if (degrees == 0) {
        content()
        return
    }
    val conf = LocalConfiguration.current
    // Ширина экрана для острова — как в портрете: раскрытый остров и уведомления не растягиваются на всю альбомную ширину
    val portrait = remember(conf) {
        Configuration(conf).apply {
            val w = screenWidthDp
            screenWidthDp = screenHeightDp
            screenHeightDp = w
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
    }
    CompositionLocalProvider(LocalConfiguration provides portrait) {
        Layout(content) { measurables, constraints ->
            // Вдоль края с камерой — ширина «портретного» острова, от края внутрь — высота его окна
            val along = constraints.maxHeight
            val inward = constraints.maxWidth
            val placeables = measurables.map { it.measure(Constraints.fixed(along, inward)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { p ->
                    if (degrees < 0) {
                        p.placeWithLayer(0, along) {
                            rotationZ = -90f
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    } else {
                        p.placeWithLayer(inward, 0) {
                            rotationZ = 90f
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
            }
        }
    }
}
