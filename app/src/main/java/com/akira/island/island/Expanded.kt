@file:OptIn(ExperimentalFoundationApi::class)

package com.akira.island.island

import android.content.Context
import android.media.AudioManager
import android.text.format.DateFormat
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import com.akira.island.service.NotifListener
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.BatteryInfo
import com.akira.island.core.CallInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Lang
import com.akira.island.core.LiveInfo
import com.akira.island.core.Lyrics
import com.akira.island.core.LyricsStatus
import com.akira.island.core.MediaInfo
import com.akira.island.core.PlayerAction
import com.akira.island.core.ProgressInfo
import com.akira.island.core.StopwatchState
import com.akira.island.core.Tints
import com.akira.island.core.TimerState
import com.akira.island.core.TorchState
import com.akira.island.core.formatDuration
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.core.InboxItem
import com.akira.island.data.IslandSettings
import com.akira.island.service.Inbox
import com.akira.island.data.QuickAction
import com.akira.island.data.SettingsRepo
import com.akira.island.service.ClockApps
import com.akira.island.service.ClockControl
import com.akira.island.service.ClockScreenReader
import java.text.SimpleDateFormat
import java.util.Date

private val Dim = Color.White.copy(alpha = 0.55f)

@Composable
fun ExpandedContent(act: Act, s: IslandSettings, lyrics: Lyrics?, battery: BatteryInfo, width: Dp) {
    // Фонарик, как в iOS 18, — не широкая плашка, а высокая карточка с лучом
    if (act is Act.TorchA) {
        TorchCard(act.t, s)
        return
    }
    // Сверху — полоса камеры: содержимое начинается ниже выреза, чтобы он ничего не закрывал.
    // Само содержимое — крупнее или мельче по «Размеру содержимого»
    Box(Modifier.width(width).heightIn(max = s.expandedMaxHeight.dp).verticalScroll(rememberScrollState())
        .padding(start = 22.dp, end = 22.dp, top = cameraInset(s), bottom = 20.dp)) {
        Scaled(s.contentScale) {
            when (act) {
                is Act.WeatherA -> WeatherDetails(act.weather)
                is Act.MediaA -> MediaExpanded(act.m, s, lyrics)
                is Act.TimerA -> TimerExpanded(act.t, s)
                is Act.StopwatchA -> StopwatchExpanded(act.w)
                is Act.TorchA -> Unit
                is Act.ChronoA -> ChronoExpanded(act.c, s)
                is Act.ProgressA -> ProgressExpanded(act.p)
                is Act.LiveA -> LiveExpanded(act.l)
                is Act.CallA -> CallExpanded(act.c)
                is Act.AlarmA -> AlarmExpanded(act.a, s)
                is Act.InboxA -> InboxExpanded(act.items)
                // Зарядка раскрывается как пустой остров: часы, заряд и кнопки — зарядка не закрывает их собой
                else -> IdleExpanded(s, battery)
            }
        }
    }
}

/**
 * «История уведомлений» раскрытой: последние уведомления, новое сверху. Нажатие открывает уведомление,
 * «Очистить» убирает все. На заблокированном телефоне — как в шторке: скрытые без имени и текста.
 */
