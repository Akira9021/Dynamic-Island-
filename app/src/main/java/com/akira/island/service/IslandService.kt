package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.display.DisplayManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.ServiceCommand
import com.akira.island.R
import com.akira.island.core.LangScope
import com.akira.island.data.IslandSettings
import com.akira.island.data.LandscapeMode
import com.akira.island.data.LockScreenMode
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Egg
import com.akira.island.island.ISLAND_PAD_DP
import com.akira.island.island.IslandOverlay
import com.akira.island.island.IslandTheme
import com.akira.island.island.IslandType
import com.akira.island.island.Rotated
import com.akira.island.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Служба спец. возможностей: только она может рисовать поверх строки состояния
 * (TYPE_ACCESSIBILITY_OVERLAY), поэтому капсула живёт здесь.
 *
 * Окон два. Окно рисования — полоса во всю ширину экрана, сквозь неё проходят касания. Её размер
 * не меняется, пока остров работает: при перекладке окна система на миг показывает старую картинку
 * в новом месте или, на некоторых оболочках (ColorOS), не показывает окно вовсе — остров мигает.
 * Меняется оно только с настройками и поворотом экрана. Невидимое окно касаний лежит ровно над
 * островом и передаёт касания в окно рисования; его перекладывают, когда остров замер.
 */
