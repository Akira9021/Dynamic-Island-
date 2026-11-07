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
import com.akira.island.core.ChargeEtaLog
import com.akira.island.core.ChargeRingMath
import com.akira.island.core.ChargeTimeEstimator
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

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
    /**
     * Reference of the current charging session. Persisted so it survives
     * service and UI restarts, and anchored to EXTRA_PLUGGED instead of the
     * "charging" flag, which some OEMs make flicker while the cable is in.
     */
    private val ringPrefs = context.getSharedPreferences(RING_PREFS, Context.MODE_PRIVATE)
    private var ringStart = ringPrefs.getLong(KEY_RING_START, 0L)
    private var ringStartLevel = ringPrefs.getInt(KEY_RING_LEVEL, -1)
    private var ringMaxProgress = ringPrefs.getFloat(KEY_RING_MAX, 0f)
    /**
     * Remaining-time reference used by the ring. It is refreshed only when the
     * battery level goes up, so a new estimate arriving while the charging card
     * moves to the secondary bubble cannot recalculate the ring.
     */
    private var ringReferenceMinutes = ringPrefs.getInt(KEY_RING_REFERENCE, -1)
    private var ringReferenceLevel = ringPrefs.getInt(KEY_RING_REFERENCE_LEVEL, -1)
    /** Идёт ли зарядка прямо сейчас — пока идёт, раз в полминуты обновляем оценку */
    private var chargingNow = false

    /** Пока заряжается, перечитываем состояние: оценка времени должна идти вниз, а не стоять. */
    private val batteryTick = object : Runnable {
        override fun run() {
            if (!chargingNow) return
            runCatching {
                val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (sticky != null) onBattery(sticky)
            }
            main.postDelayed(this, BATTERY_TICK_MS)
        }
    }

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
        main.removeCallbacks(batteryTick)
        chargingNow = false
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
                if (IslandBus.timer.value?.ringing == true) Controls.stopTimer(context)
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
                val n = when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
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

    private fun saveRingSession() {
        ringPrefs.edit()
            .putLong(KEY_RING_START, ringStart)
            .putInt(KEY_RING_LEVEL, ringStartLevel)
            .putFloat(KEY_RING_MAX, ringMaxProgress)
            .putInt(KEY_RING_REFERENCE, ringReferenceMinutes)
            .putInt(KEY_RING_REFERENCE_LEVEL, ringReferenceLevel)
            .apply()
    }

    private fun clearRingSession() {
        ringStart = 0L
        ringStartLevel = -1
        ringMaxProgress = 0f
        ringReferenceMinutes = -1
        ringReferenceLevel = -1
        ringPrefs.edit().clear().apply()
        ChargeEtaLog.log(context, "ring clear")
    }

    private fun onBattery(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = if (raw >= 0) raw * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        // Universal check: plugged and not explicitly discharging/not-charging.
        // Some OEMs report BATTERY_STATUS_UNKNOWN in the first broadcast.
        val charging = plugged != 0 &&
            status != BatteryManager.BATTERY_STATUS_DISCHARGING &&
            status != BatteryManager.BATTERY_STATUS_NOT_CHARGING

        // Charge counter and current are read via BatteryManager.getLongProperty,
        // they are not passed as extras in the ACTION_BATTERY_CHANGED intent.
        // Not all devices expose them: getLongProperty can throw or return a sentinel.
        val bm = context.getSystemService(BatteryManager::class.java)
        val chargeCounter = runCatching {
            bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrNull() ?: Long.MIN_VALUE
        val currentNow = runCatching {
            bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull() ?: Long.MIN_VALUE

        // Ring reference: starts at plug-in and is cleared only at unplug.
        // It is persisted, so restarting the service or the UI keeps the
        // original instant instead of restarting the ring from zero.
        val now = SystemClock.elapsedRealtime()
        if (ringStart != 0L && !ChargeRingMath.isValidReference(ringStart, now)) {
            // elapsedRealtime restarted: the saved reference is from a previous boot.
            clearRingSession()
        }
        if (plugged == 0) {
            if (ringStart != 0L) clearRingSession()
        } else if (ringStart == 0L) {
            ringStart = now
            ringStartLevel = level
            ringMaxProgress = 0f
            ringReferenceMinutes = -1
            ringReferenceLevel = level
            saveRingSession()
            ChargeEtaLog.log(context, "ring start level=$level start=$ringStart")
        }

        // Battery broadcasts only arrive when the level changes (every few minutes),
        // so keep refreshing the estimate while charging.
        if (charging && !chargingNow) {
            chargingNow = true
            main.removeCallbacks(batteryTick)
            main.postDelayed(batteryTick, BATTERY_TICK_MS)
        } else if (!charging && chargingNow) {
            chargingNow = false
            main.removeCallbacks(batteryTick)
        }

        // Compute charge time estimate asynchronously
        var chargeTimeMinutes: Int? = null
        if (charging) {
            chargeTimeMinutes = ChargeTimeEstimator.getInstance(context).onBatteryChanged(
                level = level,
                status = status,
                plugged = plugged,
                chargeCounter = chargeCounter,
                currentNow = currentNow,
            )?.minutes
        } else {
            // Reset estimator when not charging
            ChargeTimeEstimator.getInstance(context).onBatteryChanged(
                level = level,
                status = status,
                plugged = plugged,
                chargeCounter = chargeCounter,
                currentNow = currentNow,
            )
        }
        
        // Monotonic ring progress, computed here so the UI only renders a
        // stable value even when the estimate fluctuates between branches.
        var chargeProgress = 0f
        if (plugged != 0 && ringStart != 0L) {
            // Refresh the reference only when the level goes up. A new estimate
            // arriving at the same level (or during the primary -> secondary
            // transition) must not recalculate the ring.
            val nextReference = ChargeRingMath.referenceMinutes(
                saved = ringReferenceMinutes,
                savedLevel = ringReferenceLevel,
                currentLevel = level,
                estimate = chargeTimeMinutes,
            )
            if (nextReference != ringReferenceMinutes) {
                ringReferenceMinutes = nextReference
                ringReferenceLevel = level
                saveRingSession()
            }
            val candidate = if (level >= 100) 1f
            else ChargeRingMath.progress(now, ringStart, ringReferenceMinutes)
            val next = ChargeRingMath.advance(ringMaxProgress, candidate)
            if (next != ringMaxProgress) {
                ringMaxProgress = next
                ringPrefs.edit().putFloat(KEY_RING_MAX, next).apply()
            }
            chargeProgress = ringMaxProgress
            ChargeEtaLog.log(
                context,
                "ring elapsed=${(now - ringStart).coerceAtLeast(0)} " +
                    "remaining=${chargeTimeMinutes ?: -1}min ref=$ringReferenceMinutes " +
                    "refLevel=$ringReferenceLevel " +
                    "progress=${(chargeProgress * 1000).roundToLong() / 1000.0} " +
                    "startLevel=$ringStartLevel level=$level",
            )
        }

        IslandBus.battery.value = BatteryInfo(level, charging, chargeTimeMinutes, ringStart, chargeProgress)

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
        /** Как часто обновлять оценку времени зарядки, пока идёт зарядка */
        const val BATTERY_TICK_MS = 30_000L
        /** Persisted reference of the charging session (ring of the secondary bubble). */
        const val RING_PREFS = "charge_ring"
        const val KEY_RING_START = "ring_start"
        const val KEY_RING_LEVEL = "ring_start_level"
        const val KEY_RING_MAX = "ring_max_progress"
        const val KEY_RING_REFERENCE = "ring_reference_minutes"
        const val KEY_RING_REFERENCE_LEVEL = "ring_reference_level"
    }
}
