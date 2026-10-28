package com.akira.island.service

import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.akira.island.core.BatteryInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios
import kotlin.math.abs

/** Системные события, которые остров показывает короткими уведомлениями. */
class SystemEvents(private val context: Context) {
    private fun ic(id: Int) = Ios.load(context, id)

    private val main = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.elapsedRealtime()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val s get() = SettingsRepo.current

    private var lowWarned = false
    private var usbConnected: Boolean? = null
    private var brightnessMode = -1
    private var dnd: Boolean? = null
    private val headsetBattery = HashMap<String, Int>()
    private val wifiNetworks = HashSet<Network>()
    /** Когда подключили или отключили зарядку — кабель от компьютера не должен давать вторую плашку «USB» */
    private var lastPowerChange = 0L

    /** Первые секунды после запуска система присылает текущее состояние — это не события. */
    private val warmingUp get() = SystemClock.elapsedRealtime() - startedAt < 3000

    private fun notice(enabled: Boolean, n: Notice) {
        if (enabled && !warmingUp) IslandBus.post(n)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = handle(intent, isInitialStickyBroadcast)
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                val wasEmpty = wifiNetworks.isEmpty()
                wifiNetworks += network
                if (wasEmpty) notice(s.evWifiOn, Notice("Wi-Fi", tr("Подключён", "Connected"), ic(R.drawable.ios_wifi), Tints.Blue))
            }
        }

        override fun onLost(network: Network) {
            main.post {
                if (wifiNetworks.remove(network) && wifiNetworks.isEmpty()) {
                    notice(s.evWifiOff, Notice("Wi-Fi", tr("Отключён", "Disconnected"), ic(R.drawable.ios_wifi), Tints.Gray))
                }
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            val mode = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0,
            )
            if (mode == brightnessMode) return
            brightnessMode = mode
            val auto = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            notice(
                s.evBrightness,
                Notice(
                    tr("Яркость", "Brightness"),
                    if (auto) tr("Авто", "Auto") else tr("Вручную", "Manual"),
                    if (auto) ic(R.drawable.ios_sunny) else ic(R.drawable.ios_contrast),
                    Tints.Yellow,
                ),
            )
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BT_BATTERY_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(USB_STATE)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        dnd = isDnd()
        IslandBus.dnd.value = dnd == true
        IslandBus.ringer.value = context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val power = context.getSystemService(PowerManager::class.java)
        IslandBus.screenOn.value = power?.isInteractive ?: true
        IslandBus.powerSave.value = power?.isPowerSaveMode == true
        IslandBus.locked.value = isLocked()
        IslandBus.keyguard.value = isKeyguard()
        brightnessMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver,
        )

        runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback,
            )
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { context.contentResolver.unregisterContentObserver(brightnessObserver) }
        runCatching { connectivity?.unregisterNetworkCallback(wifiCallback) }
    }

    private fun handle(intent: Intent, initialSticky: Boolean) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)

            Intent.ACTION_POWER_CONNECTED -> {
                lastPowerChange = SystemClock.elapsedRealtime()
                val level = IslandBus.battery.value.level
                notice(
                    s.evChargeStart,
                    Notice(tr("Зарядка", "Charging"), if (level >= 0) "$level%" else null, ic(R.drawable.ios_flash), Tints.Green,
                        battery = level.takeIf { it >= 0 }),
                )
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                lastPowerChange = SystemClock.elapsedRealtime()
                val level = IslandBus.battery.value.level
                notice(
                    s.evChargeStop,
                    Notice(tr("Отключено", "Unplugged"), if (level >= 0) "$level%" else null, ic(R.drawable.ios_battery_full), Tints.Orange,
                        battery = level.takeIf { it >= 0 }),
                )
            }

            Intent.ACTION_USER_PRESENT -> onUnlocked()

            Intent.ACTION_SCREEN_OFF -> {
                IslandBus.screenOn.value = false
                // Блокировка может включиться чуть позже экрана — заранее прячем тексты, если она вообще есть
                if (keyguard?.isDeviceSecure == true) IslandBus.locked.value = true
                IslandBus.reply.value = null
                // Как на iPhone: кнопка питания выключает звонящий таймер
                if (IslandBus.timer.value?.ringing == true) Controls.cancelTimer()
                Controls.collapse()
            }

            Intent.ACTION_SCREEN_ON -> {
                IslandBus.screenOn.value = true
                IslandBus.locked.value = isLocked()
                IslandBus.keyguard.value = isKeyguard()
            }

            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                IslandBus.powerSave.value = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val on = isDnd()
                if (on == dnd) return
                dnd = on
                IslandBus.dnd.value = on
                // Как фокус «Не беспокоить» на iPhone: луна и состояние
                notice(
                    s.evDnd,
                    Notice(tr("Не беспокоить", "Do Not Disturb"), if (on) tr("Вкл", "On") else tr("Выкл", "Off"), ic(R.drawable.ios_moon),
                        if (on) Tints.Purple else Tints.Gray),
                )
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                if (on) notice(s.evAirplaneOn, Notice(tr("Режим полёта", "Airplane Mode"), tr("Вкл", "On"), ic(R.drawable.ios_airplane), Tints.Orange))
                else notice(s.evAirplaneOff, Notice(tr("Режим полёта", "Airplane Mode"), tr("Выкл", "Off"), ic(R.drawable.ios_airplane), Tints.Gray))
            }

            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
                if (mode >= 0) IslandBus.ringer.value = mode
                val n = when (mode) {
                    // Как переключатель «Звонок/Бесшумно» на iPhone: короткая пилюля, колокольчик качается
                    AudioManager.RINGER_MODE_NORMAL -> Notice("", tr("Звонок", "Ring"), ic(R.drawable.ios_notifications), Color.White, style = NoticeStyle.PILL)
                    AudioManager.RINGER_MODE_VIBRATE -> Notice("", tr("Вибрация", "Vibrate"), ic(R.drawable.ios_phone_portrait),
                        Tints.Purple, style = NoticeStyle.PILL)
                    AudioManager.RINGER_MODE_SILENT -> Notice("", tr("Без звука", "Silent"), ic(R.drawable.ios_notifications_off),
                        Tints.Red, style = NoticeStyle.PILL)
                    else -> null
                }
                if (n != null) notice(s.evRinger, n)
            }


            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                // Без разрешения BLUETOOTH_CONNECT имени и класса устройства не узнать
                val name = runCatching { device.name }.getOrNull()
                // Класс не прочитать — не знаем, наушники ли это (могут быть часы или машина), молчим
                val isAudio = runCatching {
                    device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                }.getOrDefault(false)
                if (!isAudio) return
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    val battery = headsetBattery[device.address]
                    notice(
                        s.evHeadsetOn,
                        Notice(name ?: tr("Наушники", "Headphones"), null, ic(R.drawable.ios_headset), Tints.Green,
                            ring = battery?.let { it / 100f }, caption = tr("Подключено", "Connected")),
                    )
                } else {
                    headsetBattery.remove(device.address)
                    notice(s.evHeadsetOff, Notice(name ?: tr("Наушники", "Headphones"), null, ic(R.drawable.ios_headset), Tints.Gray,
                        caption = tr("Отключено", "Disconnected")))
                }
            }

            BT_BATTERY_CHANGED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                val level = intent.getIntExtra(EXTRA_BT_BATTERY, -1)
                if (level >= 0) headsetBattery[device.address] = level
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                notice(s.evUsbOn, Notice(dev?.productName ?: tr("USB-устройство", "USB device"), tr("Подключено", "Connected"),
                    ic(R.drawable.ios_hardware_chip), Tints.Teal))
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                notice(s.evUsbOff, Notice(dev?.productName ?: tr("USB-устройство", "USB device"), tr("Отключено", "Disconnected"),
                    ic(R.drawable.ios_hardware_chip), Tints.Gray))
            }

            USB_STATE -> {
                val connected = intent.getBooleanExtra("connected", false)
                val prev = usbConnected
                usbConnected = connected
                if (initialSticky || prev == null || prev == connected) return
                // Кабель от компьютера ещё и заряжает: плашки «Зарядка» достаточно. Порядок сообщений
                // системы не определён, поэтому смотрим чуть позже, менялась ли зарядка рядом по времени
                main.postDelayed({
                    if (abs(SystemClock.elapsedRealtime() - lastPowerChange) < 3000) return@postDelayed
                    if (connected) notice(s.evUsbOn, Notice("USB", tr("Подключено", "Connected"), ic(R.drawable.ios_hardware_chip), Tints.Teal))
                    else notice(s.evUsbOff, Notice("USB", tr("Отключено", "Disconnected"), ic(R.drawable.ios_hardware_chip), Tints.Gray))
                }, 1500)
            }
        }
    }

    private var lastUnlock = 0L

    /**
     * Разблокировали. Сообщение системы USER_PRESENT доходит с задержкой, поэтому служба острова
     * зовёт это раньше — как только видит, что экран блокировки ушёл. Второй вызов подряд ничего не делает.
     */
    fun onUnlocked() {
        IslandBus.locked.value = false
        IslandBus.keyguard.value = false
        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlock < 3_000) return
        lastUnlock = now
        notice(s.evUnlock, Notice("", null, ic(R.drawable.ios_lock_open), Color.White, style = NoticeStyle.PILL))
    }

    /** Заблокирован ли телефон так, что для входа нужен PIN-код, ключ или пароль. */
    private fun isLocked(): Boolean = keyguard?.isDeviceLocked == true

    /** На экране экран блокировки — с PIN-кодом или просто «проведите, чтобы разблокировать». */
    private fun isKeyguard(): Boolean = keyguard?.isKeyguardLocked == true

    private fun isDnd(): Boolean {
        val filter = notifications?.currentInterruptionFilter ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun onBattery(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = if (raw >= 0) raw * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 &&
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        IslandBus.battery.value = BatteryInfo(level, charging)

        if (charging || level > 5) {
            lowWarned = false
        } else if (level >= 0 && !lowWarned) {
            lowWarned = true
            notice(s.evLowBattery, Notice(tr("Низкий заряд", "Low Battery"), "$level%", ic(R.drawable.ios_battery_dead), Tints.Red, battery = level))
        }
    }

    private companion object {
        // Скрытые, но давно стабильные широковещательные сообщения системы
        const val BT_BATTERY_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val USB_STATE = "android.hardware.usb.action.USB_STATE"
    }
}