class IslandService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var display: FrameLayout? = null
    private var touch: TouchRoot? = null
    private val owner = OverlayLifecycleOwner()
    private var events: SystemEvents? = null
    private var torch: TorchController? = null
    private var alarm: TimerAlarm? = null
    private var calls: CallMonitor? = null
    private var navReader: NavScreenReader? = null
    private var clockReader: ClockScreenReader? = null
    private var payReader: PayScreenReader? = null
    private var alarmMonitor: AlarmMonitor? = null
    private var screenEvents = false
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    /** Экран блокировки был на экране — ждём, когда он уйдёт, чтобы сразу показать разблокировку */
    private var keyguardShown = false

    /** Клавиатура уже появлялась, пока открыт ввод ответа: когда её спрячут, ввод закрывается */
    private var keyboardSeen = false
    /** Звонит таймер или идёт входящий вызов; [wakeBlocked] — экран выключили кнопкой, пока звонило */
    private var alerting = false
    private var wakeBlocked = false
    private var lastScreenOn = true
    private var replySince = 0L
    private var imePackages: Set<String> = emptySet()
    private var imeCheckedAt = 0L

    private var tiltOn = false
    private var tiltSmooth = 0f
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Наклон влево-вправо от -1 до 1; сглаживаем, чтобы блик плыл, а не дрожал
            tiltSmooth += (e.values[0] / 9.81f).coerceIn(-1f, 1f).minus(tiltSmooth) * 0.12f
            if (abs(tiltSmooth - IslandBus.glassTilt.value) > 0.015f) IslandBus.glassTilt.value = tiltSmooth
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var visible = true
    private var body = Rect.Zero
    private var bubble = Rect.Zero
    /** С какого момента (uptimeMillis) окно касаний ждёт перекладки; 0 — не ждёт */
    private var touchSince = 0L
    private val touchUpdate = Runnable {
        touchSince = 0L
        updateTouchWindow()
    }
    /** Скриншот без острова: снимок — когда окно уже прозрачное, остров возвращается после */
    private val shoot = Runnable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }
    private val showAgain = Runnable { display?.alpha = 1f }

    /** Поворот экрана: при «У камеры» в альбомной ориентации окно острова встаёт у края с камерой */
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) readRotation()
        }
    }

    private val displayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "DynamicIsland"
        cutoutAlways()
    }

    private val touchParams = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "DynamicIslandTouch"
        x = -10
        y = -10
        cutoutAlways()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SettingsRepo.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        IslandBus.landscape.value = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        readRotation()
        // Поворот на 180° (из одной альбомной в другую) конфигурацию не меняет — о нём сообщает только экран
        runCatching { displays?.registerDisplayListener(displayListener, handler) }

        createWindows()
        KeepAliveService.sync(this)
        IslandTile.refresh(this)
        events = SystemEvents(this).also { it.start() }
        keyguardShown = keyguard?.isKeyguardLocked == true
        torch = TorchController(this).also { it.start() }
        alarm = TimerAlarm(this, scope).also { it.start() }
        calls = CallMonitor(this, scope).also { it.start() }
        navReader = NavScreenReader(this, scope)
        clockReader = ClockScreenReader(this, scope).also { ClockControl.reader = it }
        payReader = PayScreenReader(this, scope)
        alarmMonitor = AlarmMonitor(this).also { it.start() }
        IslandBus.serviceRunning.value = true

        // Входящий смахнули — он скрыт, пока звонит; перестал звонить — следующий входящий снова в острове.
        // Здесь, а не в острове: пока остров спрятан, он не рисуется и ничего бы не сбросил
        scope.launch {
            IslandBus.calls.map { calls -> calls.values.any { it.incoming } }.distinctUntilChanged()
                .collect { ringing -> if (!ringing) IslandBus.callHidden.value = false }
        }

        // То, что умеет только служба: скриншот без острова, блокировка экрана, выключение (на время банка)
        scope.launch {
            IslandBus.commands.collect { cmd ->
                when (cmd) {
                    ServiceCommand.SCREENSHOT -> screenshotWithoutIsland()
                    ServiceCommand.LOCK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    ServiceCommand.TURN_OFF -> disableSelf()
                }
            }
        }

        // Пока вводят ответ на уведомление, окну нужен фокус, иначе клавиатура не покажется
        scope.launch {
            IslandBus.reply.collect {
                keyboardSeen = false
                replySince = SystemClock.elapsedRealtime()
                setInputFocus(it != null)
            }
        }
        // Звонит таймер, будильник «Часов» или входящий вызов: экран включается и не гаснет, пока звонит
        scope.launch {
            combine(IslandBus.timer, IslandBus.calls, IslandBus.ringingAlarms, IslandBus.screenOn) { t, calls, alarms, on ->
                (t?.ringing == true || calls.values.any { it.incoming } || alarms.isNotEmpty()) to on
            }.distinctUntilChanged().collect { (alert, on) -> onAlert(alert, on) }
        }
        // Экран погас — дальше будет экран блокировки, ждём его ухода; оплаты телефоном уже не будет
        scope.launch {
            IslandBus.screenOn.collect {
                if (!it) {
                    keyguardShown = true
                    PayMonitor.clearReady()
                }
            }
        }
        // Телефон заблокировали — отвечать с экрана блокировки нельзя
        scope.launch { IslandBus.locked.collect { if (it) IslandBus.reply.value = null } }

        // Новая версия на GitHub: проверка сама ограничена разом в 3 часа, здесь только будим её
        scope.launch {
            while (isActive) {
                if (IslandBus.screenOn.value) UpdateChecker.check(this@IslandService, force = false, announce = true)
                delay(60 * 60 * 1000L)
            }
        }

        // Liquid Glass: блики сдвигаются при наклоне телефона, датчик слушаем только пока стекло включено и экран горит
        scope.launch {
            combine(SettingsRepo.state.map { it.liquidGlass && it.enabled }, IslandBus.screenOn) { glass, on -> glass && on }
                .distinctUntilChanged()
                .collect { setTiltEnabled(it) }
        }

        scope.launch {
            combine(SettingsRepo.state, IslandBus.landscape, IslandBus.offSchedule, IslandBus.keyguard, IslandBus.displayRotation) { s, land, _, kg, rot ->
                Placement(s, land, kg, rot)
            }.collect { p -> applySettings(p.s, p.landscape, p.keyguard, p.rotation) }
        }

        // «По расписанию»: проверяем ровно на границах часов и со включением экрана (пока телефон спит, ожидание стоит)
        scope.launch {
            combine(SettingsRepo.state.map { Triple(it.scheduleOn, it.scheduleFrom, it.scheduleTo) }.distinctUntilChanged(), IslandBus.screenOn) { sch, _ -> sch }
                .collectLatest { (on) ->
                    while (true) {
                        val now = Calendar.getInstance()
                        IslandBus.offSchedule.value = !SettingsRepo.current.inSchedule(now.get(Calendar.HOUR_OF_DAY))
                        if (!on) break
                        val next = (now.clone() as Calendar).apply {
                            add(Calendar.HOUR_OF_DAY, 1)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        delay(next.timeInMillis - now.timeInMillis + 500)
                    }
                }
        }

        // Обновились — один раз карточка «Обновлено», как в ролике (первая установка молчит)
        val version = UpdateChecker.currentVersion(this)
        val seen = SettingsRepo.current.islandVersion
        if (seen != version) {
            if (seen.isNotEmpty() && SettingsRepo.current.autoUpdateCheck) {
                IslandBus.post(UpdateChecker.updatedNotice(this, version))
            }
            SettingsRepo.update { copy(islandVersion = version) }
        }

        // Пасхалка: та самая песня играет — остров празднует (эффектам хватает высоты окна)
        Egg.watch(this, scope)
    }

    private fun createWindows() {
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                IslandTheme {
                    LangScope {
                        // «У камеры» в альбомной ориентации: остров повёрнут к краю с камерой, как будто экран не поворачивали
                        val rotation by IslandBus.contentRotation.collectAsState()
                        Rotated(rotation) {
                            IslandOverlay(
                                // До целых пикселей: доли пикселя в конце пружины не двигают окна
                                onBodyBounds = { r -> val q = r.snapped(); if (q != body) { body = q; onBoundsChanged() } },
                                onBubbleBounds = { r -> val q = r.snapped(); if (q != bubble) { bubble = q; onBoundsChanged() } },
                            )
                        }
                    }
                }
            }
        }
        val drawRoot = object : FrameLayout(this) {
            // Пока вводят ответ, окно острова в фокусе и «Назад» приходит сюда: закрываем ввод, а не глотаем жест
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && IslandBus.reply.value != null) {
                    if (event.action == KeyEvent.ACTION_UP) IslandBus.reply.value = null
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        drawRoot.addView(compose)
        owner.create()
        owner.attach(drawRoot)
        placeDisplayWindow(SettingsRepo.current, IslandBus.contentRotation.value)
        runCatching { wm?.addView(drawRoot, displayParams) }.onSuccess { display = drawRoot }

        val touchRoot = TouchRoot(this, { display }, ::onOutsideTouch)
        runCatching { wm?.addView(touchRoot, touchParams) }.onSuccess { touch = touchRoot }
    }

    /**
     * Касание мимо раскрытого острова сворачивает его, мимо поля ответа — закрывает ввод.
     * Нажатия на клавиатуру тоже приходят сюда как «мимо» (она — отдельное окно), а где именно
     * коснулись, система не сообщает. Поэтому, пока клавиатура на экране, ввод не закрываем:
     * его закрывает «Назад» (клавиатура прячется) или смахивание плашки вверх.
     */
    private fun onOutsideTouch() {
        if (IslandBus.reply.value != null) {
            if (!keyboardShown()) IslandBus.reply.value = null
            return
        }
        if (IslandBus.expanded.value) Controls.collapse()
    }

    /** Видна ли клавиатура. Если список окон недоступен, считаем, что видна, — так ввод не оборвётся. */
    private fun keyboardShown(): Boolean = runCatching {
        val list = windows
        list.isEmpty() || list.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }.getOrDefault(true)

    private fun applySettings(s: IslandSettings, landscape: Boolean, keyguard: Boolean, rotation: Int) {
        IslandType.apply(this, s)
        visible = s.enabled && !(landscape && s.landscape == LandscapeMode.HIDE) && !IslandBus.offSchedule.value &&
            !(keyguard && s.lockScreen == LockScreenMode.HIDE)
        // «У камеры»: в альбомной ориентации остров повёрнут к краю с камерой; иначе — как обычно, сверху
        val turn = if (landscape && s.landscape == LandscapeMode.CAMERA) rotation else 0
        IslandBus.contentRotation.value = turn
        display?.visibility = if (visible) View.VISIBLE else View.GONE
        // Остров спрятан — выключен, вне расписания, на экране блокировки или в повороте (скажем, смотрят видео):
        // Compose в его окне не рисует и не просыпается ради анимаций и эквалайзера, пока остров снова не покажется.
        // С погашенным экраном так не делаем: включили экран — остров сразу показывает то, что есть сейчас,
        // а украшения и без того ждут экрана
        owner.setActive(visible)
        if (!visible) Controls.collapse()
        if (placeDisplayWindow(s, turn)) display?.let { v -> runCatching { wm?.updateViewLayout(v, displayParams) } }
        updateTouchWindow()
    }

    /**
     * Где окно острова: обычно — полоса сверху во всю ширину, высотой с раскрытый остров; «У камеры»
     * в альбомной ориентации — такая же полоса вдоль края с камерой. Возвращает true, если что-то поменялось.
     */
    private fun placeDisplayWindow(s: IslandSettings, rotation: Int): Boolean {
        val band = dp(windowBandDp(s))
        val offset = dp(s.topOffset - ISLAND_PAD_DP)
        val p = displayParams
        val old = listOf(p.width, p.height, p.x, p.y, p.gravity)
        if (rotation == 0) {
            p.width = WindowManager.LayoutParams.MATCH_PARENT
            p.height = band
            p.gravity = Gravity.TOP or Gravity.START
            p.x = 0
            p.y = offset
        } else {
            p.width = band
            p.height = WindowManager.LayoutParams.MATCH_PARENT
            p.gravity = (if (rotation < 0) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP
            p.x = offset
            p.y = 0
        }
        return old != listOf(p.width, p.height, p.x, p.y, p.gravity)
    }

    /** Поворот экрана: камера слева (90°) — остров повёрнут на −90°, камера справа (270°) — на 90°. */
    private fun readRotation() {
        val rotation = runCatching { displays?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation }.getOrNull() ?: Surface.ROTATION_0
        IslandBus.displayRotation.value = when (rotation) {
            Surface.ROTATION_90 -> -90
            Surface.ROTATION_270 -> 90
            else -> 0
        }
    }

    /**
     * Скриншот без острова. Системный скриншот кнопками острову не подвластен: Android не даёт прятать
     * наложение от него. Поэтому снимок делает сам остров: окно становится прозрачным, через кадр-другой
     * Android снимает экран уже без него, потом остров возвращается.
     */
    private fun screenshotWithoutIsland() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        Controls.collapse()
        val view = display ?: return
        view.alpha = 0f
        handler.removeCallbacks(shoot)
        handler.removeCallbacks(showAgain)
        handler.postDelayed(shoot, 250)
        handler.postDelayed(showAgain, 1_600)
    }

    private fun onBoundsChanged() {
        // Во время анимации границы меняются каждый кадр, а перекладка окна — вызов системы посреди кадра,
        // от которого остров может дёрнуться, а на ColorOS — пропасть на треть секунды. Поэтому окно касаний
        // перекладывается один раз, когда остров замер (120 мс без изменений), а если он движется долго
        // (пасхалка) — не чаще раза в три секунды
        val now = SystemClock.uptimeMillis()
        if (touchSince == 0L) touchSince = now
        handler.removeCallbacks(touchUpdate)
        handler.postAtTime(touchUpdate, minOf(now + TOUCH_QUIET_MS, touchSince + TOUCH_MAX_WAIT_MS))
    }

    private fun updateTouchWindow() {
        val view = touch ?: return
        val area = when {
            body.isEmpty -> bubble
            bubble.isEmpty -> body
            else -> Rect(
                minOf(body.left, bubble.left), minOf(body.top, bubble.top),
                maxOf(body.right, bubble.right), maxOf(body.bottom, bubble.bottom),
            )
        }
        val usable = visible && !area.isEmpty
        val origin = IntArray(2)
        display?.getLocationOnScreen(origin)
        val x = if (usable) origin[0] + floor(area.left).toInt() else -10
        val y = if (usable) origin[1] + floor(area.top).toInt() else -10
        val w = if (usable) ceil(area.width).toInt().coerceAtLeast(1) else 1
        val h = if (usable) ceil(area.height).toInt().coerceAtLeast(1) else 1
        val flags = if (usable) touchParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else touchParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (touchParams.x == x && touchParams.y == y && touchParams.width == w && touchParams.height == h &&
            touchParams.flags == flags
        ) return
        // Остров чуть сдвинулся или уменьшился, но остался внутри окна касаний — окно не трогаем: каждая
        // перекладка — лишний вызов системы и риск мигания на некоторых оболочках
        val slack = dp(8)
        if (usable && flags == touchParams.flags && x >= touchParams.x && y >= touchParams.y &&
            x + w <= touchParams.x + touchParams.width && y + h <= touchParams.y + touchParams.height &&
            touchParams.width - w <= slack && touchParams.height - h <= slack
        ) return
        touchParams.x = x
        touchParams.y = y
        touchParams.width = w
        touchParams.height = h
        touchParams.flags = flags
        runCatching { wm?.updateViewLayout(view, touchParams) }
    }

    private fun setTiltEnabled(on: Boolean) {
        if (on == tiltOn) return
        tiltOn = on
        val sm = getSystemService(SensorManager::class.java) ?: return
        if (on) {
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(tiltListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sm.unregisterListener(tiltListener)
            tiltSmooth = 0f
            IslandBus.glassTilt.value = 0f
        }
    }

    private fun setInputFocus(on: Boolean) {
        val view = display ?: return
        val flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val flags = if (on) displayParams.flags and flag.inv() else displayParams.flags or flag
        if (flags == displayParams.flags) return
        displayParams.flags = flags
        displayParams.softInputMode = if (on) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        else WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        runCatching { wm?.updateViewLayout(view, displayParams) }
    }

    /**
     * Звонит таймер или идёт входящий вызов — экран включается и не гаснет. Если его выключили кнопкой
     * питания, пока звонит, остров больше не включает его до следующего звонка.
     */
    private fun onAlert(alert: Boolean, screenOn: Boolean) {
        if (!alert) {
            wakeBlocked = false
        } else if (alerting && lastScreenOn && !screenOn) {
            wakeBlocked = true
        }
        // Настройка «Включать экран» выключена — остров экран не трогает
        val wake = SettingsRepo.current.wakeForAlerts
        if (wake && alert && !alerting && !screenOn) ScreenWaker.wake(this, scope) { alerting && !wakeBlocked }
        alerting = alert
        lastScreenOn = screenOn
        setAlarmScreen(keepOn = wake && alert, turnOn = wake && alert && !wakeBlocked)
    }

    /**
     * Флаг «включить экран» у окна без Activity система выполняет при каждой его перекладке, если экран
     * погашен, — так остров будит телефон сам. «Не гасить экран» держит его включённым, пока звонит.
     */
    @Suppress("DEPRECATION")
    private fun setAlarmScreen(keepOn: Boolean, turnOn: Boolean) {
        val view = display ?: return
        val mask = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        var flags = displayParams.flags and mask.inv()
        if (keepOn) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (turnOn) flags = flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (flags == displayParams.flags) return
        displayParams.flags = flags
        runCatching { wm?.updateViewLayout(view, displayParams) }
    }

    /**
     * Высота окна острова: раскрытый остров, карточка уведомления и список истории помещаются с запасом,
     * а с крупным текстом из «Оформления» — ещё выше.
     */
    private fun windowBandDp(s: IslandSettings): Int = (WINDOW_BAND_DP * maxOf(1f, s.contentScale / 100f)).toInt()

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> onWindowsChanged()
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowState(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                clockReader?.onContentChanged(event)
                payReader?.onContentChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> clockReader?.onClicked(event)
        }
    }

    /**
     * Пока открыты «Часы» или кошелёк, службе нужны изменения их экрана и нажатия — так таймер в острове
     * появляется сразу, а «Оплачено» — как только кошелёк показал сумму. В остальное время эти события
     * выключены: их много, а остров они не касаются.
     */
    private fun setScreenEvents(on: Boolean) {
        if (on == screenEvents) return
        screenEvents = on
        val info = serviceInfo ?: return
        val extra = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED
        info.eventTypes = if (on) info.eventTypes or extra else info.eventTypes and extra.inv()
        runCatching { serviceInfo = info }
    }

    /** Пока открыт ввод ответа: клавиатуру спрятали («Назад») — ввод закрывается, как в шторке. */
    private fun onWindowsChanged() {
        checkUnlock()
        if (IslandBus.reply.value == null) return
        if (keyboardShown()) {
            keyboardSeen = true
        } else if (keyboardSeen && SystemClock.elapsedRealtime() - replySince > 1000) {
            // Первую секунду не смотрим: клавиатура другого приложения может спрятаться перед нашей
            IslandBus.reply.value = null
        }
    }

    /**
     * Экран блокировки ушёл — разблокировка показывается сразу, не дожидаясь USER_PRESENT. И наоборот:
     * если блокировка включилась позже экрана (блокировка через несколько секунд), замок появится и тогда.
     */
    private fun checkUnlock() {
        if (!keyguardShown && IslandBus.screenOn.value && !IslandBus.keyguard.value && keyguard?.isKeyguardLocked == true) {
            keyguardShown = true
            IslandBus.keyguard.value = true
            IslandBus.locked.value = keyguard?.isDeviceLocked == true
            return
        }
        if (!keyguardShown || !IslandBus.screenOn.value) return
        if (keyguard?.isKeyguardLocked == false) {
            keyguardShown = false
            events?.onUnlocked()
        }
    }

    private fun onWindowState(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        // Шторка, клавиатура и сам остров не меняют «открытое приложение»
        val cls = event.className?.toString().orEmpty()
        if (pkg == "com.android.systemui" || isKeyboard(pkg, cls)) return
        // Окно оплаты из сервисов Google (поднесли телефон к терминалу) — не «открытое приложение»
        if (pkg == PayMonitor.GMS && "tapandpay" in cls.lowercase()) {
            PayMonitor.onTapAndPay(this)
            payReader?.onTapAndPay()
            setScreenEvents(true)
            return
        }
        if (pkg == packageName && !cls.endsWith("Activity")) return
        // Открыли банк или кошелёк из другого приложения — подсказка оплаты; ушли из него — подсказка уходит
        PayMonitor.onForeground(pkg)
        if (IslandBus.foregroundPackage.value != pkg && pkg != packageName) PayMonitor.onAppOpened(this, pkg)
        // Открыт навигатор — читаем с его экрана расстояние и манёвр, ушли из него — перестаём
        navReader?.onForeground(pkg)
        // Открыты «Часы» — читаем с экрана таймер и секундомер (если их уведомления выключены)
        clockReader?.onForeground(pkg)
        // Открыт кошелёк или банк — смотрим, когда он покажет сумму оплаты
        payReader?.onForeground(pkg, cls)
        setScreenEvents(clockReader?.active == true || payReader?.active == true)
        IslandBus.foregroundPackage.value = pkg
    }

    /**
     * Окно клавиатуры: у всех клавиатур это android.inputmethodservice.SoftInputWindow, на всякий
     * случай сверяемся и со списком включённых клавиатур (обновляется раз в минуту).
     */
    private fun isKeyboard(pkg: String, cls: String): Boolean {
        if (cls.contains("inputmethod", ignoreCase = true)) return true
        val now = SystemClock.elapsedRealtime()
        if (now - imeCheckedAt > 60_000) {
            imeCheckedAt = now
            imePackages = runCatching {
                getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName }.toSet()
            }.getOrDefault(emptySet())
        }
        return pkg in imePackages
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandBus.landscape.value = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onDestroy() {
        // Остров выключили в спец. возможностях — держать его процесс больше незачем
        stopService(Intent(this, KeepAliveService::class.java))
        handler.removeCallbacks(shoot)
        handler.removeCallbacks(showAgain)
        handler.removeCallbacks(touchUpdate)
        runCatching { displays?.unregisterDisplayListener(displayListener) }
        IslandBus.egg.value = false
        setTiltEnabled(false)
        IslandBus.serviceRunning.value = false
        IslandTile.refresh(this)
        events?.stop()
        torch?.stop()
        alarm?.stop()
        calls?.stop()
        navReader?.stop()
        clockReader?.stop()
        payReader?.stop()
        ClockControl.reader = null
        alarmMonitor?.stop()
        scope.cancel()
        touch?.let { runCatching { wm?.removeView(it) } }
        display?.let { runCatching { wm?.removeView(it) } }
        touch = null
        display = null
        owner.destroy()
        super.onDestroy()
    }
}

