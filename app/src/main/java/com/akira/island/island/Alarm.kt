package com.akira.island.island

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.AlarmInfo
import com.akira.island.core.Controls
import com.akira.island.core.Lang
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.IslandSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Будильник оранжевый, как на iPhone; ночь — индиго, как в «Режиме сна» */
val AlarmTint = Tints.Orange
private val NightTint = Color(0xFF7D7AFF)
private val Dim = Color.White.copy(alpha = 0.55f)
private const val DAY = 86_400_000L

/** За сколько до будильника он появляется в острове. */
const val ALARM_LEAD_MS = 12 * 3_600_000L

/** Утренний будильник (с 3 до 12) — это подъём: тогда остров говорит «сон», а не «через». */
fun isWakeUp(triggerAt: Long): Boolean =
    Calendar.getInstance().apply { timeInMillis = triggerAt }.get(Calendar.HOUR_OF_DAY) in 3..11

fun wakeTime(context: Context, triggerAt: Long): String = DateFormat.getTimeFormat(context).format(Date(triggerAt))

/**
 * Цифры крупно, а «AM»/«PM» вдвое мельче, как на iPhone: в 12-часовом формате «11:45 PM» не распирает
 * капсулу и не лезет к камере. В 24-часовом формате текст не меняется.
 */
fun clockText(time: String, size: TextUnit): AnnotatedString {
    val digits = Regex("""\d{1,2}[:.]\d{2}""").find(time) ?: return AnnotatedString(time)
    val before = time.substring(0, digits.range.first)
    val after = time.substring(digits.range.last + 1)
    if (before.isEmpty() && after.isEmpty()) return AnnotatedString(time)
    val small = SpanStyle(fontSize = (size.value * 0.5f).coerceAtLeast(11f).sp)
    return buildAnnotatedString {
        withStyle(small) { append(before) }
        append(digits.value)
        withStyle(small) { append(after) }
    }
}

/** «8 ч 15 мин», «45 мин», «меньше минуты» — как пишет Android, когда ставишь будильник. */
fun durationWords(ms: Long): String {
    if (ms < 60_000) return tr("меньше минуты", "less than a minute")
    val minutes = (ms + 59_999) / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> trf("{0} мин", "{0} min", m)
        m == 0L -> trf("{0} ч", "{0} h", h)
        else -> trf("{0} ч {1} мин", "{0} h {1} min", h, m)
    }
}

/** «сегодня», «завтра» или день недели. */
fun alarmDay(triggerAt: Long, now: Long): String {
    val days = ((midnight(triggerAt) - midnight(now)) / DAY.toDouble()).roundToLong()
    return when (days) {
        0L -> tr("сегодня", "today")
        1L -> tr("завтра", "tomorrow")
        // По-русски, по-казахски и по-португальски день недели строчными («пятница», «sexta-feira»),
        // по-английски и по-индонезийски — с большой («Friday», «Jumat»)
        else -> SimpleDateFormat("EEEE", Lang.locale).format(Date(triggerAt)).let {
            if (Lang.code in setOf("ru", "kk", "pt")) it.lowercase(Lang.locale) else it
        }
    }
}

