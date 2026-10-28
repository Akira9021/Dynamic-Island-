@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.akira.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.akira.island.core.AppMessage
import com.akira.island.core.InboxItem
import com.akira.island.core.CallInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveInfo
import com.akira.island.core.LiveKind
import com.akira.island.core.MsgAction
import com.akira.island.core.ReplyState
import com.akira.island.core.MediaInfo
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.core.formatDuration
import com.akira.island.data.IslandSettings
import com.akira.island.data.MessageStyle
import com.akira.island.data.SettingsRepo
import com.akira.island.service.Messages
import androidx.compose.runtime.collectAsState
import com.akira.island.core.tr
import com.akira.island.core.trf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Цифры одной ширины, чтобы время не «прыгало». Шрифт и толщина — из «Оформления → Шрифт» */
val Digits: TextStyle
    get() = TextStyle(fontFamily = IslandType.family, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold.island)

/** Высота капсулы, когда в ней что-то есть: чуть толще пустой, чтобы не выглядеть тонкой полоской. */
fun activeHeight(s: IslandSettings): Dp = (s.height + s.activeExtra).dp

/** Свободное место над камерой посередине — содержимое туда не заходит. */
fun compactGap(s: IslandSettings): Dp = s.cameraGap.dp.coerceAtLeast(8.dp)

/** Самое широкое содержимое одной боковой части. */
fun sideMax(s: IslandSettings): Dp = activeHeight(s) * 2.6f

/** Высота события: заметно выше свёрнутого острова, как у раскрытых уведомлений iPhone (62 при высоте 37). */
fun noticeHeight(s: IslandSettings): Dp = (s.height.dp * 1.68f).coerceAtLeast(56.dp)

/** Высота входящего звонка: аватар, имя и две большие кнопки в одну строку (84 при высоте 37). */
fun callHeight(s: IslandSettings): Dp = (s.height.dp * 2.27f).coerceAtLeast(76.dp)

/** Ниже этой линии камера уже не мешает тексту — сюда ставится содержимое раскрытых карточек. */
fun cameraInset(s: IslandSettings): Dp = s.height.dp * 0.5f + 11.5.dp