private fun WindowManager.LayoutParams.cutoutAlways() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

/** Невидимое окно над островом: пересылает касания в окно рисования. */
@SuppressLint("ViewConstructor")
class TouchRoot(
    context: Context,
    private val target: () -> View?,
    private val onOutside: () -> Unit,
) : View(context) {
    private val origin = IntArray(2)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return true
        }
        val view = target() ?: return false
        view.getLocationOnScreen(origin)
        val copy = MotionEvent.obtain(ev)
        // rawX - x — это левый верхний угол окна касаний на экране
        copy.offsetLocation(ev.rawX - ev.x - origin[0], ev.rawY - ev.y - origin[1])
        view.dispatchTouchEvent(copy)
        copy.recycle()
        return true
    }
}

/** Высота окна острова без поправки на крупный текст, dp */
private const val WINDOW_BAND_DP = 480
private const val TOUCH_QUIET_MS = 120L
private const val TOUCH_MAX_WAIT_MS = 3000L

/** Границы до целых пикселей наружу */
private fun Rect.snapped() = Rect(floor(left), floor(top), ceil(right), ceil(bottom))

/** Что определяет, где и виден ли остров: настройки, поворот, экран блокировки. */
private data class Placement(val s: IslandSettings, val landscape: Boolean, val keyguard: Boolean, val rotation: Int)
