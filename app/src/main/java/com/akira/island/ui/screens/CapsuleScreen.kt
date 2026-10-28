package com.akira.island.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.WidthNormal
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.akira.island.data.IslandFont
import com.akira.island.island.Digits
import com.akira.island.island.IslandTheme
import com.akira.island.island.IslandType
import com.akira.island.island.island
import com.akira.island.ui.posOf
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.Elasticity
import com.akira.island.data.ExpandStyle
import com.akira.island.data.FxRate
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

/**
 * «Оформление»: где стоит остров и как он выглядит — положение и размер, цвет и стекло, анимации и эффекты.
 * Что остров показывает и как себя ведёт — во вкладке «Функции».
 */
@Composable
fun CapsuleScreen(open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Оформление", "Appearance")) {
        IslandPreview()

        SectionHeader(tr("Положение и размер", "Position and size"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.OpenWith, tr("Положение и размер", "Position and size"),
                trf("Капсула {0}×{1} dp — подогнать под камеру", "Capsule {0}×{1} dp — fit it to the camera", s.width, s.height),
                color = Tile.Indigo, onClick = { open(Screen.SIZE) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.SwapHoriz, tr("Размеры", "Sizes"),
                trf(
                    "С активностью, раскрытый остров и уведомления: ширина {0}%, текст {1}%",
                    "With activity, expanded island and notifications: width {0}%, text {1}%", s.expandedWidthPercent, s.contentScale,
                ),
                color = Tile.Purple, onClick = { open(Screen.LOOK) }) { Arrow() }
        }

        SectionHeader(tr("Цвет и стекло", "Color and glass"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.FormatColorFill, tr("Цвет капсулы", "Capsule color"), tr("Цвет, контур и тень", "Color, outline and shadow"),
                color = Tile.Orange, onClick = { open(Screen.COLORS) }) { Arrow() }
            // У стекла своя плотность — ниже, у Liquid Glass
            if (!s.liquidGlass) {
                GroupSlider(
                    Pos.Middle, Icons.Rounded.Opacity, tr("Непрозрачность", "Opacity"),
                    value = s.islandOpacity.toFloat(), range = 20f..100f, label = "${s.islandOpacity}%", color = Tile.Gray,
                ) { v -> SettingsRepo.update { copy(islandOpacity = (v / 5).toInt() * 5) } }
            }
            GroupToggle(
                if (s.liquidGlass) Pos.Middle else Pos.Last, Icons.Rounded.WaterDrop, "Liquid Glass",
                tr("Стекло с бликами и сияющей кромкой, как в iOS. Блики двигаются при наклоне",
                    "Glass with highlights and a glowing edge, like in iOS. Highlights move when you tilt the phone"),
                s.liquidGlass, color = Tile.Teal, badge = tr("Экспериментальный", "Experimental"),
            ) { v -> SettingsRepo.update { copy(liquidGlass = v) } }
            if (s.liquidGlass) {
                GroupSlider(
                    Pos.Last, Icons.Rounded.Opacity, tr("Плотность стекла", "Glass density"),
                    value = s.glassOpacity.toFloat(), range = 40f..95f, label = "${s.glassOpacity}%", color = Tile.Teal,
                ) { v -> SettingsRepo.update { copy(glassOpacity = v.toInt()) } }
            }
        }

        SectionHeader(tr("Шрифт", "Font"))
        GroupItem(Pos.Single, Icons.Rounded.FontDownload, tr("Шрифт острова", "Island font"),
            "${fontTitle(s)} · ${weightTitle(s.fontWeightShift)}", color = Tile.Pink, onClick = { open(Screen.FONT) }) { Arrow() }

        SectionHeader(tr("Анимации", "Animations"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, tr("Раскрытие острова", "Island expansion"),
                "${s.expandStyle.title} · ${s.durationPercent}%",
                color = Tile.Blue, onClick = { open(Screen.ANIM) }) { Arrow() }
            // Новое раскрытие — отдельным переключателем: выключено — прежнее перетекание
            GroupToggle(Pos.Middle, Icons.Rounded.BubbleChart, tr("Жидкое раскрытие", "Liquid expansion"),
                tr("Содержимое растёт вместе с островом и проявляется из размытия",
                    "The content grows together with the island and emerges from a blur"),
                s.liquidExpand, color = Tile.Indigo, badge = tr("Новое", "New"),
            ) { v -> SettingsRepo.update { copy(liquidExpand = v) } }
            // Упругость — на виду: «слишком прыгает» правится одним нажатием
            ElasticityItem(Pos.Middle, s, Tile.Teal)
            GroupItem(Pos.Last, Icons.Rounded.AutoAwesome, tr("Эффекты", "Effects"), effectsSummary(s),
                color = Tile.Purple, onClick = { open(Screen.EFFECTS) }) { Arrow() }
        }
    }
}