@Composable
private fun InboxExpanded(items: List<InboxItem>) {
    val context = LocalContext.current
    val locked by IslandBus.locked.collectAsState()
    val now = rememberWallNow(30_000)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Уведомления", "Notifications"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island,
                modifier = Modifier.weight(1f))
            PillButton(Ios.close, tr("Очистить историю", "Clear history")) {
                Inbox.clear()
                Controls.collapse()
            }
        }
        Spacer(Modifier.height(8.dp))
        items.asReversed().take(INBOX_SHOWN).forEach { item ->
            val hidden = locked && item.sensitive
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (!Controls.send(context, item.intent)) Controls.openApp(context, item.pkg)
                        Inbox.remove(item.key)
                        Controls.collapse()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val face = if (hidden) item.appIcon else item.avatar ?: item.appIcon
                if (face != null) {
                    Image(face, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                        Icon(Ios.chatbubble, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hidden) item.appName else item.title, color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold.island, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        Text(ago(now - item.time), color = Dim, fontSize = 12.sp, maxLines = 1)
                    }
                    val text = if (hidden) tr("Содержимое скрыто", "Content hidden") else item.text
                    if (text.isNotEmpty()) {
                        Text(text, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!locked && NotifListener.canDismiss(item.key)) {
                    RoundButton(Ios.close, size = 32.dp, iconScale = 0.45f) { NotifListener.dismiss(item.key) }
                }
            }
        }
        if (items.size > INBOX_SHOWN) {
            Text(trf("И ещё {0}", "And {0} more", items.size - INBOX_SHOWN), color = Dim, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Сколько уведомлений истории видно в раскрытом острове: больше в окно не помещается. */
private const val INBOX_SHOWN = 5

/** «сейчас», «5 мин», «2 ч». */
private fun ago(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> tr("сейчас", "now")
        min < 60 -> trf("{0} мин", "{0} min", min)
        else -> trf("{0} ч", "{0} h", min / 60)
    }
}

/**
 * Плеер — как в ролике: обложка 60, название 18 и исполнитель, справа волна; строка текста песни
 * заливается белым по мере пения; время по краям тонкой полосы и три кнопки посередине.
 */
@Composable
private fun MediaExpanded(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?) {
    val context = LocalContext.current
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 260.dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerArt(m, s, Modifier.size(if (narrow) 44.dp else 60.dp).clickable { Controls.openApp(context, m.pkg) })
                Spacer(Modifier.width(if (narrow) 8.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1,
                        modifier = Modifier.marqueeFade().basicMarquee(iterations = Int.MAX_VALUE))
                    if (m.artist.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(m.artist, color = Tints.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(if (narrow) 6.dp else 10.dp))
                val egg by IslandBus.egg.collectAsState()
                if (egg) SoulHeart(Modifier.size(24.dp)) else Equalizer(m.accent, m.playing, s.liveEqualizer, Modifier.size(if (narrow) 20.dp else 30.dp, 24.dp))
            }
        }

        MediaTimeline(m, s, lyrics)
        MediaControls(m.like, m.dislike, m.playing)
    }
}

/** Only the lyric/timeline subtree ticks; artwork, title and playback controls stay outside it. */
@Composable
private fun MediaTimeline(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?) {
    val clock = rememberNowState(if (s.reduceEffects) 1_000 else 100, active = m.playing)
    MediaLyrics(m, s, lyrics, clock)
    MediaSeek(m, clock)
}

@Composable
private fun MediaLyrics(m: MediaInfo, s: IslandSettings, lyrics: Lyrics?, clock: State<Long>) {
    val lines = lyrics?.takeIf { it.key == m.key && s.lyricsEnabled && it.lines.isNotEmpty() }
    val status by IslandBus.lyricsStatus.collectAsState()
    if (lines != null) {
        Spacer(Modifier.height(14.dp))
        KaraokeLine(lines, m.positionAt(clock.value), s)
    } else if (s.lyricsEnabled && status?.first == m.key) {
        Spacer(Modifier.height(12.dp))
        Text(if (status?.second == LyricsStatus.LOADING) tr("Ищу текст песни…", "Looking for lyrics…") else tr("Текст песни не найден", "No lyrics found"),
            color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
    }
}

@Composable
private fun MediaSeek(m: MediaInfo, clock: State<Long>) {
    val pos = m.positionAt(rememberNow(1_000, active = m.playing))
    // Время по краям полосы, как на iPhone и в ролике
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(formatDuration(pos), color = Tints.Gray, fontSize = 11.sp, style = Digits.copy(fontWeight = FontWeight.Normal.island),
            maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 34.dp))
        SeekBar(
            fraction = { if (m.durationMs > 0) m.positionAt(clock.value).toFloat() / m.durationMs else 0f },
            color = Color.White,
            enabled = m.canSeek && m.durationMs > 0,
            onSeek = { f -> Controls.seekTo((f * m.durationMs).toLong()) },
            modifier = Modifier.weight(1f),
        )
        Text(
            if (m.durationMs > 0) "-" + formatDuration(m.durationMs - pos) else "",
            color = Tints.Gray, fontSize = 11.sp, style = Digits.copy(fontWeight = FontWeight.Normal.island), textAlign = TextAlign.End,
            maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 38.dp),
        )
    }
}