@Composable
fun CompactContent(act: Act, s: IslandSettings, wideWidth: Dp) {
    val h = activeHeight(s)
    val gap = compactGap(s)
    val side = sideMax(s)
    val icon = h * 0.5f
    when (act) {
        // Пустая капсула; на экране блокировки слева от камеры — замок
        is Act.Idle -> Box(Modifier.size(s.width.dp, s.height.dp), contentAlignment = Alignment.CenterStart) {
            val keyguard by IslandBus.keyguard.collectAsState()
            AnimatedVisibility(s.evLock && keyguard, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Icon(Ios.lock, null, tint = Color.White, modifier = Modifier.padding(start = s.height.dp * 0.38f).size(s.height.dp * 0.46f))
            }
        }

        is Act.MediaA -> MediaCompact(act.m, s)

        is Act.TimerA -> {
            val now = rememberNow(250)
            if (act.t.ringing) {
                val wobble = rememberInfiniteTransition(label = "bell")
                val angle = if (s.reduceEffects || !s.fxShake) null
                else wobble.animateFloat(-18f, 18f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "a")
                CompactRow(h, gap, side * 1.4f,
                    left = {
                        Icon(Ios.bell, null, tint = Tints.Orange, modifier = Modifier.size(icon).graphicsLayer {
                            rotationZ = angle?.value ?: 0f
                            transformOrigin = TransformOrigin(0.5f, 0.1f)
                        })
                    },
                    right = { Text(tr("Таймер", "Timer"), color = Tints.Orange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold.island) },
                )
            } else {
                val left = act.t.left(now)
                val color = if (left <= 10_000 && !act.t.paused) Tints.Red else Tints.Orange
                CompactRow(h, gap, side * 1.4f,
                    left = { Icon(if (act.t.paused) Ios.pause else Ios.timer, null, tint = color, modifier = Modifier.size(icon)) },
                    right = { TimeText(formatDuration(left + 999), color, 18, FontWeight.Bold.island) },
                )
            }
        }

        is Act.StopwatchA -> {
            val now = rememberNow(250, active = act.w.running)
            CompactRow(h, gap, side * 1.4f,
                left = { Icon(Ios.stopwatch, null, tint = Tints.Orange, modifier = Modifier.size(icon)) },
                right = { TimeText(formatDuration(act.w.elapsed(now)), Tints.Orange, 18, FontWeight.Bold.island) },
            )
        }

        is Act.ChronoA -> {
            val now = rememberWallNow(500)
            // Запись экрана — красная, как на iPhone; таймеры и секундомеры Часов — оранжевые
            val recording = act.c.isRecording
            val tint = if (recording) Tints.Red else Tints.Orange
            CompactRow(h, gap, side * 1.4f,
                left = {
                    when {
                        recording -> Equalizer(tint, true, s.liveEqualizer, Modifier.size(h * 0.62f, h * 0.42f), bars = 5)
                        act.c.pausedMs != null -> Icon(Ios.pause, null, tint = tint, modifier = Modifier.size(icon))
                        else -> AppIcon(act.c.icon, h * 0.56f, Ios.timer, tint)
                    }
                },
                right = { TimeText(formatDuration(act.c.elapsed(now)), tint, 18, FontWeight.Bold.island) },
            )
        }

        is Act.CallA -> if (act.c.incoming) {
            IncomingCall(act.c, s, wideWidth)
        } else {
            val now = rememberWallNow(500)
            CompactRow(h, gap, side * 2f,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ios.call, null, tint = Tints.Green, modifier = Modifier.size(h * 0.42f))
                        Spacer(Modifier.width(6.dp))
                        TimeText(formatDuration(now - act.c.base), Tints.Green, 15)
                    }
                },
                right = { Equalizer(Tints.Green, true, s.liveEqualizer, Modifier.size(h * 0.7f, h * 0.42f), bars = 6) },
            )
        }

        is Act.TorchA -> CompactRow(h, gap, side,
            left = { Icon(Ios.flashlight, null, tint = Tints.Yellow, modifier = Modifier.size(icon)) },
            right = { GlowDot(Tints.Yellow, h * 0.26f, !s.reduceEffects) },
        )

        is Act.ProgressA -> CompactRow(h, gap, side,
            left = { AppIcon(act.p.icon, h * 0.56f, Ios.download, Tints.Blue) },
            right = {
                if (act.p.indeterminate) {
                    Icon(Ios.download, null, tint = Tints.Blue, modifier = Modifier.size(icon))
                } else {
                    // Кольцо доезжает до нового процента, а не прыгает
                    val fraction by animateFloatAsState(act.p.fraction, tween(450, easing = FastOutSlowInEasing), label = "progress")
                    ProgressRing(fraction, Tints.Blue, Modifier.size(h * 0.52f), stroke = 3.dp)
                }
            },
        )

        is Act.ChargingA -> CompactRow(h, gap, side * 1.6f,
            left = { Icon(Ios.flash, null, tint = Tints.Green, modifier = Modifier.size(icon)) },
            right = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeText("${act.b.level}%", Tints.Green, 15)
                    Spacer(Modifier.width(5.dp))
                    BatteryGlyph(act.b.level, Tints.Green, Modifier.size(h * 0.6f, h * 0.3f))
                }
            },
        )

        is Act.LiveA -> LiveCompact(act.l, s)

        is Act.AlarmA -> AlarmCompact(act.a, s)

        is Act.NoticeA -> NoticeCompact(act.n, s, wideWidth)

        is Act.PayA -> PayReady(tr("Оплата", "Pay"), tr("К терминалу", "Hold near reader"), act.p.icon, s, wideWidth)

        // «История уведомлений»: значки приложений, от которых что-то ждёт, и сколько всего
        is Act.InboxA -> CompactRow(h, gap, side * 1.4f,
            left = { AppStack(act.items, h * 0.56f, islandFill(s)) },
            right = { TimeText("${act.items.size}", Tints.Blue, 16) },
        )

    }
}

@Composable
private fun TimeText(text: String, color: Color, size: Int = 16, weight: FontWeight = FontWeight.SemiBold.island) {
    Text(text, color = color, fontSize = size.sp, style = Digits.copy(fontWeight = weight), maxLines = 1, softWrap = false)
}