private fun midnight(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Угол времени суток на 24-часовом циферблате: полночь сверху, полдень снизу. */
private fun dayAngle(t: Long): Float {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60f
    return minutes / 1440f * 360f - 90f
}

/** Открыть будильники: экран, который дало приложение «Часы», иначе само приложение. */
fun openAlarm(context: Context, a: AlarmInfo) {
    if (Controls.send(context, a.show)) {
        Controls.collapse()
    } else {
        a.pkg?.let { Controls.openApp(context, it) }
    }
}

/**
 * Циферблат сна, как в «Режиме сна» iPhone: 24 часа по кругу, дуга от «сейчас» до будильника
 * переливается из ночного индиго в утренний оранжевый, на концах — луна и будильник.
 * [compact] — маленький вариант для плашки: без рисок и подписи в середине.
 */
@Composable
fun SleepDial(now: Long, triggerAt: Long, modifier: Modifier = Modifier, compact: Boolean = false, animate: Boolean = true) {
    val left = (triggerAt - now).coerceIn(0, DAY - 1)
    val target = left / DAY.toFloat() * 360f
    val sweep = remember { Animatable(if (animate) 0f else target) }
    LaunchedEffect(target) {
        if (animate) sweep.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 90f)) else sweep.snapTo(target)
    }
    // В капсуле циферблат висит часами — там свечение ровное, чтобы не перерисовывать остров каждый кадр
    val transition = rememberInfiniteTransition(label = "dial")
    val glow = if (animate && !compact) {
        transition.animateFloat(0.25f, 0.75f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "glow")
    } else {
        null
    }
    val moon = rememberVectorPainter(Ios.moon)
    val bell = rememberVectorPainter(Ios.alarm)
    val start = dayAngle(now)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * if (compact) 0.16f else 0.115f
            val r = size.minDimension / 2 - stroke / 2 - 1f
            val c = center
            drawCircle(Color.White.copy(alpha = 0.1f), radius = r, style = Stroke(stroke))
            if (!compact) {
                // 24 риски внутри кольца; полночь, 6, 12 и 18 — длиннее и ярче
                for (i in 0 until 24) {
                    val a = Math.toRadians(i * 15.0 - 90.0)
                    val major = i % 6 == 0
                    val outer = r - stroke * 0.75f
                    val inner = outer - if (major) stroke * 0.7f else stroke * 0.35f
                    drawLine(
                        Color.White.copy(alpha = if (major) 0.5f else 0.2f),
                        Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                        Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                        strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            val s = sweep.value
            if (s > 0.5f) {
                rotate(start, c) {
                    drawArc(
                        brush = Brush.sweepGradient(0f to NightTint, (s / 360f).coerceIn(0.02f, 1f) to AlarmTint, center = c),
                        startAngle = 0f, sweepAngle = s, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            val startPos = pointAt(c, r, start)
            val endPos = pointAt(c, r, start + s)
            // Мягкое свечение вокруг будильника
            drawCircle(AlarmTint.copy(alpha = (glow?.value ?: 0.5f) * 0.4f), radius = stroke * 1.2f, center = endPos)
            val knob = stroke * 0.62f
            handle(startPos, NightTint, if (compact) null else moon, knob)
            handle(endPos, AlarmTint, if (compact) null else bell, knob)
        }
        if (!compact) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%d:%02d".format(left / 3_600_000, left / 60_000 % 60),
                    color = Color.White, fontSize = 17.sp, style = Digits, maxLines = 1,
                )
                Text(if (isWakeUp(triggerAt)) tr("сна", "of sleep") else tr("до звонка", "until alarm"), color = Dim, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

private fun pointAt(c: Offset, r: Float, degrees: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
}

private fun DrawScope.handle(at: Offset, color: Color, icon: Painter?, radius: Float) {
    drawCircle(Color.Black, radius = radius * 1.18f, center = at)
    drawCircle(color, radius = radius, center = at)
    if (icon != null) {
        val s = radius * 1.3f
        translate(at.x - s / 2, at.y - s / 2) {
            with(icon) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.Black)) }
        }
    }
}

/** Будильник в капсуле: слева луна (перед подъёмом) или будильник, справа — время звонка. */
@Composable
fun AlarmCompact(a: AlarmInfo, s: IslandSettings) {
    val context = LocalContext.current
    val h = activeHeight(s)
    val wake = isWakeUp(a.triggerAt)
    CompactRow(h, compactGap(s), sideMax(s) * 1.4f,
        left = {
            Icon(if (wake) Ios.moon else Ios.alarm, null, tint = if (wake) NightTint else AlarmTint,
                modifier = Modifier.size(h * if (wake) 0.44f else 0.5f))
        },
        right = {
            Text(clockText(wakeTime(context, a.triggerAt), 16.sp), color = AlarmTint, fontSize = 16.sp, style = Digits, maxLines = 1,
                softWrap = false)
        },
    )
}

/**
 * Раскрытый будильник: слева циферблат сна, справа «Подъём · завтра», крупное время звонка
 * и сколько осталось спать. Нажатие открывает будильники «Часов».
 */
@Composable
fun AlarmExpanded(a: AlarmInfo, s: IslandSettings) {
    val context = LocalContext.current
    val now = rememberWallNow(15_000)
    val left = (a.triggerAt - now).coerceAtLeast(0)
    val wake = isWakeUp(a.triggerAt)
    Row(Modifier.fillMaxWidth().clickable { openAlarm(context, a) }, verticalAlignment = Alignment.CenterVertically) {
        SleepDial(now, a.triggerAt, Modifier.size(96.dp), animate = !s.reduceEffects)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("${if (wake) tr("Подъём", "Wake up") else tr("Будильник", "Alarm")} · ${alarmDay(a.triggerAt, now)}",
                color = Dim, fontSize = 14.sp, maxLines = 1)
            Text(clockText(wakeTime(context, a.triggerAt), 44.sp), color = Color.White, fontSize = 44.sp,
                style = Digits.copy(fontWeight = FontWeight.Normal.island), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (wake) Ios.moon else Ios.alarm, null, tint = if (wake) NightTint else AlarmTint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (wake) trf("Сон — {0}", "Sleep — {0}", durationWords(left))
                    else trf("Через {0}", "In {0}", durationWords(left)),
                    color = AlarmTint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Плашка, когда поставили будильник: маленький циферблат сна, «Подъём» и время звонка,
 * справа — сколько осталось спать. Как подсказка Android «Будильник сработает через…», только красиво.
 */
@Composable
fun AlarmBanner(n: Notice, a: AlarmInfo, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    LaunchedEffect(n.id) { haptic() }
    val now = rememberWallNow(30_000)
    val height = noticeHeight(s)
    val wake = isWakeUp(a.triggerAt)
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.34f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SleepDial(now, a.triggerAt, Modifier.size(height * 0.64f), compact = true, animate = !s.reduceEffects)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (wake) tr("Подъём", "Wake up") else tr("Будильник", "Alarm"), color = Dim, fontSize = 12.sp, maxLines = 1)
                Text(clockText(wakeTime(context, a.triggerAt), 20.sp), color = Color.White, fontSize = 20.sp, style = Digits, maxLines = 1)
            }
        }
        Spacer(Modifier.width(compactGap(s)))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(if (wake) tr("сон", "sleep") else tr("через", "in"), color = Dim, fontSize = 12.sp, maxLines = 1)
            Text(durationWords((a.triggerAt - now).coerceAtLeast(0)), color = AlarmTint, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold.island, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