/**
 * Кнопки плеера сразу под полосой. Отдельно от полосы и времени: те обновляются 10 раз в секунду,
 * а кнопки при этом не перерисовываются.
 */
@Composable
private fun MediaControls(like: PlayerAction?, dislike: PlayerAction?, playing: Boolean) {
    val context = LocalContext.current
    val ratings: @Composable () -> Unit = {
        like?.let { a ->
            RoundButton(if (a.on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, size = 30.dp, bg = Color.Transparent,
                fg = if (a.on) Tints.Red else Dim, iconScale = 0.66f) { Controls.playerAction(a.action) }
        }
        dislike?.let { a ->
            RoundButton(Icons.Rounded.HeartBroken, size = 30.dp, bg = Color.Transparent, fg = Dim, iconScale = 0.62f) { Controls.playerAction(a.action) }
        }
    }
    val output: @Composable () -> Unit = {
        RoundButton(Ios.airplay, size = 36.dp, bg = Color.Transparent, fg = Dim, iconScale = 0.6f) { Controls.openOutput(context) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 260.dp
        val transport: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Ios.back, size = if (narrow) 36.dp else 44.dp, bg = Color.Transparent, iconScale = 0.636f) { Controls.prev() }
                RoundButton(if (playing) Ios.pause else Ios.play, size = if (narrow) 44.dp else 48.dp, bg = Color.Transparent, iconScale = 0.667f) { Controls.playPause() }
                RoundButton(Ios.forward, size = if (narrow) 36.dp else 44.dp, bg = Color.Transparent, iconScale = 0.636f) { Controls.next() }
            }
        }
        if (narrow) Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { transport() }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ratings(); Spacer(Modifier.weight(1f)); output()
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { ratings() }
            transport()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { output() }
        }
    }
}

/** Идущий разговор: аватар, имя, длительность и кнопка «завершить». */
@Composable
private fun CallExpanded(c: CallInfo) {
    val context = LocalContext.current
    val now = rememberWallNow(500)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
            if (c.avatar != null) {
                Image(c.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold.island)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable { Controls.openCall(context, c) }) {
            Text(c.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(formatDuration(now - c.base), color = Tints.Green, fontSize = 15.sp, style = Digits)
        }
        RoundButton(Ios.call, size = 48.dp, bg = Tints.Red, iconScale = 0.46f, iconRotation = 135f) {
            Controls.endCall(context, c)
        }
    }
}

/**
 * Текст песни, как в ролике: одна текущая строка, спетая часть белая, остальное — бледное. Заливка идёт
 * от начала строки до начала следующей; новая строка въезжает снизу.
 */
@Composable
private fun KaraokeLine(lyrics: Lyrics, pos: Long, s: IslandSettings) {
    if (!lyrics.synced) {
        Text(tr("Текст без синхронизации", "Unsynced lyrics"), color = Dim, fontSize = 11.sp)
        Text(lyrics.lines.joinToString("\n") { it.text }, color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp, lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
        return
    }
    val index = lyrics.indexAt(pos)
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                (slideOutVertically(tween(320)) { -it / 2 } + fadeOut(tween(200)))
        },
        label = "lyrics",
    ) { i ->
        val line = lyrics.lines.getOrNull(i)
        val text = line?.text?.ifBlank { null } ?: "• • •"
        val start = line?.timeMs ?: 0L
        val end = lyrics.lines.getOrNull(i + 1)?.timeMs ?: (start + 4_000)
        val sung = if (line == null) 0f else ((pos - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
        // Заливка чуть опережает голос и доходит до конца строки к 85% её времени
        val chars = if (s.reduceEffects) text.length else (text.length * (sung / 0.85f).coerceAtMost(1f)).toInt()
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append(text.take(chars)) }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.32f))) { append(text.drop(chars)) }
            },
            fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SeekBar(
    fraction: () -> Float,
    color: Color,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val dragging by remember { derivedStateOf { drag != null } }
    val thick by animateDpAsState(if (dragging) 9.dp else 5.dp, label = "thick")
    Box(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let(seek)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thick).drawBehind {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = radius)
            val shown = (drag ?: fraction()).coerceIn(0f, 1f)
            if (shown > 0f) drawRoundRect(if (enabled) color else color.copy(alpha = 0.6f),
                size = Size(size.width * shown, size.height), cornerRadius = radius)
        })
    }
}

