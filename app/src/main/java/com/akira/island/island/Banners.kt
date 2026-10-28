package com.akira.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.R
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.update.DownloadState
import com.akira.island.update.UpdateChecker
import com.akira.island.update.localNotes
import kotlinx.coroutines.launch

/** Чем писать на сиреневых кнопках — тёмным, как в ролике. */
private val Ink = Color(0xFF1C1B22)

/**
 * Ответ ушёл — как в ролике: сиреневый кружок с галочкой и «Отправлено», справа — кому.
 * Показывается коротко (половина «Показа событий») и сразу уступает место.
 */
@Composable
fun SentBanner(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.4f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.32f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(height * 0.42f).scale(pop.value).clip(CircleShape).background(n.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ios_check), null, tint = onAccent(n.tint), modifier = Modifier.size(height * 0.26f))
            }
            Spacer(Modifier.width(10.dp))
            Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1)
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (n.state != null) {
                Text(n.state, color = Tints.Gray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Новая версия — карточка из ролика: значок приложения, «Доступна версия», что нового и две кнопки.
 * «Обновить» сразу качает APK — на кнопке проценты, рядом кольцо, — и открывает установку; пока качается,
 * карточка не уходит. «Позже» больше не напоминает об этой версии. После установки остров один раз
 * показывает ту же карточку с галочкой: «Обновлено», «Что нового» и «Готово».
 */
@Composable
fun UpdateCard(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val context = LocalContext.current
    val release = n.update
    val download by UpdateChecker.download.collectAsState()
    val progress = (download as? DownloadState.Running)?.progress?.takeIf { release != null }
    // Скачалось, но установка не открылась (нужно разрешение) — «Установить» ставит уже скачанный файл
    val ready = release?.let { UpdateChecker.readyFile(it, download) }
    val failed = (download as? DownloadState.Failed)?.message?.takeIf { release != null }
    val loading = progress != null
    // Пока качается, плашка не уходит по таймеру
    DisposableEffect(loading) {
        if (loading) IslandBus.noticeHold.value = true
        onDispose { if (loading) IslandBus.noticeHold.value = false }
    }
    Column(Modifier.width(wideWidth).padding(start = 22.dp, end = 22.dp, top = cameraInset(s) + 8.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(n, 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold.island, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                // Пока качается, справа кольцо — вместо длинной подписи коротко, что происходит
                val caption = if (loading) tr("Скачивается…", "Downloading…") else n.caption
                if (failed != null) {
                    // Ошибку — целиком, в две строки: по ней понятно, что делать
                    Text(failed, color = Tints.Orange, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else if (caption != null) {
                    // Длинная подпись чуть уменьшается, а не обрезается
                    FitText(caption, Tints.Gray, 13.sp, 11.sp, FontWeight.Normal.island)
                }
            }
            when {
                progress != null -> {
                    Spacer(Modifier.width(10.dp))
                    ProgressRing(progress, Tints.Lavender, Modifier.size(30.dp), stroke = 3.5.dp)
                }
                release == null -> {
                    Spacer(Modifier.width(10.dp))
                    CheckBadge(Tints.Green, !s.reduceEffects, Modifier.size(30.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardButton(if (release == null) tr("Что нового", "What's new") else tr("Позже", "Later"),
                Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.75f)) {
                when {
                    release == null -> Controls.openApp(context, context.packageName)
                    // Пример из «Проверки» не должен скрыть настоящую версию
                    release.apkUrl != UpdateChecker.DEMO_APK -> UpdateChecker.skip(release.version)
                }
                IslandBus.notice.value = null
            }
            CardButton(
                when {
                    release == null -> tr("Готово", "Done")
                    progress != null -> "${(progress * 100).toInt()}%"
                    ready != null -> tr("Установить", "Install")
                    else -> tr("Обновить", "Update")
                },
                Tints.Lavender, Ink, bold = true,
                icon = if (release != null && progress == null) Ios.download else null,
            ) {
                when {
                    release == null -> IslandBus.notice.value = null
                    ready != null -> UpdateChecker.install(context.applicationContext, ready)
                    progress == null -> {
                        val app = context.applicationContext
                        IslandBus.scope.launch { UpdateChecker.downloadAndInstall(app, release) }
                    }
                }
            }
        }
    }
}

/** Значок приложения в карточке обновления; если его не достать — тёмный квадрат с капсулой, как в ролике. */
@Composable
private fun AppMark(n: Notice, size: Dp) {
    if (n.appIcon != null) {
        Image(n.appIcon, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)))
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.linearGradient(listOf(Color(0xFF2B2350), Color(0xFF0E0D16)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.56f, size * 0.2f)
                .clip(CircleShape)
                .background(Color.Black)
                .border(size * 0.025f, Tints.Lavender, CircleShape),
        )
    }
}

/** Кнопка карточки: высокая капсула во всю половину строки, чуть проседает под пальцем. */
@Composable
private fun RowScope.CardButton(
    text: String,
    bg: Color,
    fg: Color,
    bold: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Row(
        Modifier
            .weight(1f)
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .clickable(interaction, indication = null) {
                haptic()
                onClick()
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, maxLines = 1,
            style = Digits.copy(fontWeight = if (bold) FontWeight.Bold.island else FontWeight.SemiBold.island))
    }
}

/** Что нового в релизе одной строкой — заголовки разделов из описания: «Плашка уведомления, Ответ из острова, Будильник». */
fun releaseHighlights(notes: String): String? {
    // В описании два языка — берём заголовки на языке острова
    val heads = localNotes(notes)
        .map { it.trim() }
        .filter { it.startsWith("### ") }
        .map { h -> h.removePrefix("### ").dropWhile { !it.isLetterOrDigit() }.substringBefore(" — ").trim() }
        .filter { it.isNotEmpty() }
    return heads.take(3).joinToString(", ").ifEmpty { null }
}