/** Мягко светящаяся точка — признак того, что фонарик горит. Свечение только перерисовывается. */
@Composable
private fun GlowDot(color: Color, size: Dp, animate: Boolean) {
    // Фонарик и навигатор горят долго — свечение идёт с частотой из «Эффектов» (по умолчанию 60 кадров), а не до 120
    val time = rememberFxClock(animate)
    Canvas(Modifier.size(size * 1.8f)) {
        val k = if (animate) 0.55f + 0.45f * pingPong(time.value, 1100) else 1f
        val r = this.size.minDimension / 2
        drawCircle(color.copy(alpha = 0.25f * k), radius = r * k)
        drawCircle(color, radius = r / 1.8f)
    }
}

/**
 * Левая и правая части свёрнутой капсулы, как на iPhone: значок у левого края, текст или время
 * у правого, посередине камера. Капсула шире пустой на «Ширину с активностью»; если содержимое
 * не помещается, оно чуть уменьшается, а если и так длиннее — капсула расширяется ровно настолько,
 * чтобы не заходить на камеру.
 * Отступ от края равен отступу сверху — содержимое вписано в скругление, как требует Apple.
 */
@Composable
fun CompactRow(
    h: Dp,
    gap: Dp,
    side: Dp,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
    shrink: Boolean = true,
) {
    // Капсула с активностью шире спокойной на «Ширину с активностью»; длинный текст раздвинет её сам
    val st = SettingsRepo.state.collectAsState().value
    val baseWidth = st.width.dp + st.activeWidth.dp
    val density = LocalDensity.current
    SubcomposeLayout { _ ->
        val hPx = h.roundToPx()
        val pad = (h * 0.3f).roundToPx()
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxWidth = side.roundToPx().coerceAtLeast(1), maxHeight = hPx)
        var l = subcompose("l") { Box(contentAlignment = Alignment.Center, content = left) }.first().measure(loose)
        var r = subcompose("r") { Box(contentAlignment = Alignment.Center, content = right) }.first().measure(loose)
        val base = baseWidth.roundToPx()
        val sideNeed = maxOf(l.width, r.width)
        // В узкой капсуле содержимое сначала уменьшается (до 70%) — как будто нарисовано мельче, —
        // и только если и так не помещается, капсулу раздвигает
        val sideRoom = (base - gapPx) / 2 - pad
        val k = if (shrink && sideNeed > sideRoom && sideRoom > 0) (sideRoom.toFloat() / sideNeed).coerceIn(0.7f, 1f) else 1f
        if (k < 1f) {
            val small = Density(density.density * k, density.fontScale)
            l = subcompose("ls") {
                CompositionLocalProvider(LocalDensity provides small) { Box(contentAlignment = Alignment.Center, content = left) }
            }.first().measure(loose)
            r = subcompose("rs") {
                CompositionLocalProvider(LocalDensity provides small) { Box(contentAlignment = Alignment.Center, content = right) }
            }.first().measure(loose)
        }
        val needed = 2 * (pad + maxOf(l.width, r.width)) + gapPx
        val w = maxOf(needed, base)
        layout(w, hPx) {
            l.place(pad, (hPx - l.height) / 2)
            r.place(w - pad - r.width, (hPx - r.height) / 2)
        }
    }
}

@Composable
private fun MediaCompact(m: MediaInfo, s: IslandSettings) {
    val h = activeHeight(s)
    val art = h * 0.58f
    val egg by IslandBus.egg.collectAsState()
    // Новый трек: обложка сменяется плавно, цвет эквалайзера перетекает в цвет новой обложки
    val accent by animateColorAsState(m.accent, tween(400), label = "accent")
    CompactRow(h, compactGap(s), sideMax(s),
        left = { Crossfade(m.art, animationSpec = tween(300), label = "art") { Art(it, art, art * 0.24f) } },
        right = {
            // Пасхалка: вместо эквалайзера бьётся душа
            if (egg) SoulHeart(Modifier.size(h * 0.5f)) else Equalizer(accent, m.playing, s.liveEqualizer, Modifier.size(h * 0.6f, h * 0.42f))
        },
    )
}

/**
 * Событие как на iPhone. Широкое — остров разворачивается почти на всю ширину экрана:
 * слева значок и подпись, справа состояние, батарейка или кольцо. Пилюля — короткое
 * переключение (режим звука), значок качается. Сообщение — аватар, имя и текст.
 */