/** Таймер как на iPhone: слева оранжевая «пауза» и серый крестик, справа «Таймер» и крупное время. */
@Composable
private fun TimerExpanded(t: TimerState, s: IslandSettings) {
    val now = rememberNow(1_000, active = t.pausedLeft == null && !t.ringing)
    if (t.ringing) {
        val wobble = rememberInfiniteTransition(label = "bell")
        val angle = if (s.reduceEffects || !s.fxShake) null
        else wobble.animateFloat(-16f, 16f, infiniteRepeatable(tween(110), RepeatMode.Reverse), label = "a")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Tints.Orange.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Ios.bell, null, tint = Tints.Orange,
                    modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = angle?.value ?: 0f })
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Таймер", "Timer"), color = Tints.Orange, fontSize = 15.sp)
                Text(tr("Время вышло", "Time's up"), color = Tints.Orange, fontSize = 24.sp, fontWeight = FontWeight.SemiBold.island)
            }
            PillButton(Ios.stop, tr("Стоп", "Stop"), bg = Tints.Orange, fg = Color.Black) { Controls.cancelTimer() }
        }
        return
    }
    val left = t.left(now)
    val color = if (left <= 10_000 && !t.paused) Tints.Red else Tints.Orange
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val buttons: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(if (t.paused) Ios.play else Ios.pause, size = 50.dp,
                    bg = color.copy(alpha = 0.25f), fg = color, iconScale = 0.5f) {
                    if (t.paused) Controls.resumeTimer() else Controls.pauseTimer()
                }
                Spacer(Modifier.width(12.dp))
                RoundButton(Ios.close, size = 50.dp, bg = Color.White.copy(alpha = 0.2f), iconScale = 0.44f) { Controls.cancelTimer() }
            }
        }
        if (maxWidth < 260.dp) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (t.paused) tr("Пауза", "Paused") else tr("Таймер", "Timer"), color = color, fontSize = 15.sp)
            FitText(formatDuration(left + 999), color, 40.sp, 24.sp, FontWeight.Normal.island, Modifier.fillMaxWidth())
            buttons()
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            buttons()
            Spacer(Modifier.weight(1f))
            Text(if (t.paused) tr("Пауза", "Paused") else tr("Таймер", "Timer"), color = color, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(left + 999), color = color, fontSize = 44.sp, style = Digits.copy(fontWeight = FontWeight.Normal.island), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun StopwatchExpanded(w: StopwatchState) {
    val now = rememberNow(100, active = w.running)
    val elapsed = w.elapsed(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(if (w.running) Ios.pause else Ios.play, size = 50.dp,
            bg = Tints.Orange.copy(alpha = 0.25f), fg = Tints.Orange, iconScale = 0.5f) { Controls.toggleStopwatch() }
        Spacer(Modifier.width(12.dp))
        if (w.running) {
            RoundButton(Ios.close, size = 50.dp, bg = Color.White.copy(alpha = 0.2f), iconScale = 0.44f) { Controls.stopStopwatch() }
        } else {
            RoundButton(Ios.refresh, size = 50.dp, bg = Color.White.copy(alpha = 0.2f), iconScale = 0.46f) {
                Controls.startStopwatch()
                Controls.toggleStopwatch()
            }
        }
        Spacer(Modifier.weight(1f))
        Text(formatDuration(elapsed) + ",%d".format((elapsed % 1000) / 100), color = Tints.Orange, fontSize = 40.sp,
            style = Digits.copy(fontWeight = FontWeight.Normal.island), maxLines = 1)
    }
}

/**
 * Фонарик — как в ролике: квадратная карточка, посередине жёлтый фонарик в мягком свечении, под ним
 * «Фонарик · 80%». Провести пальцем вверх или вниз — ярче или тусклее (если телефон это умеет): свечение
 * растёт и вспыхивает на каждом шаге. Нажатие — выключить.
 */
@Composable
private fun TorchCard(t: TorchState, s: IslandSettings) {
    val haptic = rememberHaptic()
    val max = t.maxLevel.coerceAtLeast(1)
    var level by remember { mutableFloatStateOf(t.level.toFloat()) }
    LaunchedEffect(t.level) { level = t.level.toFloat() }
    val share = if (t.on) (if (max > 1) level / max else 1f) else 0f
    val intensity by animateFloatAsState(share, spring(dampingRatio = 0.7f, stiffness = 300f), label = "glow")
    // Свечение «дышит», как в ролике
    val breath = rememberInfiniteTransition(label = "breath")
    val wave = if (s.reduceEffects) null else breath.animateFloat(0f, 1f, infiniteRepeatable(tween(1050, easing = LinearEasing)), label = "w")
    val flare = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val label = if (max > 1 && t.on) trf("Фонарик · {0}%", "Flashlight · {0}%", (share * 100).toInt()) else tr("Фонарик", "Flashlight")

    Box(
        Modifier
            .size(200.dp, 200.dp)
            .pointerInput(max) {
                detectVerticalDragGestures(
                    onDragStart = { IslandBus.lastTouch = SystemClock.elapsedRealtime() },
                ) { change, dy ->
                    change.consume()
                    IslandBus.lastTouch = SystemClock.elapsedRealtime()
                    if (max <= 1) return@detectVerticalDragGestures
                    val before = Math.round(level)
                    level = (level - dy / (size.height * 0.6f) * max).coerceIn(1f, max.toFloat())
                    val after = Math.round(level)
                    if (after != before) {
                        haptic()
                        Controls.torchLevel(after)
                        if (!s.reduceEffects) scope.launch {
                            flare.snapTo(1f)
                            flare.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    haptic()
                    Controls.torchOff()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Жёлтое свечение за фонариком: круг 120 dp, ярче с яркостью фонарика и на каждом шаге
            val pulse = wave?.let { 0.85f + 0.15f * sin(it.value * 2 * Math.PI).toFloat() } ?: 1f
            val glow = (0.35f + 0.65f * intensity) * pulse * (1f + 0.4f * flare.value)
            val r = 60.dp.toPx() * (1f + 0.15f * flare.value)
            val c = Offset(size.width / 2, 40.dp.toPx() + 60.dp.toPx())
            drawCircle(
                Brush.radialGradient(
                    0f to Tints.Yellow.copy(alpha = (0.55f * glow).coerceAtMost(1f)),
                    0.55f to Tints.Yellow.copy(alpha = (0.3f * glow).coerceAtMost(1f)),
                    1f to Tints.Yellow.copy(alpha = 0f),
                    center = c, radius = r,
                ),
                radius = r, center = c,
            )
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Ios.flashlight, null, tint = if (t.on) Tints.Yellow else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 20.dp).size(58.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1)
        }
    }
}

/** Таймер или секундомер из Часов, звонок по VoIP и запись экрана или звука — запись красной волной и кнопкой «стоп». */
@Composable
private fun ChronoExpanded(c: ChronoInfo, s: IslandSettings) {
    val context = LocalContext.current
    if (c.isRecording) {
        val now = rememberWallNow(1_000, active = c.pausedMs == null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordingWave(Modifier.weight(1f).height(22.dp), !s.reduceEffects && c.pausedMs == null)
            Spacer(Modifier.width(14.dp))
            Text(formatDuration(c.elapsed(now)), color = Tints.Red, fontSize = 22.sp, style = Digits)
            Spacer(Modifier.width(14.dp))
            // Белое кольцо с красным квадратом — как кнопка «стоп» диктофона на iPhone
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable { if (!Controls.send(context, c.stop)) Controls.openApp(context, c.pkg) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Tints.Red))
            }
        }
        return
    }
    // «Часы» — с кнопками, как свой таймер; другие приложения с отсчётом — просто время, нажатие открывает их
    if (c.key.startsWith(ClockScreenReader.KEY_PREFIX) || c.pause != null || c.resume != null || ClockApps.isClock(context, c.pkg)) {
        ClockChrono(c)
        return
    }
    val now = rememberWallNow(1_000, active = c.pausedMs == null)
    Row(Modifier.clickable { Controls.openApp(context, c.pkg) }, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(c.icon, 46.dp, Ios.timer, Tints.Orange)
        Spacer(Modifier.width(14.dp))
        Text(c.title, color = Tints.Orange, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 12.dp))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(c.elapsed(now)), color = Tints.Orange, fontSize = 40.sp,
            style = Digits.copy(fontWeight = FontWeight.Normal.island), maxLines = 1)
    }
}

