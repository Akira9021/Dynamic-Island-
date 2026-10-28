package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Lang
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.AccentMode
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.ViewAgenda
import com.akira.island.data.BubbleSide
import com.akira.island.data.LandscapeMode
import com.akira.island.data.LockScreenMode
import com.akira.island.data.MessageStyle
import com.akira.island.data.AppLanguage
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.island.FitText
import com.akira.island.island.onAccent
import com.akira.island.island.resolveAccent
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
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.akira.island.data.QuickAction

/**
 * «Функции»: что остров показывает и как с ним обращаться. Внешний вид — во вкладке «Оформление»,
 * версия, помощь и ссылки — в «О приложении».
 */
@Composable
fun FeaturesScreen(open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }

    Page(tr("Функции", "Features")) {
        SectionHeader(tr("Что показывает остров", "What the island shows"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Dashboard, tr("Динамические карточки", "Dynamic cards"),
                tr("Музыка, звонки, таймеры, фонарик, навигация", "Music, calls, timers, flashlight, navigation"),
                color = Tile.Purple, onClick = { open(Screen.CARDS) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.ChatBubble, tr("Уведомления", "Notifications"),
                tr("Сообщения приложений с ответом прямо в острове", "App messages with replies right in the island"),
                color = Tile.Blue, onClick = { open(Screen.NOTIFICATIONS) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.NotificationsActive, tr("События", "Events"),
                tr("Зарядка, Wi-Fi, наушники, оплата, будильник", "Charging, Wi-Fi, headphones, payments, alarm"),
                color = Tile.Orange, onClick = { open(Screen.EVENTS) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.LibraryMusic, tr("Текст песен", "Lyrics"),
                tr("Синхронный текст в раскрытом плеере", "Synced lyrics in the expanded player"),
                color = Tile.Pink, onClick = { open(Screen.LYRICS) }) { Arrow() }
        }

        SectionHeader(tr("Управление", "Controls"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Gesture, tr("Жесты", "Gestures"), tr("Нажатия и свайпы по капсуле", "Taps and swipes on the capsule"),
                color = Tile.Teal, onClick = { open(Screen.GESTURES) }) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Tune, tr("Поведение", "Behavior"),
                tr("Когда показывать и сворачивать остров, виброотклик", "When to show and collapse the island, haptics"),
                color = Tile.Indigo, onClick = { open(Screen.BEHAVIOR) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.NotificationsOff, tr("Системные плашки", "System banners"),
                tr("Чтобы звонок, таймер и батарею показывал только остров", "So only the island shows calls, timers and battery"),
                color = Tile.Red, onClick = { open(Screen.BANNERS) }) { Arrow() }
        }

        SectionHeader(tr("Общие", "General"))
        Group {
            // Значок глобуса и «Language» рядом — язык найдётся, даже если сейчас он непонятен
            GroupItem(Pos.First, Icons.Rounded.Language, languageTitle(), s.language.title,
                color = Tile.Blue, onClick = { languageDialog = true }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Restore, tr("Сбросить все настройки", "Reset all settings"),
                tr("Вернуть всё как было после установки", "Back to how it was after installing"),
                onClick = { confirmReset = true }, color = Tile.Gray)
        }
    }

    if (languageDialog) {
        ChoiceDialog(languageTitle(), AppLanguage.entries, s.language, { it.title },
            { v -> SettingsRepo.update { copy(language = v) } }) { languageDialog = false }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Ui.Card,
            title = { Text(tr("Сбросить настройки?", "Reset settings?")) },
            text = {
                Text(tr("Размер, цвета, анимации, карточки и события вернутся к значениям по умолчанию.",
                    "Size, colors, animations, cards and events go back to their defaults."))
            },
            confirmButton = {
                TextButton(onClick = {
                    SettingsRepo.reset()
                    confirmReset = false
                }) { Text(tr("Сбросить", "Reset"), color = Ui.Primary) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Отмена", "Cancel"), color = Ui.Sub) } },
        )
    }
}

/** «Поведение»: когда остров виден, как сворачивается и чем отзывается. */
@Composable
fun BehaviorScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var collapseDialog by remember { mutableStateOf(false) }
    var landscapeDialog by remember { mutableStateOf(false) }
    var lockDialog by remember { mutableStateOf(false) }
    var sideDialog by remember { mutableStateOf(false) }

    Page(tr("Поведение", "Behavior"), onBack) {
        SectionHeader(tr("Когда показывать", "When to show"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.VisibilityOff, tr("Скрывать пустую капсулу", "Hide the empty capsule"),
                tr("Показывать капсулу, только когда в ней что-то есть", "Show the capsule only when it has something in it"),
                s.hideIdle, color = Tile.Gray) { v -> SettingsRepo.update { copy(hideIdle = v) } }
            // В играх и видео: спрятать, оставить над камерой (как на iPhone) или посередине сверху
            GroupItem(Pos.Middle, Icons.Rounded.ScreenRotation, tr("В альбомной ориентации", "In landscape"),
                "${s.landscape.title} — ${s.landscape.hint}", color = Tile.Gray, onClick = { landscapeDialog = true })
            GroupItem(Pos.Middle, Icons.Rounded.ScreenLockPortrait, tr("На экране блокировки", "On the lock screen"),
                s.lockScreen.title, color = Tile.Indigo, onClick = { lockDialog = true })
            GroupToggle(Pos.Last, Icons.Rounded.Apps, tr("Прятать плеер в его приложении", "Hide the player in its own app"),
                tr("Карточка музыки не показывается, пока открыт сам плеер", "The music card hides while the player itself is open"),
                s.hideWhenPlayerOpen, color = Tile.Blue) { v -> SettingsRepo.update { copy(hideWhenPlayerOpen = v) } }
        }

        // Например, только днём: с 10:00 до 23:00; можно и через полночь — с 22:00 до 7:00
        SectionHeader(tr("Расписание", "Schedule"))
        Group {
            GroupToggle(if (s.scheduleOn) Pos.First else Pos.Single, Icons.Rounded.Schedule, tr("По расписанию", "On a schedule"),
                trf(
                    "Остров виден только с {0} до {1}",
                    "The island shows only from {0} to {1}", hourLabel(s.scheduleFrom), hourLabel(s.scheduleTo),
                ),
                s.scheduleOn, color = Tile.Indigo) { v -> SettingsRepo.update { copy(scheduleOn = v) } }
            if (s.scheduleOn) {
                GroupSlider(Pos.Middle, Icons.Rounded.WbSunny, tr("С", "From"), s.scheduleFrom.toFloat(), 0f..23f,
                    hourLabel(s.scheduleFrom), steps = 22, color = Tile.Orange) { v -> SettingsRepo.update { copy(scheduleFrom = v.toInt()) } }
                GroupSlider(Pos.Last, Icons.Rounded.Bedtime, tr("До", "To"), s.scheduleTo.toFloat(), 0f..23f,
                    hourLabel(s.scheduleTo), steps = 22, color = Tile.Purple) { v -> SettingsRepo.update { copy(scheduleTo = v.toInt()) } }
            }
        }

        SectionHeader(tr("Раскрытый остров", "Expanded island"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.UnfoldLess, tr("Сворачивать раскрытый остров", "Collapse the expanded island"), collapseLabel(s.autoCollapseSec),
                color = Tile.Indigo, onClick = { collapseDialog = true })
            GroupToggle(if (s.bubble) Pos.Middle else Pos.Last, Icons.Rounded.Circle, tr("Вторая активность рядом", "Second activity alongside"),
                tr("Когда активностей две, вторая — кружком рядом с капсулой, как на iPhone",
                    "With two activities, the second one is a small circle next to the capsule, like on iPhone"),
                s.bubble, color = Tile.Purple) { v ->
                SettingsRepo.update { copy(bubble = v) }
            }
            if (s.bubble) {
                GroupItem(Pos.Last, if (s.bubbleSide == BubbleSide.LEFT) Icons.Rounded.AlignHorizontalLeft else Icons.Rounded.AlignHorizontalRight,
                    tr("Сторона кружка", "Circle side"), s.bubbleSide.title, color = Tile.Purple, onClick = { sideDialog = true })
            }
        }

        // Нажатие на пустой остров: лишние кнопки убираются, а можно не раскрывать его вовсе
        SectionHeader(tr("Пустой остров по нажатию", "Tapping the empty island"))
        Group {
            GroupToggle(if (s.idleExpand) Pos.First else Pos.Single, Icons.Rounded.UnfoldMore, tr("Раскрывать", "Expand"),
                tr("Часы, заряд и кнопки ниже. Выключено — нажатие ничего не делает",
                    "Clock, battery and the buttons below. Off — tapping does nothing"),
                s.idleExpand, color = Tile.Blue) { v -> SettingsRepo.update { copy(idleExpand = v) } }
            if (s.idleExpand) {
                QuickAction.entries.forEachIndexed { i, a ->
                    GroupToggle(if (i == QuickAction.entries.lastIndex) Pos.Last else Pos.Middle, a.settingsIcon, a.title,
                        a.hint, a in s.quickActions, color = Tile.Orange) { v ->
                        // Порядок кнопок — как в списке, что бы ни включали первым
                        SettingsRepo.update { copy(quickActions = QuickAction.entries.filter { if (it == a) v else it in quickActions }) }
                    }
                }
            }
        }

        SectionHeader(tr("Отклик", "Feedback"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Vibration, tr("Виброотклик", "Haptics"),
                tr("При открытии, закрытии и нажатиях на капсулу", "When opening, closing and tapping the capsule"),
                s.haptics, color = Tile.Pink) { v -> SettingsRepo.update { copy(haptics = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.LightMode, tr("Включать экран", "Turn on the screen"),
                tr("Когда звонит таймер, будильник или входящий вызов — экран включается и не гаснет, пока звонит",
                    "When a timer, alarm or incoming call rings, the screen turns on and stays on while it rings"),
                s.wakeForAlerts, color = Tile.Yellow) { v -> SettingsRepo.update { copy(wakeForAlerts = v) } }
        }
    }

    if (collapseDialog) {
        ChoiceDialog(tr("Сворачивать раскрытый остров", "Collapse the expanded island"), listOf(0, 5, 10, 20, 30), s.autoCollapseSec, ::collapseLabel,
            { v -> SettingsRepo.update { copy(autoCollapseSec = v) } }) { collapseDialog = false }
    }
    if (landscapeDialog) {
        ChoiceDialog(tr("В альбомной ориентации", "In landscape"), LandscapeMode.entries, s.landscape, { "${it.title} — ${it.hint}" },
            { v -> SettingsRepo.update { copy(landscape = v) } }) { landscapeDialog = false }
    }
    if (lockDialog) {
        ChoiceDialog(tr("На экране блокировки", "On the lock screen"), LockScreenMode.entries, s.lockScreen, { it.title },
            { v -> SettingsRepo.update { copy(lockScreen = v) } }) { lockDialog = false }
    }
    if (sideDialog) {
        ChoiceDialog(tr("Сторона кружка", "Circle side"), BubbleSide.entries, s.bubbleSide, { it.title },
            { v -> SettingsRepo.update { copy(bubbleSide = v) } }) { sideDialog = false }
    }
}

/** «Язык» на текущем языке и рядом по-английски — язык найдётся, даже если сейчас он непонятен. */
private fun languageTitle(): String = when (Lang.code) {
    "en" -> "Language"
    "ru" -> "Язык · Language"
    "id" -> "Bahasa · Language"
    "pt" -> "Idioma · Language"
    "kk" -> "Тіл · Language"
    else -> "Language"
}

/** Час расписания: «10:00». */
private fun hourLabel(h: Int) = "%d:00".format(h)

/** Подпись для «Сворачивать раскрытый остров». */
private fun collapseLabel(sec: Int) = if (sec <= 0) tr("Только касанием мимо или свайпом вверх", "Only by tapping outside or swiping up")
    else trf("Через {0} секунд без касаний", "After {0} seconds without a touch", sec)

private val accentColors = listOf(
    0xFF0A84FF, 0xFFC6C3F7, 0xFF64D2FF, 0xFF34C759, 0xFFFFD60A, 0xFFFF9F0A,
    0xFFFF453A, 0xFFFF2D92, 0xFFBF5AF2, 0xFFFFFFFF,
)

/** «Уведомления»: сообщения приложений в острове — что показывать, сколько текста и каким цветом кнопки. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var accentDialog by remember { mutableStateOf(false) }
    var styleDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }

    Page(tr("Уведомления", "Notifications"), onBack) {
        Group {
            GroupToggle(Pos.First, Icons.Rounded.ChatBubble, tr("Уведомления приложений", "App notifications"),
                tr("Сообщения из Telegram, WhatsApp, почты и других приложений — карточкой в острове, как на iPhone",
                    "Messages from Telegram, WhatsApp, email and other apps as a card in the island, like on iPhone"),
                s.cardMessages, color = Tile.Blue) { v -> SettingsRepo.update { copy(cardMessages = v) } }
            GroupToggle(Pos.Middle, Icons.AutoMirrored.Rounded.Reply, tr("Кнопки в уведомлениях", "Notification buttons"),
                tr("«Ответить» с вводом текста прямо в острове и «Пометить прочитанным»", "Reply with typing right in the island, and Mark as read"),
                s.msgActions, color = Tile.Indigo) { v -> SettingsRepo.update { copy(msgActions = v) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Smartphone, tr("Из открытого приложения", "From the open app"),
                tr("Показывать и уведомления приложения, которое сейчас на экране", "Also show notifications from the app that's on screen"),
                s.msgInApp, color = Tile.Teal) { v ->
                SettingsRepo.update { copy(msgInApp = v) }
            }
            GroupSlider(
                Pos.Last, Icons.Rounded.FormatLineSpacing, tr("Строк текста", "Text lines"),
                value = s.msgLines.toFloat(), range = 1f..6f, steps = 4, label = "${s.msgLines}", color = Tile.Green,
            ) { v -> SettingsRepo.update { copy(msgLines = Math.round(v)) } }
        }

        SectionHeader(tr("Как показывать", "How to show"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.ViewAgenda, tr("Вид уведомлений", "Notification style"),
                "${s.messageStyle.title} — ${s.messageStyle.hint}", color = Tile.Blue, onClick = { styleDialog = true })
            GroupItem(Pos.Middle, Icons.Rounded.Timelapse, tr("Сколько показывать", "How long to show"),
                messageTimeLabel(s.messageSeconds), color = Tile.Orange, onClick = { timeDialog = true })
            GroupItem(Pos.Middle, Icons.Rounded.History, tr("История уведомлений", "Notification history"),
                historyLabel(s.historyMinutes), color = Tile.Indigo, onClick = { historyDialog = true })
            GroupToggle(Pos.Last, Icons.Rounded.Repeat, tr("Убирать повторы", "Skip repeats"),
                tr("То же сообщение ещё раз — от второго аккаунта, пересланное, из двух приложений — не показывается",
                    "The same message again — from a second account, forwarded, or from two apps — isn't shown"),
                s.msgDedupe, color = Tile.Teal) { v -> SettingsRepo.update { copy(msgDedupe = v) } }
        }

        SectionHeader(tr("Какие приложения", "Which apps"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.NotificationImportant, tr("Тихие уведомления", "Silent notifications"),
                tr("Показывать и те, что Android не показывает всплывающими, — так бывает у Snapchat, Instagram и многих приложений",
                    "Also show ones Android doesn't pop up — this happens with Snapchat, Instagram and many apps"),
                s.msgQuiet, color = Tile.Purple) { v -> SettingsRepo.update { copy(msgQuiet = v) } }
            GroupItem(Pos.Middle, Icons.Rounded.Apps, tr("Приложения", "Apps"),
                tr("Всегда или никогда — для каждого приложения", "Always or never — for each app"),
                color = Tile.Blue, onClick = { open(Screen.NOTIF_APPS) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.ManageSearch, tr("Почему не показано", "Why it didn't show"),
                tr("Что остров решил о последних уведомлениях", "What the island decided about recent notifications"),
                color = Tile.Gray, onClick = { open(Screen.NOTIF_DIAG) }) { Arrow() }
        }

        SectionHeader(tr("Цвет кнопок", "Button color"))
        AccentPreview(s)
        GroupItem(Pos.Single, Icons.Rounded.Palette, tr("Цвет кнопок", "Button color"), "${s.accentMode.title} — ${s.accentMode.hint}",
            onClick = { accentDialog = true }, color = Tile.Orange)
        if (s.accentMode == AccentMode.CUSTOM) {
            Swatches(accentColors, s.accentColor) { c -> SettingsRepo.update { copy(accentColor = c) } }
        }
    }

    if (accentDialog) {
        ChoiceDialog(tr("Цвет кнопок", "Button color"), AccentMode.entries, s.accentMode, { "${it.title} — ${it.hint}" },
            { v -> SettingsRepo.update { copy(accentMode = v) } }) { accentDialog = false }
    }
    if (styleDialog) {
        ChoiceDialog(tr("Вид уведомлений", "Notification style"), MessageStyle.entries, s.messageStyle, { "${it.title} — ${it.hint}" },
            { v -> SettingsRepo.update { copy(messageStyle = v) } }) { styleDialog = false }
    }
    if (timeDialog) {
        ChoiceDialog(tr("Сколько показывать", "How long to show"), listOf(0, 5, 8, 10, 15, 30, -1), s.messageSeconds, ::messageTimeLabel,
            { v -> SettingsRepo.update { copy(messageSeconds = v) } }) { timeDialog = false }
    }
    if (historyDialog) {
        ChoiceDialog(tr("История уведомлений", "Notification history"), listOf(0, 5, 15, 60, -1), s.historyMinutes, ::historyLabel,
            { v -> SettingsRepo.update { copy(historyMinutes = v) } }) { historyDialog = false }
    }
}

/** «Сколько показывать» уведомление: как события, секунды или пока не смахнёте. */
private fun messageTimeLabel(sec: Int) = when {
    sec == 0 -> tr("Как события, с запасом на чтение", "Like events, with extra time to read")
    sec < 0 -> tr("Пока не смахнёте", "Until you swipe it away")
    else -> trf("{0} секунд", "{0} seconds", sec)
}

/** «История уведомлений»: выключена, минуты или пока не прочитаете. */
private fun historyLabel(min: Int) = when {
    min == 0 -> tr("Выключена — уведомление уходит вместе с плашкой", "Off — a notification leaves with its banner")
    min < 0 -> tr("Ждут в острове, пока не прочитаете", "They wait in the island until you read them")
    min >= 60 -> trf("Ждут в острове {0} ч", "They wait in the island for {0} h", min / 60)
    else -> trf("Ждут в острове {0} минут", "They wait in the island for {0} minutes", min)
}

/** Как выглядят «Ответить», «Пометить прочитанным» и кнопка отправки с выбранным цветом. */
@Composable
private fun AccentPreview(s: IslandSettings) {
    val context = LocalContext.current
    // В режиме «Из аватара» цвет зависит от самого уведомления — в примере берём оранжевый
    val accent = resolveAccent(context, s, Tints.Orange)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewTile(tr("Ответить", "Reply"), accent, Modifier.weight(1f))
        PreviewTile(tr("Пометить прочитанным", "Mark as read"), accent, Modifier.weight(2f))
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = onAccent(accent), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PreviewTile(title: String, accent: Color, modifier: Modifier) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitText(title, accent, 14.sp, 10.sp, FontWeight.SemiBold)
    }
}

private val QuickAction.settingsIcon: ImageVector
    get() = when (this) {
        QuickAction.TIMER -> Icons.Rounded.HourglassBottom
        QuickAction.STOPWATCH -> Icons.Rounded.Timer
        QuickAction.FLASHLIGHT -> Icons.Rounded.FlashlightOn
        QuickAction.SCREENSHOT -> Icons.Rounded.Screenshot
        QuickAction.CAMERA -> Icons.Rounded.PhotoCamera
        QuickAction.LOCK -> Icons.Rounded.Lock
        QuickAction.RINGER -> Icons.AutoMirrored.Rounded.VolumeUp
        QuickAction.DND -> Icons.Rounded.DoNotDisturbOn
    }

/** Подпись к кнопке — только там, где непонятно по названию */
private val QuickAction.hint: String?
    get() = when (this) {
        QuickAction.SCREENSHOT -> tr("Остров на миг прячется — на снимке его нет (Android 9+)",
            "The island hides for a moment, so it's not in the shot (Android 9+)")
        QuickAction.LOCK -> tr("Гасит экран, как кнопка питания (Android 9+)", "Turns the screen off like the power button (Android 9+)")
        QuickAction.RINGER -> tr("Звонок → вибрация → без звука. Для «Без звука» Android попросит доступ к «Не беспокоить»",
            "Ring → vibrate → silent. For Silent, Android asks for Do Not Disturb access")
        QuickAction.DND -> tr("Включает и выключает «Не беспокоить». В первый раз Android попросит доступ",
            "Turns Do Not Disturb on and off. The first time, Android asks for access")
        else -> null
    }