@Composable
private fun NoticeCompact(n: Notice, s: IslandSettings, wideWidth: Dp) {
    when {
        n.style == NoticeStyle.MESSAGE && n.message != null && s.messageStyle == MessageStyle.PILL -> MessagePill(n, n.message, s)
        n.style == NoticeStyle.MESSAGE && n.message != null -> MessageCard(n, n.message, s, wideWidth)
        n.style == NoticeStyle.PAY_DONE -> PayDone(n, s, wideWidth)
        n.style == NoticeStyle.LIVE && n.live != null -> LiveBanner(n, n.live, s, wideWidth)
        n.style == NoticeStyle.ALARM && n.alarm != null -> AlarmBanner(n, n.alarm, s, wideWidth)
        n.style == NoticeStyle.EGG -> EggBanner(n, s)
        n.style == NoticeStyle.SENT -> SentBanner(n, s, wideWidth)
        n.style == NoticeStyle.UPDATE -> UpdateCard(n, s, wideWidth)
        n.style == NoticeStyle.PILL || n.title.isBlank() && n.state == null -> PillNotice(n, s)
        else -> WideNotice(n, s, wideWidth)
    }
}

@Composable
private fun WideNotice(n: Notice, s: IslandSettings, wideWidth: Dp) {
    // Значок «выпрыгивает», батарейка и кольцо заполняются — как на iPhone
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.3f) }
    val fill = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0f) }
    LaunchedEffect(n.id) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 320f)) }
        fill.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val height = noticeHeight(s)
    // Как в ролике: слева значок и что случилось, справа — значение цветом события и батарейка или кольцо.
    // У наушников с известным зарядом значение — проценты, иначе — подпись («Подключено»)
    val value = when {
        n.ring != null -> "${(n.ring * 100).toInt()}%"
        else -> n.state ?: n.caption
    }
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.32f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(n.icon, null, tint = n.tint, modifier = Modifier.size(height * 0.355f).scale(pop.value))
            Spacer(Modifier.width(9.dp))
            // «Не беспокоить», «Режим полёта» не влезают до камеры — шрифт чуть меньше, а не многоточие
            FitText(n.title, Color.White, 17.sp, 13.sp, FontWeight.SemiBold.island, Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                FitText(value, n.tint, if (value.length <= 5) 17.sp else 16.sp, 12.sp, FontWeight.SemiBold.island,
                    Modifier.weight(1f, fill = false), style = Digits)
            }
            if (n.battery != null) {
                Spacer(Modifier.width(8.dp))
                BatteryGlyph((n.battery * fill.value).toInt(), n.tint, Modifier.size(30.dp, 14.dp))
            }
            if (n.ring != null) {
                Spacer(Modifier.width(8.dp))
                ProgressRing(n.ring * fill.value, n.tint, Modifier.size(20.dp), stroke = 3.dp)
            }
        }
    }
}

/** Пилюля режима звука: значок слева качается, как колокольчик на iPhone, состояние справа. */
@Composable
private fun PillNotice(n: Notice, s: IslandSettings) {
    val h = activeHeight(s)
    val swing = remember(n.id) { Animatable(0f) }
    LaunchedEffect(n.id) {
        if (s.reduceEffects || !s.fxShake) return@LaunchedEffect
        // Затухающее покачивание: 22° → -16° → 12° → … → 0
        var amp = 22f
        repeat(6) {
            swing.animateTo(amp, tween(70))
            amp = -amp * 0.75f
        }
        swing.animateTo(0f, spring(dampingRatio = 0.5f))
    }
    CompactRow(h, compactGap(s), h * 2.4f,
        left = {
            Icon(
                n.icon, null, tint = n.tint,
                modifier = Modifier.size(h * 0.5f).graphicsLayer {
                    rotationZ = swing.value
                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                },
            )
        },
        right = {
            if (n.state != null) {
                Text(n.state, color = n.tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1, softWrap = false)
            }
        },
        // Как в ролике: подпись в полный размер, пилюля для неё чуть шире
        shrink = false,
    )
}

