package com.akira.island.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.service.ConnectionEvent
import com.akira.island.service.SystemEventObservation
import com.akira.island.ui.*


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/** «События»: короткие плашки о том, что произошло, — по разделам. */
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    val readings by SystemEventObservation.state.collectAsState()
    var example by remember { mutableStateOf(ConnectionEvent.VPN.notice(true)) }
    fun set(block: IslandSettings.() -> IslandSettings) {
        val before = SettingsRepo.current
        SettingsRepo.update(block)
        example = eventExample(before, SettingsRepo.current) ?: example
    }

    Page(tr("События", "Events"), onBack, preview = { EventExamplePreview(example) }) {
        if (!readings.observing) SettingsHint(tr("Чтобы получать реальные события, включите остров на Главной. Здесь можно посмотреть примеры без изменения настроек телефона.",
            "Turn on the island on Home to receive real events. You can view examples here without changing your phone settings."))
        SectionHeader(tr("Показ событий", "Event display"))
        Group {
            GroupSlider(Pos.First, Icons.Rounded.Timer, tr("Время на экране", "Time on screen"), s.noticeDurationMs / 1000f, 1.5f..10f,
                tr("%.1f с", "%.1f s").format(s.noticeDurationMs / 1000f), color = Tile.Orange) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 2).toInt() * 500) } }
            GroupSlider(Pos.Last, Icons.Rounded.HourglassTop, tr("Задержка перед показом", "Delay before showing"), s.noticeDelayMs / 1000f, 0f..3f,
                tr("%.1f с", "%.1f s").format(s.noticeDelayMs / 1000f), color = Tile.Gray) { v -> SettingsRepo.update { copy(noticeDelayMs = (v * 10).toInt() * 100) } }
        }
        SettingsHint(tr("Для коротких системных событий. Время сообщений настраивается отдельно в «Уведомлениях».", "For short system events. Message duration is set separately in Notifications."))
        SectionHeader(tr("Батарея", "Battery"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Bolt, tr("Зарядка началась", "Charging started"), null,
                s.evChargeStart, color = Tile.Green) { set { copy(evChargeStart = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.PowerOff, tr("Зарядка остановлена", "Charging stopped"), null,
                s.evChargeStop, color = Tile.Gray) { set { copy(evChargeStop = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BatteryAlert, tr("Низкий заряд", "Low battery"),
                tr("Когда заряд батареи падает до 5%", "When the battery drops to 5%"),
                s.evLowBattery, color = Tile.Red) { set { copy(evLowBattery = it) } }
        }

        SectionHeader(tr("Подключения", "Connections"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Wifi, tr("Wi-Fi подключён", "Wi-Fi connected"), null,
                s.evWifiOn, color = Tile.Blue) { set { copy(evWifiOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.WifiOff, tr("Wi-Fi отключён", "Wi-Fi disconnected"), null,
                s.evWifiOff, color = Tile.Gray) { set { copy(evWifiOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothConnected, tr("Наушники подключены", "Headphones connected"),
                tr("По Bluetooth, с зарядом, если он известен", "Over Bluetooth, with the charge if it's known"),
                s.evHeadsetOn, color = Tile.Indigo) { set { copy(evHeadsetOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothDisabled, tr("Наушники отключены", "Headphones disconnected"), null,
                s.evHeadsetOff, color = Tile.Gray) { set { copy(evHeadsetOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Usb, tr("USB-устройство подключено", "USB device connected"), null,
                s.evUsbOn, color = Tile.Teal) { set { copy(evUsbOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.UsbOff, tr("USB-устройство отключено", "USB device disconnected"), null,
                s.evUsbOff, color = Tile.Gray) { set { copy(evUsbOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.VpnKey, "VPN", tr("Плашка при подключении и отключении", "Banner when connecting or disconnecting"),
                s.evVpn, color = Tile.Blue) { on -> set { copy(evVpn = on) } }
            GroupToggle(Pos.Last, Icons.Rounded.WifiTethering, tr("Точка доступа", "Hotspot"), tr("Когда телефон раздаёт интернет", "When your phone shares its connection"),
                s.evHotspot, color = Tile.Teal) { on -> set { copy(evHotspot = on) } }
        }
        EventReading(ConnectionEvent.VPN, readings.vpn, readings.observing) { example = it }
        EventReading(ConnectionEvent.HOTSPOT, readings.hotspot, readings.observing) { example = it }
        if (readings.observing && readings.hotspot == null) SettingsHint(tr("Эта прошивка пока не передала состояние точки доступа. Если оно недоступно Android-приложениям, реальные плашки для неё не появятся.",
            "This phone has not reported its hotspot state. If the firmware does not expose it to apps, real hotspot banners are unavailable."))

        SectionHeader(tr("Режимы", "Modes"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.AirplanemodeActive, tr("Режим полёта включён", "Airplane mode on"), null,
                s.evAirplaneOn, color = Tile.Orange) { set { copy(evAirplaneOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AirplanemodeInactive, tr("Режим полёта выключен", "Airplane mode off"), null,
                s.evAirplaneOff, color = Tile.Gray) { set { copy(evAirplaneOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.DoNotDisturbOn, tr("Не беспокоить", "Do Not Disturb"),
                tr("Луна и состояние при переключении", "A moon and the state when it changes"),
                s.evDnd, color = Tile.Indigo) { set { copy(evDnd = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Vibration, tr("Режим громкости", "Ring mode"),
                tr("Звонок, вибрация или без звука", "Ring, vibrate or silent"),
                s.evRinger, color = Tile.Pink) { set { copy(evRinger = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BrightnessAuto, tr("Режим яркости", "Brightness mode"),
                tr("Когда включают или выключают адаптивную яркость", "When adaptive brightness is turned on or off"),
                s.evBrightness, color = Tile.Yellow) { set { copy(evBrightness = it) } }
        }

        SectionHeader(tr("Для разработчиков", "Developer options"))
        GroupToggle(Pos.Single, Icons.Rounded.DeveloperMode, tr("Отладка USB и Wi-Fi", "USB and wireless debugging"),
            tr("Сообщать об изменении режима отладки, а не о подключении кабеля", "Report debugging mode changes, not cable connections"),
            s.evDebugging, color = Tile.Gray) { on -> set { copy(evDebugging = on) } }
        EventReading(ConnectionEvent.USB_DEBUG, readings.usbDebug, readings.observing) { example = it }
        EventReading(ConnectionEvent.WIRELESS_DEBUG, readings.wirelessDebug, readings.observing) { example = it }

        SectionHeader(tr("Оплата", "Payments"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Nfc, tr("Приложите к терминалу", "Hold near reader"),
                tr("Когда на экране оплата телефоном — Mir Pay, T-Pay, SberPay, Яндекс Пэй и другие. Держится, пока не заплатите или не выйдете из приложения",
                    "When a phone payment is on screen — Mir Pay, T-Pay, SberPay, Yandex Pay and others. Stays until you pay or leave the app"),
                s.evPayReady, color = Tile.Blue) { set { copy(evPayReady = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AccountBalance, tr("Подсказка при открытии банка", "Hint when opening a bank"),
                tr("«К терминалу» сразу, как открыли банк или кошелёк, а не только на экране оплаты",
                    "Hold near reader as soon as you open a bank or wallet app, not only on the payment screen"),
                s.payHintOnOpen, color = Tile.Gray) { set { copy(payHintOnOpen = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.CheckCircle, tr("Оплачено", "Paid"),
                tr("Галочка и сумма — с экрана кошелька, из уведомления или СМС банка",
                    "A check mark and the amount — from the wallet screen, a notification or a bank SMS"),
                s.evPaySuccess, color = Tile.Green) { set { copy(evPaySuccess = it) } }
        }

        SectionHeader(tr("Другое", "Other"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Alarm, tr("Будильник поставлен", "Alarm set"),
                tr("Время подъёма и сколько осталось спать", "Wake-up time and how long you can sleep"),
                s.evAlarmSet, color = Tile.Orange) { set { copy(evAlarmSet = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Lock, tr("Замок на экране блокировки", "Lock on the lock screen"),
                tr("Пока телефон заблокирован, в капсуле замок", "While the phone is locked, the capsule shows a lock"),
                s.evLock, color = Tile.Indigo) { set { copy(evLock = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.LockOpen, tr("Разблокировка устройства", "Device unlock"),
                tr("Замок открывается, когда вы разблокируете телефон", "A lock opens when you unlock the phone"),
                s.evUnlock, color = Tile.Purple) { set { copy(evUnlock = it) } }
        }
    }
}

@Composable
private fun EventReading(kind: ConnectionEvent, value: Boolean?, observing: Boolean, onExample: (Notice) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(kind.title + " · " + when {
            !observing -> tr("остров выключен", "island is off")
            value == true -> tr("сейчас включено", "currently on")
            value == false -> tr("сейчас выключено", "currently off")
            else -> tr("нет данных от телефона", "no reading from phone")
        }, color = Ui.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onExample(kind.notice(value != true)) }) { Text(tr("Пример", "Example")) }
    }
}

@Composable
private fun EventExamplePreview(example: Notice) {
    val s by SettingsRepo.state.collectAsState()
    var replay by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(example.id, s.noticeDurationMs, s.noticeDelayMs, replay) {
        shown = false; waiting = s.noticeDelayMs > 0
        kotlinx.coroutines.delay(s.noticeDelayMs.toLong())
        waiting = false; shown = true
        kotlinx.coroutines.delay(s.noticeDurationMs.toLong())
        shown = false
    }
    Column {
        SettingsPreview(event = example, eventShown = shown)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (waiting) tr("Ожидание перед показом…", "Waiting before showing…") else tr("Это пример, состояние телефона не меняется", "Preview only; phone state is unchanged"),
                color = Ui.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { replay++ }) { Text(tr("Повторить", "Replay")) }
        }
    }
}

private fun eventExample(old: IslandSettings, s: IslandSettings): Notice? {
    if (old.evVpn != s.evVpn) return ConnectionEvent.VPN.notice(s.evVpn)
    if (old.evHotspot != s.evHotspot) return ConnectionEvent.HOTSPOT.notice(s.evHotspot)
    if (old.evDebugging != s.evDebugging) return ConnectionEvent.USB_DEBUG.notice(s.evDebugging)
    val change = listOf(
        Triple(old.evChargeStart != s.evChargeStart, tr("Зарядка", "Charging"), Icons.Rounded.Bolt),
        Triple(old.evChargeStop != s.evChargeStop, tr("Зарядка остановлена", "Charging stopped"), Icons.Rounded.PowerOff),
        Triple(old.evLowBattery != s.evLowBattery, tr("Низкий заряд", "Low battery"), Icons.Rounded.BatteryAlert),
        Triple(old.evWifiOn != s.evWifiOn, "Wi-Fi", Icons.Rounded.Wifi),
        Triple(old.evWifiOff != s.evWifiOff, "Wi-Fi", Icons.Rounded.WifiOff),
        Triple(old.evHeadsetOn != s.evHeadsetOn, tr("Наушники", "Headphones"), Icons.Rounded.BluetoothConnected),
        Triple(old.evHeadsetOff != s.evHeadsetOff, tr("Наушники", "Headphones"), Icons.Rounded.BluetoothDisabled),
        Triple(old.evUsbOn != s.evUsbOn || old.evUsbOff != s.evUsbOff, "USB", Icons.Rounded.Usb),
        Triple(old.evAirplaneOn != s.evAirplaneOn || old.evAirplaneOff != s.evAirplaneOff, tr("Режим полёта", "Airplane mode"), Icons.Rounded.AirplanemodeActive),
        Triple(old.evDnd != s.evDnd, tr("Не беспокоить", "Do Not Disturb"), Icons.Rounded.DoNotDisturbOn),
        Triple(old.evRinger != s.evRinger, tr("Режим громкости", "Ring mode"), Icons.Rounded.Vibration),
        Triple(old.evBrightness != s.evBrightness, tr("Яркость", "Brightness"), Icons.Rounded.BrightnessAuto),
        Triple(old.evPayReady != s.evPayReady || old.payHintOnOpen != s.payHintOnOpen, tr("К терминалу", "Hold near reader"), Icons.Rounded.Nfc),
        Triple(old.evPaySuccess != s.evPaySuccess, tr("Оплачено", "Paid"), Icons.Rounded.CheckCircle),
        Triple(old.evAlarmSet != s.evAlarmSet, tr("Будильник", "Alarm"), Icons.Rounded.Alarm),
        Triple(old.evLock != s.evLock || old.evUnlock != s.evUnlock, tr("Блокировка", "Lock"), Icons.Rounded.Lock),
    ).firstOrNull { it.first } ?: return null
    return Notice(change.second, tr("Пример", "Example"), change.third, Tints.Blue)
}