/**
 * Таймер и секундомер «Часов» — с теми же кнопками, что свои: пауза или продолжить и сброс.
 * Кнопки нажимают кнопки «Часов» (см. ClockControl); нажатие на время открывает приложение.
 */
@Composable
private fun ClockChrono(c: ChronoInfo) {
    val context = LocalContext.current
    val paused = c.pausedMs != null
    val now = rememberWallNow(if (c.countDown) 250 else 100, active = !paused)
    val value = c.elapsed(now)
    val color = if (c.countDown && !paused && value <= 10_000) Tints.Red else Tints.Orange
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(if (paused) Ios.play else Ios.pause, size = 50.dp, bg = color.copy(alpha = 0.25f), fg = color, iconScale = 0.5f) {
            ClockControl.toggle(context, c)
        }
        Spacer(Modifier.width(12.dp))
        RoundButton(if (c.countDown) Ios.close else Ios.refresh, size = 50.dp, bg = Color.White.copy(alpha = 0.2f),
            iconScale = if (c.countDown) 0.44f else 0.46f) {
            ClockControl.reset(context, c)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { Controls.openApp(context, c.pkg) }, horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(c.icon, 16.dp, Ios.timer, color)
                Spacer(Modifier.width(5.dp))
                Text(if (paused) tr("Пауза", "Paused") else if (c.countDown) tr("Таймер", "Timer") else tr("Секундомер", "Stopwatch"),
                    color = color, fontSize = 14.sp, maxLines = 1)
            }
            val text = if (c.countDown) formatDuration(value + 999) else formatDuration(value) + ",%d".format((value % 1000) / 100)
            Text(text, color = color, fontSize = 40.sp, style = Digits.copy(fontWeight = FontWeight.Normal.island), maxLines = 1, softWrap = false)
        }
    }
}