/** Пункт «Упругость» с выбором — прямо в «Оформлении», рядом с «Раскрытием острова». */
@Composable
private fun ElasticityItem(pos: Pos, s: IslandSettings, color: Color) {
    var dialog by remember { mutableStateOf(false) }
    GroupItem(pos, Icons.Rounded.Waves, tr("Упругость", "Elasticity"), s.elasticity.title, color = color, onClick = { dialog = true })
    if (dialog) {
        ChoiceDialog(tr("Упругость анимации", "Animation elasticity"), Elasticity.entries, s.elasticity, { it.title },
            { v -> SettingsRepo.update { copy(elasticity = v) } }) { dialog = false }
    }
}

/** Пока двигают ползунок размера, на острове пример активности — сразу видно, какой она будет. */
private fun previewActivity() {
    IslandBus.widthPreviewUntil.value = SystemClock.elapsedRealtime() + 2_500
}

/** «Размер с активностью»: насколько капсула с навигатором, фонариком или музыкой толще и шире спокойной. */
@Composable
fun LookScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Размеры", "Sizes"), onBack) {
        IslandPreview()
        SectionHeader(tr("С активностью", "With activity"))
        Group {
            GroupSlider(
                Pos.First, Icons.Rounded.LineWeight, tr("Толщина", "Thickness"),
                value = s.activeExtra.toFloat(), range = 0f..14f, label = "+${s.activeExtra} dp", steps = 13, color = Tile.Purple,
            ) { v ->
                previewActivity()
                SettingsRepo.update { copy(activeExtra = v.toInt()) }
            }
            GroupSlider(
                Pos.Last, Icons.Rounded.SwapHoriz, tr("Ширина", "Width"),
                value = s.activeWidth.toFloat(), range = 0f..200f, steps = 39,
                label = if (s.activeWidth == 0) tr("Как без неё", "As without it") else "+${s.activeWidth} dp", color = Tile.Blue,
            ) { v ->
                // Сразу видно, не налезает ли капсула на значки строки состояния
                previewActivity()
                SettingsRepo.update { copy(activeWidth = (v / 5).toInt() * 5) }
            }
        }

        // Уведомления занимают слишком много места — делаем их уже и мельче, и наоборот
        SectionHeader(tr("Раскрытый остров и уведомления", "Expanded island and notifications"))
        Group {
            GroupSlider(
                Pos.First, Icons.Rounded.WidthNormal, tr("Ширина", "Width"),
                value = s.expandedWidthPercent.toFloat(), range = 60f..100f, steps = 7,
                label = "${s.expandedWidthPercent}%", color = Tile.Indigo,
            ) { v -> SettingsRepo.update { copy(expandedWidthPercent = (v / 5).toInt() * 5) } }
            GroupSlider(
                Pos.Last, Icons.Rounded.TextIncrease, tr("Размер текста и значков", "Text and icon size"),
                value = s.contentScale.toFloat(), range = 70f..130f, steps = 11,
                label = "${s.contentScale}%", color = Tile.Pink,
            ) { v -> SettingsRepo.update { copy(contentScale = (v / 5).toInt() * 5) } }
        }
        Text(
            tr("Проверьте на примерах: «Главная → Проверка».", "See it on the examples: Home → Try it."),
            color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** «Раскрытие острова»: как остров раскрывается, насколько пружинит и сколько видно события. */
@Composable
fun AnimScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var styleDialog by remember { mutableStateOf(false) }

    Page(tr("Раскрытие острова", "Island expansion"), onBack, action = {
        Text(tr("Сброс", "Reset"), color = Ui.Sub, fontSize = 16.sp, modifier = Modifier.clickable { SettingsRepo.resetAnimations() })
    }) {
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, tr("Стиль раскрытия", "Expansion style"), s.expandStyle.title, color = Tile.Purple,
                onClick = { styleDialog = true })
            GroupSlider(
                Pos.Middle, Icons.Rounded.Speed, tr("Длительность", "Duration"),
                value = s.durationPercent.toFloat(), range = 0f..100f, label = "${s.durationPercent}%", color = Tile.Indigo,
            ) { v -> SettingsRepo.update { copy(durationPercent = v.toInt()) } }
            // Сколько видно событие; плашки, которым нужно больше времени (сообщение с кнопками, оплата), — от него же
            GroupSlider(
                Pos.Middle, Icons.Rounded.Schedule, tr("Показ событий", "Event display"),
                value = s.noticeDurationMs / 1000f, range = 1.5f..10f, label = tr("%.1f с", "%.1f s").format(s.noticeDurationMs / 1000f), color = Tile.Orange,
            ) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 2).roundToInt() * 500) } }
            // Остров показывает событие не сразу, а через столько секунд
            GroupSlider(
                Pos.Middle, Icons.Rounded.HourglassTop, tr("Задержка появления", "Delay before showing"),
                value = s.noticeDelayMs / 1000f, range = 0f..3f,
                label = if (s.noticeDelayMs == 0) tr("Сразу", "Right away") else tr("%.1f с", "%.1f s").format(s.noticeDelayMs / 1000f),
                color = Tile.Yellow,
            ) { v -> SettingsRepo.update { copy(noticeDelayMs = (v * 10).roundToInt() * 100) } }
            GroupToggle(Pos.Last, Icons.Rounded.BlurOn, tr("Уменьшить эффекты", "Reduce effects"),
                tr("Может быть полезно для не самых производительных устройств", "Can help on less powerful devices"),
                s.reduceEffects, color = Tile.Gray) { v -> SettingsRepo.update { copy(reduceEffects = v) } }
        }
    }

    if (styleDialog) {
        ChoiceDialog(tr("Стиль раскрытия", "Expansion style"), ExpandStyle.entries, s.expandStyle, { it.title },
            { v -> SettingsRepo.update { copy(expandStyle = v) } }) { styleDialog = false }
    }
}

