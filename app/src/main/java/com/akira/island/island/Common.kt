package com.akira.island.island

import android.os.SystemClock
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Tints
import com.akira.island.core.Turn
import com.akira.island.data.AccentMode
import com.akira.island.data.ExpandStyle
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.akira.island.core.IslandBus
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/** Анимация формы острова по настройкам «Стиль раскрытия», «Длительность» и «Упругость». */
fun <T> islandSpec(s: IslandSettings): FiniteAnimationSpec<T> {
    val damping = if (s.reduceEffects) 1f else s.elasticity.damping
    return when (s.expandStyle) {
        ExpandStyle.INSTANT -> snap()
        ExpandStyle.SMOOTH -> tween(animDurationMs(s), easing = FastOutSlowInEasing)
        ExpandStyle.SPRING -> spring(dampingRatio = (damping + 0.1f).coerceAtMost(1f), stiffness = animStiffness(s))
        ExpandStyle.FRUIT -> spring(dampingRatio = damping, stiffness = animStiffness(s))
    }
}

/** Проявление и таяние содержимого острова: та же скорость, что у формы, но быстрее и без отскока. */
fun <T> fadeSpec(s: IslandSettings): FiniteAnimationSpec<T> = when (s.expandStyle) {
    ExpandStyle.INSTANT -> snap()
    ExpandStyle.SMOOTH -> tween(animDurationMs(s) / 2)
    else -> spring(stiffness = animStiffness(s) * 6f)
}

/** Жёсткость пружины в ролике (damping 12, stiffness 190, mass 0.85): остров перетекает за полсекунды с перелётом. */
private const val VIDEO_STIFFNESS = 223f

/**
 * «Длительность»: 0% — быстрее всего, 100% — медленнее всего. По умолчанию (35%) — ровно как в ролике;
 * в обе стороны жёсткость меняется в разы, поэтому ползунок заметен по всей длине.
 */
fun animStiffness(s: IslandSettings): Float {
    val t = s.durationPercent.coerceIn(0, 100) / 100f
    return if (t <= 0.35f) {
        1200f * (VIDEO_STIFFNESS / 1200f).pow(t / 0.35f)
    } else {
        VIDEO_STIFFNESS * (60f / VIDEO_STIFFNESS).pow((t - 0.35f) / 0.65f)
    }
}

/** Во сколько раз всё медленнее, чем в ролике: по ней же идут проявление содержимого и размытие. */
fun motionScale(s: IslandSettings): Float = if (s.expandStyle == ExpandStyle.INSTANT) 0.5f else sqrt(VIDEO_STIFFNESS / animStiffness(s))

/** Плавность из ролика: быстро в начале и долго мягко дотягивает (cubic-bezier 0.16, 1, 0.3, 1). */
val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

fun animDurationMs(s: IslandSettings): Int = 160 + s.durationPercent.coerceIn(0, 100) * 6

/** Текущее время (elapsedRealtime) с обновлением раз в [periodMs]; пока экран погашен — не обновляется. */
@Composable
fun rememberNow(periodMs: Long, active: Boolean = true): Long = rememberNowState(periodMs, active).value

/** Can be read in DrawScope, so progress does not recompose or remeasure the player. */
@Composable
fun rememberNowState(periodMs: Long, active: Boolean = true): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(periodMs, active) {
        while (active) {
            if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
            now.longValue = SystemClock.elapsedRealtime()
            delay(periodMs)
        }
    }
    return now
}

/**
 * Время для живых украшений — эквалайзера, свечения: обновляется с частотой из «Эффектов» (по умолчанию 60 раз
 * в секунду) и только пока горит экран. Музыка играет часами, и с полной частотой экрана остров перерисовывался бы
 * до 120 раз в секунду всё это время — телефон грелся. 60 кадров на глаз так же плавно, а работы вдвое меньше.
 * Между кадрами корутина спит почти до следующего нужного, а не просыпается на каждом кадре экрана.
 * Читать значение нужно при рисовании, тогда меняется только картинка.
 */
@Composable
fun rememberFxClock(active: Boolean): State<Long> {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        var last = 0L
        while (active) {
            if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
            val fps = SettingsRepo.current.fxRate.fps
            val step = if (fps > 0) 1000L / fps else 0L
            // Кадр экрана раньше срока пропускаем: на 120 Гц при 60 кадрах рисуется каждый второй
            withInfiniteAnimationFrameMillis { t ->
                if (t - last >= step - 3) {
                    time.longValue = t
                    last = t
                }
            }
            if (step > 0) delay(step - 6)
        }
    }
    return time
}