/** Волна записи: слева идут красные столбики, справа ждёт пунктир — как у диктофона в острове. */
@Composable
private fun RecordingWave(modifier: Modifier, animate: Boolean) {
    val clock = rememberFxClock(animate)
    Canvas(modifier) {
        val step = 4.dp.toPx()
        val n = (size.width / step).toInt()
        val active = (n * 0.55f).toInt()
        for (i in 0 until n) {
            val x = i * step + step / 2
            if (i < active) {
                val k = 0.25f + 0.75f * abs(sin((i * 0.7f + (if (animate) clock.value % 900 / 900f else 0f) * 6.28f).toDouble())).toFloat()
                val h = size.height * k
                drawLine(Tints.Red, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawCircle(Tints.Red.copy(alpha = 0.45f), radius = 1.dp.toPx(), center = Offset(x, size.height / 2))
            }
        }
    }
}

@Composable
private fun ProgressExpanded(p: ProgressInfo) {
    val context = LocalContext.current
    Column(Modifier.clickable { Controls.openApp(context, p.pkg) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(p.icon, 44.dp, Ios.download, Tints.Blue)
            Spacer(Modifier.width(14.dp))
            Text(p.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!p.indeterminate) {
                Spacer(Modifier.width(8.dp))
                Text("${(p.fraction * 100).toInt()}%", color = Tints.Blue, fontSize = 18.sp, style = Digits)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(if (p.indeterminate) 1f else p.fraction).fillMaxHeight().clip(CircleShape)
                .background(if (p.indeterminate) Tints.Blue.copy(alpha = 0.4f) else Tints.Blue))
        }
    }
}

/**
 * Навигатор, такси, доставка — как в ролике: слева круг со стрелкой, справа расстояние крупно, подсказка,
 * время прибытия цветом и маленькая кнопка «Открыть».
 */
@Composable
private fun LiveExpanded(l: LiveInfo) {
    val context = LocalContext.current
    val tint = liveTint(l.kind)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(62.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            LiveGlyph(l, 34.dp, tint)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (l.distance != null) {
                Text(l.distance, color = Color.White, fontSize = 27.sp, style = Digits.copy(fontWeight = FontWeight.Bold.island), maxLines = 1)
            } else {
                Text(l.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold.island, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (l.text.isNotEmpty()) {
                Text(l.text, color = Tints.Gray, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val line = l.eta ?: l.sub.takeIf { it.isNotEmpty() }
            if (line != null) {
                Text(line, color = tint, fontSize = 14.sp, style = Digits, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .clickable {
                        if (!Controls.send(context, l.open)) Controls.openApp(context, l.pkg)
                        Controls.collapse()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ios.navigate, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Открыть", "Open"), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold.island)
            }
        }
    }
}

/**
 * Раскрытый пустой остров: часы, заряд и кнопки, которые выбрали в «Поведении» (по умолчанию таймер,
 * секундомер и фонарик). Кнопок нет — только часы и заряд, остров компактный.
 */
@Composable
private fun IdleExpanded(s: IslandSettings, battery: BatteryInfo) {
    val context = LocalContext.current
    val now = rememberWallNow(1000)
    val time = DateFormat.getTimeFormat(context).format(Date(now))
    // «Пятница, 24 октября» или «Friday, October 24» — на языке острова
    val pattern = when (Lang.code) {
        "en" -> "EEEE, MMMM d"
        "pt" -> "EEEE, d 'de' MMMM"
        else -> "EEEE, d MMMM"
    }
    val date = SimpleDateFormat(pattern, Lang.locale).format(Date(now))
        .replaceFirstChar { it.titlecase(Lang.locale) }
    val alarm by IslandBus.nextAlarm.collectAsState()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clockText(time, 36.sp), color = Color.White, fontSize = 36.sp, style = Digits)
                Text(date, color = Dim, fontSize = 14.sp)
                // Ближайший будильник в пределах суток — под датой, как на экране блокировки iPhone
                alarm?.takeIf { it.triggerAt - now in 1..86_400_000L }?.let { a ->
                    Row(
                        Modifier.padding(top = 3.dp).clickable { openAlarm(context, a) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ios.alarm, null, tint = AlarmTint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(trf("{0} · через {1}", "{0} · in {1}", wakeTime(context, a.triggerAt), durationWords(a.triggerAt - now)),
                            color = AlarmTint, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            if (battery.level >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!s.batteryPercentOnly) BatteryGlyph(battery.level, when {
                        battery.charging -> Tints.Green
                        battery.level <= 15 -> Tints.Red
                        else -> Color.White
                    }, Modifier.size(26.dp, 13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${battery.level}%", color = Color.White, fontSize = 16.sp, style = Digits)
                }
            }
        }
        if (battery.charging) {
            val measurements = listOfNotNull(
                battery.powerWatts?.let { trf("{0} Вт на батарее", "{0} W at battery", String.format(java.util.Locale.ROOT, "%.1f", it)) },
                battery.currentMa?.let { trf("{0} мА", "{0} mA", it.toInt()) },
                battery.remainingMs?.takeIf { it > 0 }?.let { trf("До полного заряда: {0}", "Until full: {0}", formatDuration(it)) },
            )
            if (measurements.isNotEmpty()) Text(measurements.joinToString(" · "), color = Dim, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        IdleExtras(s)
        val actions = s.quickActions
        if (actions.isEmpty()) return@Column
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Таймер от 1 до 99 минут
            if (QuickAction.TIMER in actions) IdleTimerPicker(s)
            // Остальные кнопки — по две в ряд, в порядке из настроек
            actions.filter { it != QuickAction.TIMER }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { QuickButton(it, context, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Narrow cards put the start button below the stepper instead of squeezing its text away. */
@Composable
internal fun IdleTimerPicker(s: IslandSettings) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 280.dp
        val stepper: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Ios.remove, size = 38.dp, iconScale = 0.44f) {
                    SettingsRepo.update { copy(timerMinutes = (timerMinutes - 1).coerceAtLeast(1)) }
                }
                Text(trf("{0} мин", "{0} min", s.timerMinutes), color = Color.White, fontSize = 16.sp, style = Digits,
                    maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                RoundButton(Ios.add, size = 38.dp, iconScale = 0.44f) {
                    SettingsRepo.update { copy(timerMinutes = (timerMinutes + 1).coerceAtMost(99)) }
                }
            }
        }
        val start: @Composable (Modifier) -> Unit = { modifier ->
            PillButton(Ios.play, tr("Таймер", "Timer"), modifier = modifier, bg = Tints.Orange, fg = Color.Black) {
                Controls.startTimer(s.timerMinutes)
                Controls.expand("timer")
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stepper(Modifier.fillMaxWidth())
            start(Modifier.fillMaxWidth())
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stepper(Modifier.weight(1.4f))
            start(Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickButton(a: QuickAction, context: Context, modifier: Modifier) {
    val ringer by IslandBus.ringer.collectAsState()
    val dnd by IslandBus.dnd.collectAsState()
    val icon = when (a) {
        QuickAction.STOPWATCH -> Ios.stopwatch
        QuickAction.FLASHLIGHT -> Ios.flashlight
        QuickAction.SCREENSHOT -> Icons.Rounded.Screenshot
        QuickAction.CAMERA -> Icons.Rounded.PhotoCamera
        QuickAction.LOCK -> Ios.lock
        QuickAction.TIMER -> Ios.timer
        QuickAction.RINGER -> when (ringer) {
            AudioManager.RINGER_MODE_SILENT -> Ios.bellOff
            AudioManager.RINGER_MODE_VIBRATE -> Ios.vibrate
            else -> Ios.bell
        }
        QuickAction.DND -> Ios.moon
    }
    // У «Режима звука» подпись — сам режим; «Не беспокоить» горит, пока включено
    val label = when (a) {
        QuickAction.RINGER -> when (ringer) {
            AudioManager.RINGER_MODE_SILENT -> tr("Без звука", "Silent")
            AudioManager.RINGER_MODE_VIBRATE -> tr("Вибрация", "Vibrate")
            else -> tr("Звонок", "Ring")
        }
        else -> a.short
    }
    val on = a == QuickAction.DND && dnd
    PillButton(icon, label, modifier, bg = if (on) Tints.Purple else Color.White.copy(alpha = 0.14f)) {
        when (a) {
            QuickAction.STOPWATCH -> {
                Controls.startStopwatch()
                Controls.expand("stopwatch")
            }
            QuickAction.FLASHLIGHT -> {
                Controls.torchOn()
                Controls.collapse()
            }
            QuickAction.SCREENSHOT -> Controls.screenshot()
            QuickAction.CAMERA -> {
                Controls.collapse()
                Controls.openCamera(context)
            }
            QuickAction.LOCK -> {
                Controls.collapse()
                Controls.lockScreen()
            }
            QuickAction.TIMER -> Controls.startTimer(SettingsRepo.current.timerMinutes)
            QuickAction.RINGER -> Controls.cycleRinger(context)
            QuickAction.DND -> Controls.toggleDnd(context)
        }
    }
}


/** Disc rotation uses the shared screen-aware effects clock and stops on pause/reduced effects. */
@Composable
private fun PlayerArt(m: MediaInfo, s: IslandSettings, modifier: Modifier = Modifier) {
    val rotating = s.vinylArt && m.playing && !s.reduceEffects
    val clock = rememberFxClock(rotating)
    Box(modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Art(m.art, 60.dp, if (s.vinylArt) 30.dp else 14.4.dp,
            Modifier.graphicsLayer { if (s.vinylArt) rotationZ = clock.value % 20_000L / 20_000f * 360f })
        if (s.vinylArt) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Black))
    }
}

private fun Modifier.marqueeFade(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.04f to Color.White,
            0.96f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