/** Что включено в «Эффектах» — подпись пункта. */
private fun effectsSummary(s: IslandSettings): String {
    if (s.reduceEffects) return tr("Выключены: включено «Уменьшить эффекты»", "Off: Reduce effects is on")
    val on = listOfNotNull(
        tr("размытие", "blur").takeIf { s.fxBlur },
        tr("эквалайзер", "equalizer").takeIf { s.fxEqualizer },
        tr("свечение", "glow").takeIf { s.fxGlow },
        tr("волны оплаты", "payment waves").takeIf { s.fxPay },
        tr("вздрагивание", "shake").takeIf { s.fxShake },
    )
    return if (on.isEmpty()) tr("Все выключены", "All off") else on.joinToString(", ").replaceFirstChar { it.uppercase() }
}

/** «Эффекты»: каждый живой эффект острова включается и выключается отдельно. */
@Composable
fun EffectsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var rateDialog by remember { mutableStateOf(false) }
    Page(tr("Эффекты", "Effects"), onBack, action = {
        Text(tr("Сброс", "Reset"), color = Ui.Sub, fontSize = 16.sp, modifier = Modifier.clickable { SettingsRepo.resetEffects() })
    }) {
        IslandPreview()
        if (s.reduceEffects) {
            Text(
                tr("Сейчас включено «Уменьшить эффекты» в «Раскрытии острова» — пока оно включено, эффектов нет",
                    "Reduce effects is on in Island expansion — while it's on, there are no effects"),
                color = Tints.Orange, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        SectionHeader(tr("Движение", "Motion"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.BlurCircular, tr("Размытие при смене", "Blur on change"),
                tr("Содержимое острова перетекает с размытием, как на iPhone (Android 12+)",
                    "The island's content flows with a blur, like on iPhone (Android 12+)"),
                s.fxBlur, color = Tile.Blue) { v -> SettingsRepo.update { copy(fxBlur = v) } }
            GroupToggle(Pos.Middle, Icons.Rounded.GraphicEq, tr("Живой эквалайзер", "Live equalizer"),
                tr("Полоски у музыки и звонка пляшут. Выключено — стоят ровно, так экономнее",
                    "The music and call bars dance. Off — they stand still, which saves power"),
                s.fxEqualizer, color = Tile.Pink) { v -> SettingsRepo.update { copy(fxEqualizer = v) } }
            // Раскрытие и смена содержимого всегда идут с частотой экрана — это только для того, что движется постоянно
            GroupItem(Pos.Middle, Icons.Rounded.Speed, tr("Частота эквалайзера", "Equalizer frame rate"),
                trf(
                    "{0}. Ещё светящаяся точка фонарика и навигатора",
                    "{0}. Also the glowing dot of the flashlight and navigation", s.fxRate.title,
                ),
                color = Tile.Indigo, onClick = { rateDialog = true })
            GroupToggle(Pos.Last, Icons.Rounded.NotificationsActive, tr("Вздрагивание", "Shake"),
                tr("Капсула вздрагивает при смене режима звука, колокольчик звонящего таймера качается",
                    "The capsule shakes when the ring mode changes, and a ringing timer's bell swings"),
                s.fxShake, color = Tile.Orange) { v -> SettingsRepo.update { copy(fxShake = v) } }
        }

        SectionHeader(tr("Свечение", "Glow"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Flare, tr("Свечение обложки", "Artwork glow"),
                tr("Мягкий ореол цвета обложки вокруг острова, пока играет музыка", "A soft halo in the artwork's color around the island while music plays"),
                s.fxGlow, color = Tile.Purple) { v -> SettingsRepo.update { copy(fxGlow = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.Contactless, tr("Волны оплаты", "Payment waves"),
                tr("Волны NFC вокруг острова, пока ждём терминал, и вспышка с искрами на «Оплачено»",
                    "NFC waves around the island while waiting for the reader, and a burst of sparks on Paid"),
                s.fxPay, color = Tile.Teal) { v -> SettingsRepo.update { copy(fxPay = v) } }
        }

        SectionHeader(tr("Батарея", "Battery"))
        GroupToggle(Pos.Single, Icons.Rounded.BatterySaver, tr("Экономия заряда", "Battery saving"),
            tr("В режиме энергосбережения Android остров сам обходится без живых эффектов",
                "In Android's battery saver mode the island skips live effects by itself"),
            s.batterySaver, color = Tile.Green) { v -> SettingsRepo.update { copy(batterySaver = v) } }
    }

    if (rateDialog) {
        ChoiceDialog(tr("Частота эквалайзера", "Equalizer frame rate"), FxRate.entries, s.fxRate, { it.title },
            { v -> SettingsRepo.update { copy(fxRate = v) } }) { rateDialog = false }
    }
}

/** Название шрифта острова для подписи: у своего — имя файла. */
private fun fontTitle(s: IslandSettings): String =
    if (s.islandFont == IslandFont.CUSTOM && s.customFontName.isNotEmpty()) s.customFontName else s.islandFont.title

/** Толщина текста острова: −1 тоньше, 0 обычная, 1 и 2 — жирнее. */
private fun weightTitle(shift: Int): String = when (shift) {
    -1 -> tr("Тоньше", "Lighter")
    1 -> tr("Жирнее", "Bolder")
    2 -> tr("Самая жирная", "Boldest")
    else -> tr("Обычная толщина", "Regular weight")
}

/**
 * «Шрифт острова»: Inter (как на iPhone), шрифт телефона или свой файл .ttf/.otf, и толщина текста.
 * Свой шрифт копируется в личную папку приложения; если файл не читается как шрифт, остаётся прежний.
 */
@Composable
fun FontScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    LaunchedEffect(s) { IslandType.apply(context, s) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { copyFont(context, uri) }
            if (picked == null) {
                Toast.makeText(context, tr("Этот файл не читается как шрифт", "This file can't be read as a font"), Toast.LENGTH_LONG).show()
            } else {
                val old = SettingsRepo.current.customFontFile
                SettingsRepo.update { copy(islandFont = IslandFont.CUSTOM, customFontName = picked.first, customFontFile = picked.second) }
                // Прежний свой шрифт больше не нужен
                if (old.isNotEmpty() && old != picked.second) withContext(Dispatchers.IO) { File(IslandType.fontsDir(context), old).delete() }
            }
        }
    }

    Page(tr("Шрифт острова", "Island font"), onBack) {
        FontSample()
        SectionHeader(tr("Шрифт", "Font"))
        Group {
            IslandFont.entries.forEachIndexed { i, f ->
                val chosen = s.islandFont == f
                GroupItem(
                    posOf(i, IslandFont.entries.size), Icons.Rounded.FontDownload,
                    if (f == IslandFont.CUSTOM && s.customFontName.isNotEmpty()) "${f.title}: ${s.customFontName}" else f.title,
                    when (f) {
                        IslandFont.INTER -> tr("Близок к шрифту iPhone", "Close to the iPhone font")
                        IslandFont.SYSTEM -> tr("Тот, что выбран в настройках телефона", "The one chosen in your phone's settings")
                        IslandFont.CUSTOM -> tr("Файл .ttf или .otf с телефона", "A .ttf or .otf file from your phone")
                    },
                    color = if (chosen) Tile.Pink else Tile.Gray,
                    onClick = {
                        when {
                            f != IslandFont.CUSTOM -> SettingsRepo.update { copy(islandFont = f) }
                            // Свой шрифт уже выбирали — нажатие ещё раз даёт выбрать другой файл
                            s.customFontFile.isEmpty() || chosen -> picker.launch(arrayOf("font/*", "application/*"))
                            else -> SettingsRepo.update { copy(islandFont = f) }
                        }
                    },
                ) { if (chosen) Icon(Icons.Rounded.Check, null, tint = Ui.Primary) }
            }
        }
        SectionHeader(tr("Толщина", "Weight"))
        Group {
            listOf(-1, 0, 1, 2).forEachIndexed { i, w ->
                GroupItem(posOf(i, 4), Icons.Rounded.FormatBold, weightTitle(w),
                    color = if (s.fontWeightShift == w) Tile.Indigo else Tile.Gray,
                    onClick = { SettingsRepo.update { copy(fontWeightShift = w) } },
                ) { if (s.fontWeightShift == w) Icon(Icons.Rounded.Check, null, tint = Ui.Primary) }
            }
        }
    }
}

/** Пример текста острова выбранным шрифтом: время, имя и сообщение. */
@Composable
private fun FontSample() {
    IslandTheme {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black).padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text("12:31", color = Color.White, fontSize = 34.sp, style = Digits)
            Spacer(Modifier.height(6.dp))
            Text(tr("Анна", "Anna"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold.island)
            Text(tr("Привет! Во сколько встречаемся?", "Hi! What time are we meeting?"), color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
        }
    }
}

/**
 * Копия выбранного файла шрифта в личной папке: (название для настроек, имя файла) или null, если это не шрифт.
 * Больше 30 МБ не копируем — шрифтов такого размера не бывает.
 */
private fun copyFont(context: Context, uri: Uri): Pair<String, String>? {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.take(40)?.ifBlank { null } ?: tr("Свой шрифт", "Custom font")
    val dir = IslandType.fontsDir(context).apply { mkdirs() }
    val file = File(dir, "font_${System.currentTimeMillis()}")
    val ok = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 30L * 1024 * 1024) return@runCatching false
                    out.write(buf, 0, n)
                }
            }
            true
        } ?: false
    }.getOrDefault(false)
    if (!ok || !IslandType.isFont(file)) {
        file.delete()
        return null
    }
    return name to file.name
}
