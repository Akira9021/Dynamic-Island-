package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.IconAction
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/** «Динамические карточки»: что остров показывает, пока это идёт, — по разделам. */
@Composable
fun CardsScreen(onBack: () -> Unit, onInstruction: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page(tr("Динамические карточки", "Dynamic cards"), onBack,
        action = { IconAction(Icons.Rounded.Lightbulb, tr("Инструкция", "Instructions"), onInstruction) }) {
        SectionHeader(tr("Музыка и звонки", "Music and calls"))
        Group {
            GroupToggle(Pos.First, Icons.Outlined.PlayCircle, tr("Управление проигрывателем", "Player controls"),
                tr("Когда играет музыка и плеер свёрнут", "When music is playing and the player is in the background"),
                s.cardMedia, color = Tile.Pink) { set { copy(cardMedia = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Call, tr("Звонки", "Calls"),
                tr("Входящий вызов с кнопками ответа и время разговора", "Incoming calls with answer buttons, and the call time"),
                s.cardCall, color = Tile.Green) { set { copy(cardCall = it) } }
        }

        SectionHeader(tr("Таймеры и будильник", "Timers and alarm"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.HourglassEmpty, tr("Таймер и секундомер", "Timer and stopwatch"),
                tr("Таймер и секундомер «Часов» в фоне — даже если их уведомления выключены",
                    "Clock's timer and stopwatch in the background — even with its notifications off"),
                s.cardTimer, color = Tile.Orange) { set { copy(cardTimer = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.TouchApp, tr("Кнопки для «Часов»", "Clock buttons"),
                tr("Пауза и сброс в острове нажимают кнопки самих «Часов». Выключено — просто открывают «Часы»",
                    "Pause and reset in the island press Clock's own buttons. Off — they just open Clock"),
                s.clockAutoTap, color = Tile.Orange) { set { copy(clockAutoTap = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.KeyboardVoice, tr("Таймер от помощника", "Timer from assistant"),
                tr("«Поставь таймер на 5 минут» от помощника или виджета можно отдать острову",
                    "“Set a timer for 5 minutes” from an assistant or widget can go to the island"),
                s.timerFromAssistant, color = Tile.Pink) { set { copy(timerFromAssistant = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Bedtime, tr("Будильник", "Alarm"),
                tr("За 12 часов до будильника — время подъёма и циферблат сна, как на iPhone",
                    "12 hours before an alarm — the wake-up time and a sleep dial, like on iPhone"),
                s.cardAlarm, color = Tile.Indigo) { set { copy(cardAlarm = it) } }
        }

        SectionHeader(tr("Система", "System"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.FlashlightOn, tr("Управление вспышкой", "Flashlight controls"),
                tr("Когда включён фонарик", "When the flashlight is on"),
                s.cardTorch, color = Tile.Yellow) { set { copy(cardTorch = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.OfflineBolt, tr("Индикатор зарядки", "Charging indicator"),
                tr("Когда устройство заряжается", "When the device is charging"),
                s.cardCharging, color = Tile.Green) { set { copy(cardCharging = it) } }
            if (s.cardCharging) {
                // Зарядка часами занимает остров — можно оставить только плашку при подключении
                GroupToggle(Pos.Middle, Icons.Rounded.BatteryChargingFull, tr("Только при подключении", "Only when plugged in"),
                    tr("Плашка «Зарядка» на пару секунд, без капсулы с процентами всё время зарядки",
                        "A Charging banner for a couple of seconds, without the percentage capsule the whole time"),
                    s.chargingBrief, color = Tile.Green) { set { copy(chargingBrief = it) } }
            }
            GroupToggle(Pos.Last, Icons.Rounded.Downloading, tr("Индикатор прогресса", "Progress indicator"),
                tr("Загрузки и отправка в Google Play, Telegram и других приложениях", "Downloads and uploads in Google Play, Telegram and other apps"),
                s.cardProgress, color = Tile.Teal) { set { copy(cardProgress = it) } }
        }

        SectionHeader(tr("В дороге", "On the road"))
        GroupToggle(Pos.Single, Icons.Rounded.Navigation, tr("Навигация, такси и доставка", "Navigation, taxi and delivery"),
            tr("Google и Яндекс Карты, Навигатор, 2ГИС, Яндекс Go, Uber и другие — расстояние и время в капсуле, подробности в раскрытом виде",
                "Google and Yandex Maps, Navigator, 2GIS, Yandex Go, Uber and others — distance and time in the capsule, details when expanded"),
            s.cardLive, color = Tile.Teal) { set { copy(cardLive = it) } }

        SectionHeader(tr("Сюрпризы", "Surprises"))
        GroupToggle(Pos.Single, Icons.Rounded.AutoAwesome, tr("Пасхалки", "Easter eggs"),
            tr("Одну особенную песню остров узнаёт и устраивает для неё маленькое шоу в такт музыке. Какую — попробуйте угадать",
                "The island recognizes one special song and puts on a little show to its beat. Which one? Try to guess"),
            s.easterEggs, color = Tile.Red) { set { copy(easterEggs = it) } }
    }
}