/**
 * Уведомление приложения — как развёрнутый остров iPhone, но по меркам всплывающего уведомления Android:
 * та же ширина (по 16 dp от краёв), аватар 40 dp, имя 15 sp, текст 14 sp, кнопки 14 sp. Сверху, по бокам
 * от камеры, — значок и название приложения и «сейчас»; само уведомление начинается под строкой
 * состояния, там же, где системное. Ниже — кнопки из уведомления («Ответить», «Пометить прочитанным»).
 * Все размеры в sp растут вместе с размером шрифта Android, ширина и отступы — с настройками острова.
 */
@Composable
private fun MessageCard(n: Notice, m: AppMessage, s: IslandSettings, @Suppress("UNUSED_PARAMETER") wideWidth: Dp) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val iconSize = 40.dp
    val reply by IslandBus.reply.collectAsState()
    val locked by IslandBus.locked.collectAsState()
    // На заблокированном телефоне — как в шторке: без кнопок, а скрытые уведомления без имени и текста
    val hidden = locked && m.sensitive
    val replyAction = reply?.takeIf { it.noticeId == n.id && !locked }?.action
    val accent = rememberAccent(s, m.accent)
    var open by remember(n.id) { mutableStateOf(false) }
    val dim = Color.White.copy(alpha = 0.55f)

    // Пока отвечают или листают переписку, плашка не уходит по таймеру
    DisposableEffect(open, replyAction != null) {
        IslandBus.noticeHold.value = open || replyAction != null
        onDispose { IslandBus.noticeHold.value = false }
    }
    // Плашка ушла — ввод закрывается, окну возвращается «неактивный» режим
    DisposableEffect(n.id) {
        onDispose { if (IslandBus.reply.value?.noticeId == n.id) IslandBus.reply.value = null }
    }

    Column(
        Modifier
            .animateContentSize(islandSpec(s))
            .width(messageWidth(s))
            .padding(bottom = 12.dp),
    ) {
        // Шапка на уровне камеры: слева приложение, справа время — как боковые части острова iPhone
        Row(
            Modifier.fillMaxWidth().height(s.height.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (m.appIcon != null) {
                    Image(m.appIcon, null, Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(6.dp))
                }
                Text(m.appName, color = dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(compactGap(s)))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("сейчас", "now"), color = dim, fontSize = 12.sp, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(4.dp))
                Icon(Ios.bell, null, tint = dim, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height((messageTop(s) - s.height.dp).coerceAtLeast(4.dp)))
        // Ниже шапки — крупнее или мельче по «Размеру содержимого»; шапка у камеры остаётся как есть
        Scaled(s.contentScale) {
            Column {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                    NotifIcon(if (hidden) m.copy(avatar = null) else m, n, iconSize, Color(s.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hidden) m.appName else n.title, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp,
                                fontWeight = FontWeight.SemiBold.island, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (m.history.size > 1 && !hidden) {
                                Spacer(Modifier.width(8.dp))
                                CountPill(m.history.size, open, accent) {
                                    haptic()
                                    open = !open
                                }
                            }
                        }
                        if (hidden) {
                            Text(tr("Содержимое скрыто", "Content hidden"), color = dim, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1)
                        } else if (open && m.history.size > 1) {
                            m.history.forEachIndexed { i, line ->
                                Text(
                                    line, color = Color.White.copy(alpha = if (i == m.history.lastIndex) 0.9f else 0.6f),
                                    fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                        } else if (m.text.isNotEmpty()) {
                            Text(
                                m.text, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 19.sp, maxLines = s.msgLines.coerceIn(1, 8),
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (replyAction != null) {
                    // Строка ответа стоит под текстом, как кнопки, — на одной линии с ним
                    Box(Modifier.padding(start = 16.dp + iconSize + 12.dp, end = 16.dp)) {
                        ReplyField(replyAction, accent) { text ->
                            if (text.isBlank()) return@ReplyField
                            if (Controls.sendReply(context, replyAction, text)) {
                                Messages.onReplied(m.key)
                                IslandBus.reply.value = null
                                IslandBus.notice.value = null
                                // Как в ролике: на миг «Отправлено» с галочкой и кому
                                IslandBus.post(
                                    Notice(
                                        title = tr("Отправлено", "Sent"),
                                        state = n.title,
                                        icon = n.icon,
                                        tint = accent,
                                        style = NoticeStyle.SENT,
                                        extraMs = -SettingsRepo.current.noticeDurationMs / 2L,
                                    ),
                                )
                            } else {
                                // Поле остаётся открытым с текстом — можно попробовать ещё раз или ответить в приложении
                                Toast.makeText(context, tr("Не удалось отправить ответ", "Couldn't send the reply"), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (m.actions.isNotEmpty() && !locked) {
                    // Кнопки стоят под текстом, на одной линии с ним, как в шторке
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp + iconSize + 12.dp, end = 16.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        m.actions.forEachIndexed { i, a ->
                            // Последняя кнопка уступает место, если подпись длинная
                            TextAction(a.title, accent, if (i == m.actions.lastIndex) Modifier.weight(1f, fill = false) else Modifier, size = 14.sp) {
                                if (a.isReply) {
                                    IslandBus.reply.value = ReplyState(n.id, a)
                                } else {
                                    Controls.send(context, a.intent)
                                    IslandBus.notice.value = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Уведомление пилюлей («Вид уведомлений → Пилюля»): у камеры слева аватар и кто пишет, справа приложение,
 * под ними одна строка текста. Занимает меньше места, чем карточка; нажатие открывает приложение.
 */
@Composable
private fun MessagePill(n: Notice, m: AppMessage, s: IslandSettings) {
    val locked by IslandBus.locked.collectAsState()
    val hidden = locked && m.sensitive
    val dim = Color.White.copy(alpha = 0.6f)
    Column(Modifier.width(messageWidth(s)).padding(bottom = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(s.height.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val face = if (hidden) m.appIcon else m.avatar ?: m.appIcon
                if (face != null) {
                    Image(face, null, Modifier.size(20.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (hidden) m.appName else n.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold.island,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(compactGap(s)))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(m.appName, color = dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (m.appIcon != null) {
                    Spacer(Modifier.width(5.dp))
                    Image(m.appIcon, null, Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }
        Scaled(s.contentScale) {
            Text(
                if (hidden) tr("Содержимое скрыто", "Content hidden") else m.text.ifEmpty { m.appName },
                color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
            )
        }
    }
}

/** Значки приложений внахлёст, новое — сверху и слева; у каждого ободок цвета острова. */
@Composable
private fun AppStack(items: List<InboxItem>, size: Dp, ring: Color) {
    val apps = items.asReversed().distinctBy { it.pkg }.take(3)
    val step = size * 0.55f
    Box(Modifier.size(width = size + step * (apps.size - 1).coerceAtLeast(0), height = size)) {
        for (k in apps.indices.reversed()) {
            val icon = apps[k].appIcon
            Box(
                Modifier.offset(x = step * k).size(size).clip(CircleShape).background(ring).padding(1.5.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Ios.chatbubble, null, tint = Tints.Blue, modifier = Modifier.fillMaxSize().padding(3.dp))
                }
            }
        }
    }
}

/**
 * Ширина плашки уведомления — как у всплывающего уведомления Android: по 16 dp от краёв экрана;
 * уже — по «Ширине раскрытого острова».
 */
@Composable
fun messageWidth(s: IslandSettings): Dp {
    val full = (LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtMost(460.dp)
    return (full * (s.expandedWidthPercent.coerceIn(60, 100) / 100f)).coerceAtLeast((s.width.dp + 60.dp).coerceAtMost(full))
}

/**
 * Откуда в плашке начинается само уведомление: под строкой состояния, на той же высоте, что у системного
 * уведомления, — с учётом того, на сколько остров опущен в настройках. Не выше шапки у камеры.
 */
@Composable
fun messageTop(s: IslandSettings): Dp {
    val status = statusBarHeight()
    return maxOf(s.height.dp + 6.dp, status + 24.dp - s.topOffset.dp)
}

/** Высота строки состояния — из ресурсов системы: окну острова отступы строки состояния не приходят. */
@Composable
private fun statusBarHeight(): Dp {
    val res = LocalContext.current.resources
    val density = LocalDensity.current
    return remember(res, density) {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) with(density) { res.getDimensionPixelSize(id).toDp() } else 24.dp
    }
}

/** Круглый аватар (или значок приложения) со значком приложения в углу. */
@Composable
private fun NotifIcon(m: AppMessage, n: Notice, size: Dp, ring: Color) {
    Box(Modifier.size(size + 4.dp)) {
        val big = m.avatar ?: m.appIcon
        if (big != null) {
            Image(big, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        }
        if (m.avatar != null && m.appIcon != null) {
            Image(
                m.appIcon, null,
                Modifier.align(Alignment.BottomEnd).size(size * 0.44f).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape),
            )
        }
    }
}

/** Счётчик непрочитанных со стрелкой: нажатие раскрывает всю переписку. */
@Composable
private fun CountPill(count: Int, open: Boolean, accent: Color, onClick: () -> Unit) {
    val ink = onAccent(accent)
    val rotation by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "chevron")
    Row(
        Modifier.height(24.dp).clip(CircleShape).background(accent).clickable(onClick = onClick).padding(start = 9.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count", color = ink, fontSize = 13.sp, style = Digits, maxLines = 1)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = ink, modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = rotation })
    }
}

/** Кнопка под текстом: просто жирная подпись цвета [accent], чуть тускнеет под пальцем. */
@Composable
private fun TextAction(title: String, accent: Color, modifier: Modifier = Modifier, size: TextUnit = 16.sp, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interaction, indication = null) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, color = accent.copy(alpha = if (pressed) 0.55f else 1f), fontSize = size, fontWeight = FontWeight.SemiBold.island,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Строка ответа как в ролике и в быстром ответе iOS: поле со скруглением, а справа от него — круглая
 * кнопка со стрелкой вверх цвета [accent]. Окну острова на это время выдаётся фокус (см. IslandService),
 * поэтому клавиатура покажется.
 */
@Composable
private fun ReplyField(action: MsgAction, accent: Color, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Окно получает фокус не сразу, поэтому просим клавиатуру несколько раз
        repeat(3) {
            delay(180)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val ready = text.isNotBlank()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = IslandType.family),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text("${action.title.ifBlank { "Ответ" }}…", color = Tints.Gray, fontSize = 15.sp, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        RoundButton(
            Ios.arrowUp, size = 40.dp,
            bg = if (ready) accent else accent.copy(alpha = 0.35f), fg = onAccent(accent), iconScale = 0.5f,
        ) { onSend(text) }
    }
}

/**
 * Оплата, как Apple Pay и как в ролике: слева знак бесконтактной оплаты, дуги которого загораются
 * по очереди, и «Оплата»; справа «К терминалу» и значок банка. Посередине — место под камеру.
 */
@Composable
private fun PayReady(title: String, caption: String?, appIcon: ImageBitmap?, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.29f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ContactlessWaves(Tints.Lavender, !s.reduceEffects, Modifier.size(height * 0.42f))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1)
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (caption != null) {
                // «Hold near reader» длиннее «К терминалу» — чуть уменьшается, а не обрезается
                FitText(caption, Tints.Gray, 14.sp, 11.sp, FontWeight.Normal.island, Modifier.weight(1f, fill = false))
            }
            if (appIcon != null) {
                Spacer(Modifier.width(9.dp))
                Image(appIcon, null, Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)))
            }
        }
    }
}

/** Оплата прошла: зелёный круг выпрыгивает, галочка прорисовывается, справа сумма — как «Готово» в Apple Pay. */
@Composable
private fun PayDone(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val haptic = rememberHaptic()
    LaunchedEffect(n.id) { haptic() }
    val height = noticeHeight(s)
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.29f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CheckBadge(n.tint, !s.reduceEffects, Modifier.size(height * 0.42f))
            Spacer(Modifier.width(10.dp))
            Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1)
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (n.state != null) {
                Text(n.state, color = n.tint, fontSize = 18.sp, style = Digits.copy(fontWeight = FontWeight.Bold.island), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Цвет «живой активности»: навигатор зелёный, такси жёлтое, доставка оранжевая. */
fun liveTint(kind: LiveKind): Color = when (kind) {
    LiveKind.NAV -> Tints.Green
    LiveKind.RIDE -> Tints.Yellow
    LiveKind.DELIVERY -> Tints.Orange
}

/**
 * Главное из уведомления для узкой капсулы: расстояние до манёвра; у навигатора без расстояния —
 * время в пути, у такси и доставки — заголовок. Если показывать нечего, возвращает null.
 */
fun liveShort(l: LiveInfo): String? = l.distance
    ?: if (l.kind == LiveKind.NAV) {
        l.eta?.substringAfterLast(" · ")?.takeIf { it.isNotBlank() }
    } else {
        l.title.ifBlank { l.text }.take(18).takeIf { it.isNotBlank() }
    }

/** Значок «живой активности»: стрелка манёвра, если известно куда ехать, иначе картинка из уведомления или значок приложения. */
@Composable
fun LiveGlyph(l: LiveInfo, size: Dp, tint: Color) {
    when {
        l.turn != null -> TurnArrow(l.turn, tint, Modifier.size(size))
        l.art != null -> Image(l.art, null, Modifier.size(size).clip(CircleShape))
        else -> AppIcon(l.icon, size, Ios.navigate, tint)
    }
}

/** Навигатор, такси или доставка в капсуле: значок приложения у левого края, главное — у правого. */
@Composable
private fun LiveCompact(l: LiveInfo, s: IslandSettings) {
    val h = activeHeight(s)
    val tint = liveTint(l.kind)
    CompactRow(h, compactGap(s), sideMax(s) * 1.5f,
        left = { LiveGlyph(l, if (l.turn != null) h * 0.51f else h * 0.56f, tint) },
        right = {
            val short = liveShort(l)
            if (short != null) {
                Text(short, color = tint, fontSize = 17.sp, style = Digits.copy(fontWeight = FontWeight.Bold.island), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            } else {
                GlowDot(tint, h * 0.22f, !s.reduceEffects)
            }
        },
    )
}

/**
 * Широкая плашка при запуске навигатора, такси или доставки — как у iPhone и в ролике: стрелка и расстояние
 * слева, улица и время прибытия справа; потом остров сжимается в капсулу.
 */
@Composable
private fun LiveBanner(n: Notice, l: LiveInfo, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    val tint = liveTint(l.kind)
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.3f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.32f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.scale(pop.value)) { LiveGlyph(l, height * 0.39f, tint) }
            Spacer(Modifier.width(10.dp))
            Text(
                l.distance ?: l.title, color = Color.White, fontSize = if (l.distance != null) 19.sp else 16.sp,
                style = Digits.copy(fontWeight = FontWeight.Bold.island), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(compactGap(s)))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            if (l.text.isNotBlank()) {
                Text(l.text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            }
            // На узкой плашке — коротко: «12:31 · 19 ч»; полностью — в раскрытом виде
            val arrive = trf("Прибытие в {0}", "Arrive {0}", "")
            val shortEta = l.eta?.removePrefix(arrive)?.removePrefix("Прибытие в ")?.removePrefix("Arrive ")?.split(" · ")?.take(2)
                ?.joinToString(" · ")
            if (shortEta != null) {
                Text(shortEta, color = tint, fontSize = 13.sp, style = Digits, maxLines = 1)
            }
        }
    }
}

/**
 * Входящий звонок, как на iPhone и в ролике: аватар, подпись с именем и две круглые кнопки в одну строку
 * посередине высоты; зелёная кнопка «дышит», пока звонят. Камера приходится между именем и кнопками.
 */
@Composable
private fun IncomingCall(c: CallInfo, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    val breath = rememberInfiniteTransition(label = "answer")
    val wave = if (s.reduceEffects) null else breath.animateFloat(0f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing)), label = "w")
    val pulse = Modifier.graphicsLayer {
        val k = wave?.let { 1f + 0.06f * kotlin.math.sin(it.value * 2 * Math.PI).toFloat().coerceAtLeast(0f) } ?: 1f
        scaleX = k
        scaleY = k
    }
    Row(
        Modifier.width(wideWidth).height(callHeight(s)).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFDBA74), Color(0xFFF472B6)))),
            contentAlignment = Alignment.Center,
        ) {
            if (c.avatar != null) {
                Image(c.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold.island)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(tr("Входящий вызов", "Incoming call"), color = Tints.Gray, fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1)
            Text(c.name, color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        RoundButton(Ios.call, size = 46.dp, bg = Tints.Red, iconScale = 0.52f, iconRotation = 135f) {
            Controls.endCall(context, c)
        }
        Spacer(Modifier.width(11.dp))
        RoundButton(Ios.call, pulse, size = 46.dp, bg = Tints.Green, iconScale = 0.52f) {
            Controls.answerCall(context, c)
        }
    }
}