/**
 * Значение «туда-обратно» за [halfMs] в каждую сторону с плавным разгоном и торможением — как
 * infiniteRepeatable(tween(halfMs, FastOutSlowInEasing), RepeatMode.Reverse), но от общего времени [t].
 */
fun pingPong(t: Long, halfMs: Int): Float {
    val p = (t % (2L * halfMs)).toFloat() / halfMs
    return FastOutSlowInEasing.transform(if (p < 1f) p else 2f - p)
}

/** То же для настенных часов (System.currentTimeMillis). */
@Composable
fun rememberWallNow(periodMs: Long, active: Boolean = true): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs, active) {
        while (active) {
            if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) {
        {
            if (SettingsRepo.current.haptics) {
                val performed = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val systemHaptics = !performed && runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
                }.getOrDefault(false)
                if (!performed && systemHaptics) {
                    // Some overlay windows cannot request view haptics; use the public vibrator API as a fallback.
                    runCatching {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                        if (vibrator?.hasVibrator() == true) {
                            vibrator.vibrate(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                            else VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Звуковая волна из тонких полосок, как в острове iPhone. Полоски тоньше промежутков и качаются спокойно:
 * толстые и быстрые, как писали в комментариях, смотрятся плохо, особенно под медленную музыку. У каждой полоски
 * две свои скорости, поэтому движение не механическое. Высоты читаются только при рисовании:
 * кадр анимации перерисовывает полоски, а не пересобирает остров.
 */
@Composable
fun Equalizer(color: Color, playing: Boolean, animate: Boolean, modifier: Modifier = Modifier, bars: Int = 5) {
    val moving = playing && animate
    val time = rememberFxClock(moving)
    Canvas(modifier) {
        // Промежуток в 1,6 раза шире полоски
        val w = size.width / (bars + (bars - 1) * 1.6f)
        for (i in 0 until bars) {
            val h = when {
                moving -> {
                    val t = time.value
                    0.25f + 0.75f * (0.6f * pingPong(t, 420 + (i * 173) % 380) + 0.4f * pingPong(t + i * 211L, 700 + (i * 97) % 400))
                }
                playing -> 0.3f + 0.45f * ((i * 37) % 10) / 10f
                else -> 0.15f
            }
            val bh = (size.height * h).coerceAtLeast(w)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2.6f * w, (size.height - bh) / 2),
                size = Size(w, bh),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

/** Батарейка как в iOS: заливка по уровню заряда и «носик» справа. */
@Composable
fun BatteryGlyph(level: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val nub = size.height * 0.16f
        val bodyW = size.width - nub - 1.5.dp.toPx()
        val r = size.height * 0.32f
        drawRoundRect(color.copy(alpha = 0.3f), size = Size(bodyW, size.height), cornerRadius = CornerRadius(r))
        val inset = size.height * 0.14f
        val fillW = (bodyW - inset * 2) * (level.coerceIn(0, 100) / 100f)
        if (fillW > 0f) {
            drawRoundRect(
                color,
                topLeft = Offset(inset, inset),
                size = Size(fillW.coerceAtLeast(r * 0.5f), size.height - inset * 2),
                cornerRadius = CornerRadius(r * 0.7f),
            )
        }
        drawRoundRect(
            color.copy(alpha = 0.5f),
            topLeft = Offset(bodyW + 1.5.dp.toPx(), size.height * 0.29f),
            size = Size(nub, size.height * 0.42f),
            cornerRadius = CornerRadius(nub / 2),
        )
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 3.dp,
    track: Color = color.copy(alpha = 0.22f),
) {
    Canvas(modifier) {
        val px = stroke.toPx()
        val inset = px / 2
        val arcSize = Size(size.width - px, size.height - px)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(px))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
            style = Stroke(px, cap = StrokeCap.Round))
    }
}

@Composable
fun Art(art: ImageBitmap?, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF2C2C2E)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Ios.music, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun AppIcon(icon: ImageBitmap?, size: Dp, fallback: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    if (icon != null) {
        Image(icon, null, modifier.size(size).clip(CircleShape))
    } else {
        Icon(fallback, null, tint = tint, modifier = modifier.size(size))
    }
}

/** Optional main-action accent; red and green retain their call/warning meanings. */
private fun controlTint(color: Color): Color {
    val accent = IslandType.actionAccent ?: return color
    val rgb = color.copy(alpha = 1f)
    return if (rgb == Tints.Orange || rgb == Tints.Lavender || rgb == Tints.Blue) accent.copy(alpha = color.alpha) else color
}

@Composable
fun RoundButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    bg: Color = Color.White.copy(alpha = 0.14f),
    fg: Color = Color.White,
    iconScale: Float = 0.52f,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    // Кнопка чуть проседает под пальцем, как на iPhone
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(controlTint(bg))
            .clickable(interaction, indication = null) { haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = controlTint(fg), modifier = Modifier.size(size * iconScale).graphicsLayer { rotationZ = iconRotation })
    }
}

@Composable
fun PillButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Color.White.copy(alpha = 0.14f),
    fg: Color = Color.White,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    Row(
        modifier
            .clip(CircleShape)
            .background(controlTint(bg))
            .clickable { haptic(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = controlTint(fg), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        // Длинная подпись («Не беспокоить», «Без звука») чуть уменьшается, а не обрезается
        FitText(text, controlTint(fg), 14.sp, 11.sp, FontWeight.Medium.island, Modifier.weight(1f, fill = false))
    }
}

/**
 * Знак бесконтактной оплаты (Contactless Indicator, EMVCo) — тот самый, что печатают на терминалах
 * и картах: четыре дуги, растущие вправо. Как в ролике: дуги загораются по очереди изнутри наружу,
 * полный круг — 0,8 с.
 */
@Composable
fun ContactlessWaves(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val moving = if (animate) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "phase") else null
    Canvas(modifier) {
        val phase = moving?.value ?: 0.6f
        // Сетка 24×24, как у значка в ролике: центр дуг слева посередине
        val k = size.minDimension / 24f
        val c = Offset(4f * k, size.height / 2)
        for (i in 0..3) {
            val r = (4f + 4.2f * i) * k
            val bright = 0.35f + 0.65f * (1f - kotlin.math.abs(phase - (i + 1) / 5f) * 4f).coerceIn(0f, 1f)
            drawArc(
                color.copy(alpha = if (animate) bright else 1f),
                startAngle = -43f,
                sweepAngle = 86f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = 2.2f * k, cap = StrokeCap.Round),
            )
        }
    }
}

/** Зелёный круг выпрыгивает с пружиной, следом прорисовывается галочка — как «Оплачено» в ролике. */
@Composable
fun CheckBadge(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        launch { pop.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 220f)) }
        delay(100)
        draw.animateTo(1f, tween(300, easing = LinearEasing))
    }
    Canvas(modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        val w = size.width
        drawCircle(color)
        val tick = Path().apply {
            moveTo(w * 0.31f, w * 0.513f)
            lineTo(w * 0.445f, w * 0.65f)
            lineTo(w * 0.704f, w * 0.364f)
        }
        val part = Path()
        val measure = PathMeasure()
        measure.setPath(tick, false)
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(part, Color.White, style = Stroke(width = w * 0.093f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Строка, которая уменьшает шрифт (от [max] до [min]), если не помещается, — вместо многоточия.
 * Многоточие остаётся на крайний случай: очень длинный текст или крупный шрифт Android.
 */
@Composable
fun FitText(
    text: String,
    color: Color,
    max: TextUnit,
    min: TextUnit,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.merge(style)
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val size = remember(text, room, max, min, base) {
            var sz = max.value
            while (sz > min.value &&
                measurer.measure(text, base.copy(fontSize = sz.sp, fontWeight = weight), maxLines = 1).size.width > room
            ) {
                sz -= 0.5f
            }
            sz.sp
        }
        // Тот же стиль, по которому мерили: шрифт темы и его межбуквенный интервал
        Text(text, color = color, fontSize = size, fontWeight = weight, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis, style = base)
    }
}

/** Цвет кнопок карточки уведомления по настройке: сиреневый, системный, из аватара или свой. */
@Composable
fun rememberAccent(s: IslandSettings, adaptive: Color? = null): Color {
    val context = LocalContext.current
    return remember(s.accentMode, s.accentColor, adaptive) { resolveAccent(context, s, adaptive) }
}

fun resolveAccent(context: android.content.Context, s: IslandSettings, adaptive: Color?): Color = when (s.accentMode) {
    AccentMode.DEFAULT -> Tints.Lavender
    AccentMode.CUSTOM -> Color(s.accentColor)
    AccentMode.ADAPTIVE -> adaptive ?: Tints.Lavender
    AccentMode.SYSTEM -> systemAccent(context) ?: Tints.Lavender
}

/** Акцент обоев (Material You) — светлый тон 200, хорошо читается на чёрном. Только Android 12+. */
fun systemAccent(context: android.content.Context): Color? =
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        runCatching { Color(context.getColor(android.R.color.system_accent1_200)) }.getOrNull()
    } else {
        null
    }

/** Чем рисовать поверх [accent]: чёрным на светлом цвете, белым на тёмном. */
fun onAccent(accent: Color): Color = if (accent.luminance() > 0.5f) Color(0xFF1B1830) else Color.White

/**
 * Стрелка манёвра, как в навигаторе: прямо, налево, направо или разворот. Рисуется линией
 * со скруглёнными концами, поэтому одинаково чёткая в капсуле и в раскрытом виде.
 */
@Composable
fun TurnArrow(turn: Turn, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 100f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        // Рисуем «направо», налево — зеркало по горизонтали
        val flip = turn == Turn.LEFT
        fun x(v: Float) = if (flip) 100f - v else v
        val body = Path()
        val head = Path()
        when (turn) {
            Turn.STRAIGHT -> {
                body.moveTo(50 * k, 86 * k); body.lineTo(50 * k, 20 * k)
                head.moveTo(30 * k, 40 * k); head.lineTo(50 * k, 18 * k); head.lineTo(70 * k, 40 * k)
            }
            Turn.RIGHT, Turn.LEFT -> {
                // Как значок поворота в ролике: вверх, скругление и вправо, наконечник — уголком
                body.moveTo(x(25f) * k, 92 * k); body.lineTo(x(25f) * k, 50 * k)
                body.quadraticBezierTo(x(25f) * k, 33.3f * k, x(41.7f) * k, 33.3f * k); body.lineTo(x(79f) * k, 33.3f * k)
                head.moveTo(x(62.5f) * k, 16.7f * k); head.lineTo(x(79f) * k, 33.3f * k); head.lineTo(x(62.5f) * k, 50 * k)
            }
            Turn.UTURN -> {
                body.moveTo(30 * k, 88 * k); body.lineTo(30 * k, 40 * k)
                body.quadraticBezierTo(30 * k, 14 * k, 54 * k, 14 * k)
                body.quadraticBezierTo(76 * k, 14 * k, 76 * k, 40 * k); body.lineTo(76 * k, 62 * k)
                head.moveTo(60 * k, 48 * k); head.lineTo(76 * k, 66 * k); head.lineTo(92 * k, 48 * k)
            }
        }
        val stroke = Stroke(width = 11.7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(body, color, style = stroke)
        drawPath(head, color, style = stroke)
    }
}


/**
 * A clipped outline with independent widths for all four sides. The inside follows the outer rounded
 * corners with each radius reduced by its adjacent edge width, so corners have no rectangular seams.
 * Shape state is read at draw time; animated rounding does not rebuild the content.
 */
fun Modifier.islandEdgeOutline(shape: Shape, color: Color, top: Dp, right: Dp, bottom: Dp, left: Dp): Modifier =
    drawWithCache {
        val t = top.toPx().coerceIn(0f, size.height / 2)
        val r = right.toPx().coerceIn(0f, size.width / 2)
        val b = bottom.toPx().coerceIn(0f, size.height / 2)
        val l = left.toPx().coerceIn(0f, size.width / 2)
        val outline = shape.createOutline(size, layoutDirection, this)
        val outer = Path()
        val inner = Path()
        val hasInside = size.width > l + r && size.height > t + b
        when (outline) {
            is Outline.Rounded -> {
                val rr = outline.roundRect
                outer.addRoundRect(rr)
                if (hasInside) {
                    fun inset(c: CornerRadius, x: Float, y: Float) = CornerRadius((c.x - x).coerceAtLeast(0f), (c.y - y).coerceAtLeast(0f))
                    inner.addRoundRect(RoundRect(
                        l, t, size.width - r, size.height - b,
                        inset(rr.topLeftCornerRadius, l, t), inset(rr.topRightCornerRadius, r, t),
                        inset(rr.bottomRightCornerRadius, r, b), inset(rr.bottomLeftCornerRadius, l, b),
                    ))
                }
            }
            is Outline.Rectangle -> {
                outer.addRect(outline.rect)
                if (hasInside) inner.addRect(Rect(l, t, size.width - r, size.height - b))
            }
            is Outline.Generic -> {
                outer.addPath(outline.path)
                if (hasInside) inner.addRect(Rect(l, t, size.width - r, size.height - b))
            }
        }
        onDrawWithContent {
            drawContent()
            if (t + r + b + l > 0f && color.alpha > 0f) {
                if (hasInside) clipPath(inner, clipOp = ClipOp.Difference) { drawPath(outer, color) }
                else drawPath(outer, color)
            }
        }
    }
