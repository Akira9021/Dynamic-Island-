package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/** Приложения «Часы»: те, что умеют ставить таймер, плюс известные по пакету. */
object ClockApps {
    private val known = setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
        "com.oplus.alarmclock",
        "com.huawei.deskclock",
        "com.motorola.timeweatherwidget",
    )
    private var found: Set<String>? = null

    fun packages(context: Context): Set<String> = found ?: (known + runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SET_TIMER), 0).map { it.activityInfo.packageName }
    }.getOrDefault(emptyList()) - context.packageName).also { found = it }

    fun isClock(context: Context, pkg: String) = pkg in packages(context)

    private var alarmApps: Set<String>? = null

    /** «Часы» и будильники (Alarmy, Sleep as Android…) — те, кто ставит будильники или показывает их список */
    fun isAlarmApp(context: Context, pkg: String): Boolean = pkg in (alarmApps ?: (packages(context) +
        listOf(AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_ALARMS).flatMap { action ->
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(Intent(action), 0).map { it.activityInfo.packageName }
            }.getOrDefault(emptyList())
        }).also { alarmApps = it })
}

/** Что делает кнопка «Часов». */
enum class ClockAction { START, PAUSE, RESET }

/** Подписи кнопок «Часов» — для нажатий на экране, кнопок уведомления и поиска кнопки, которую нажмёт остров. */
object ClockWords {
    private val start = listOf("старт", "start", "начать", "пуск", "запуст", "продолж", "возобнов", "resume")
    private val pause = setOf("пауза", "pause", "стоп", "stop")
    private val reset = listOf("сброс", "reset", "удал", "delete", "отмен", "cancel", "очист", "clear")
    private val nonLetters = Regex("[^\\p{L}]+")

    /** Кнопки на экране «Часов», которые остров ищет и нажимает сам, — подпись целиком. */
    val labels = mapOf(
        ClockAction.START to listOf("Старт", "Начать", "Продолжить", "Пуск", "Возобновить", "Start", "Resume"),
        ClockAction.PAUSE to listOf("Пауза", "Стоп", "Pause", "Stop"),
        ClockAction.RESET to listOf("Сбросить", "Сброс", "Удалить", "Reset", "Delete"),
    )

    /** По началу слова; «стоп» — только целым словом, иначе совпадёт «Stopwatch». */
    fun action(label: CharSequence?): ClockAction? {
        val words = label?.toString()?.lowercase()?.split(nonLetters)?.filter { it.isNotEmpty() } ?: return null
        return when {
            words.any { w -> reset.any { w.startsWith(it) } } -> ClockAction.RESET
            words.any { w -> w in pause || w.startsWith("приостанов") } -> ClockAction.PAUSE
            words.any { w -> start.any { w.startsWith(it) } } -> ClockAction.START
            else -> null
        }
    }

    /** Кнопки из уведомления «Часов»: пауза, продолжить, сброс. */
    fun buttons(actions: Array<android.app.Notification.Action>?): Map<ClockAction, android.app.PendingIntent> {
        val out = HashMap<ClockAction, android.app.PendingIntent>()
        actions?.forEach { a ->
            val intent = a.actionIntent ?: return@forEach
            val kind = action(a.title) ?: return@forEach
            if (kind !in out) out[kind] = intent
        }
        return out
    }
}

/** Разбор надписей экрана «Часов». Отдельно от Android, чтобы можно было проверить на примерах. */
object ClockScreenParser {
    /** «4:59», «00:04:59», «01:05,23», «1:02:03.4» */
    private val time = Regex("""^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?$""")

    private val anywhere = Regex("""(?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?""")

    /** Время где-нибудь в тексте: «Осталось 4:59» → 4:59. */
    fun find(text: CharSequence?): Long? = text?.let { anywhere.find(it)?.value }?.let { parse(it) }

    fun parse(text: String): Long? {
        val m = time.matchEntire(text.filterNot { it.isWhitespace() }) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        if (sec >= 60 || (m.groupValues[1].isNotEmpty() && min >= 60)) return null
        val frac = m.groupValues[4].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toLong() ?: 0L
        return ((h * 60 + min) * 60 + sec) * 1000 + frac
    }
}

/**
 * Таймер и секундомер приложения «Часы» — с его экрана. Если у «Часов» выключены уведомления, Android
 * не отдаёт их никому, и узнать о таймере можно только так. Пока «Часы» открыты, остров запоминает
 * надписи со временем и обновляет их четыре раза в секунду, а ещё получает изменения экрана и нажатия:
 * «Старт» на вкладке «Таймер» — таймер сразу в острове, «Пауза» — замирает, «Сброс» — исчезает.
 * Без нажатия таймер узнаётся по тому, что время идёт: убывает — таймер, растёт — секундомер.
 * Когда приложение закрывают, остров ведёт отсчёт сам. Другие приложения так не читаются.
 */
class ClockScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private enum class Kind { TIMER, STOPWATCH }

    /** Надпись со временем на экране; [key] — id вида или место, [height] — главная надпись крупнее */
    private class Display(val key: String, val node: AccessibilityNodeInfo, val height: Int)

    /** Что последним видели в надписи: значение, когда оно сменилось и сколько смен подряд шли «как часы» */
    private class Seen(var value: Long, var changedAt: Long, var kind: Kind? = null, var streak: Int = 0, var fine: Boolean = false)

    /**
     * Что показывает остров: [base] — настенное время конца таймера или начала секундомера,
     * [pausedAt] — когда встал на паузу (через [INACTIVE_MS] на паузе плашка убирается)
     */
    private class Track(val key: String, val base: Long, val pausedMs: Long?, val pausedAt: Long = 0L)

    private fun pausedTrack(t: Track, value: Long) = Track(t.key, t.base, value, SystemClock.elapsedRealtime())

    val active: Boolean get() = current != null

    private var current: String? = null
    private var loop: Job? = null
    private var endJob: Job? = null
    private var displays: List<Display> = emptyList()
    private var tab: Kind? = null
    private var scannedAt = 0L
    private var lastTick = 0L
    private var needRescan = true
    private val seen = HashMap<String, Seen>()
    private var hint: ClockAction? = null
    /** К чему относится нажатие: открытая вкладка или то, на что нажал сам остров */
    private var hintKind: Kind? = null
    private var hintAt = 0L
    private var timer: Track? = null
    private var stopwatch: Track? = null
    private var timerMisses = 0
    private var stopwatchMisses = 0
    private val labels = HashMap<String, String>()

    /** Кнопку нажимает сам остров: какую, для таймера или секундомера, до какого времени ждать и открыл ли «Часы» он сам */
    private class Pending(val action: ClockAction, val timer: Boolean, val until: Long, val opened: Boolean)
    private var pending: Pending? = null
    /** Когда открылись «Часы»: столько остров уже смотрит на их цифры и знает, идут ли они */
    private var openedAt = 0L
    /** Когда остров сам нажал кнопку «Часов»: их ответное «нажали» не должно стереть то, что он знает */
    private var ownClickAt = 0L
    private var cleanupJob: Job? = null

    fun onForeground(pkg: String) {
        if (!ClockApps.isClock(service, pkg)) {
            // Уходя из «Часов», читаем их в последний раз: окно ещё на экране, и таймер, запущенный
            // перед самым уходом, не потеряется
            val left = current
            if (left != null && SettingsRepo.current.cardTimer) {
                runCatching { tick() }
                // Ушли с вкладки, где его больше нет (сбросили или удалили), — плашка не нужна
                if (tab == Kind.TIMER && displays.none { it.key == timer?.key }) timer = null
                if (tab == Kind.STOPWATCH && displays.none { it.key == stopwatch?.key }) stopwatch = null
                publish(left)
            }
            stopReading()
            return
        }
        if (pkg == current) return
        stopReading()
        current = pkg
        openedAt = SystemClock.elapsedRealtime()
        loop = scope.launch {
            while (isActive) {
                // Экран погашен — читать нечего, спим, пока его не включат
                if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
                if (SettingsRepo.current.cardTimer) runCatching { tick() }
                delay(250)
            }
        }
    }

    /** Экран «Часов» изменился — читаем сразу, не дожидаясь следующей четверти секунды. */
    fun onContentChanged(event: AccessibilityEvent) {
        if (event.packageName?.toString() != current) return
        if (event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0) needRescan = true
        if (SystemClock.elapsedRealtime() - lastTick >= 80) runCatching { tick() }
    }

    /** Нажали кнопку в «Часах»: «Старт», «Пауза», «Сброс» — экран вот-вот поменяется, читаем его несколько раз. */
    fun onClicked(event: AccessibilityEvent) {
        if (event.packageName?.toString() != current) return
        // Это эхо нажатия самого острова — что нажато, он уже знает (подпись в эхе бывает пустой)
        if (SystemClock.elapsedRealtime() - ownClickAt < 1_500) return
        val label = event.text.joinToString(" ") + " " + (event.contentDescription ?: "")
        // Подпись не узнали — смотрим на id кнопки; круглая кнопка Google Часов то запускает, то ставит на паузу
        val id = runCatching { event.source?.viewIdResourceName }.getOrNull().orEmpty().lowercase()
        val running = if (tab == Kind.STOPWATCH) stopwatch?.pausedMs == null && stopwatch != null else timer?.pausedMs == null && timer != null
        val action = ClockWords.action(label) ?: when {
            "reset" in id || "delete" in id -> ClockAction.RESET
            "pause" in id || ("stop" in id && "stopwatch" !in id) -> ClockAction.PAUSE
            "start" in id || "resume" in id || "play" in id -> ClockAction.START
            "fab" in id -> if (running) ClockAction.PAUSE else ClockAction.START
            else -> null
        }
        // Нажали что-то другое (вкладку, «Круг») — то, что известно о прошлом нажатии, не трогаем
        if (action != null) {
            hintKind = tab
            hint = action
            hintAt = SystemClock.elapsedRealtime()
        }
        // После нажатия экран меняется — перечитываем его сразу, без ожидания
        needRescan = true
        scannedAt = 0L
        scope.launch {
            for (wait in longArrayOf(60, 140, 250, 350)) {
                delay(wait)
                runCatching { tick() }
            }
        }
    }

    /**
     * Кнопка острова, когда нажать кнопку уведомления «Часов» нельзя (уведомления выключены или такой кнопки
     * в нём нет): остров сразу показывает результат, открывает «Часы», сам нажимает нужную кнопку и, если
     * открывал их сам, возвращает вас обратно. [own] — таймер прочитан с экрана, его ведёт этот класс.
     */
    fun control(pkg: String, isTimer: Boolean, action: ClockAction, own: Boolean) {
        if (own) {
            val wall = System.currentTimeMillis()
            val next = (if (isTimer) timer else stopwatch)?.let { apply(it, isTimer, action, wall) }
            if (isTimer) timer = next else stopwatch = next
            publish(pkg)
        }
        val opened = current != pkg
        pending = Pending(action, isTimer, SystemClock.elapsedRealtime() + 4_000, opened)
        if (!opened) {
            scope.launch {
                delay(60)
                runCatching { tick() }
            }
            return
        }
        val intents = listOfNotNull(
            if (isTimer) Intent(AlarmClock.ACTION_SHOW_TIMERS).setPackage(pkg) else null,
            service.packageManager.getLaunchIntentForPackage(pkg),
        )
        val started = intents.any { intent ->
            runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!started) pending = null
    }

    private fun apply(t: Track, isTimer: Boolean, action: ClockAction, wall: Long): Track? {
        val value = t.pausedMs ?: if (isTimer) (t.base - wall).coerceAtLeast(0) else wall - t.base
        return when (action) {
            ClockAction.PAUSE -> pausedTrack(t, value)
            ClockAction.START -> Track(t.key, if (isTimer) wall + value else wall - value, null)
            ClockAction.RESET -> null
        }
    }

    /**
     * «Часы» открылись — переходим на нужную вкладку и нажимаем кнопку. Кнопка с подписью («Пауза») нажимается
     * сразу; круглая кнопка-переключатель — только если таймер сейчас в другом состоянии: сначала остров
     * смотрит, идут ли цифры. Сброса не видно, пока идёт, — сначала пауза. Открыли «Часы» сами — потом назад.
     */
    private fun performPending() {
        val p = pending ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > p.until) {
            pending = null
            return
        }
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != current) return
        val want = if (p.timer) Kind.TIMER else Kind.STOPWATCH
        if (tab != null && tab != want) {
            if (clickTab(root, if (p.timer) TIMER_TABS else STOPWATCH_TABS)) needRescan = true
            return
        }
        val main = displays.maxByOrNull { it.height }
        val s = main?.let { seen[it.key] }
        val running = s != null && s.kind != null && now - s.changedAt < 1_200
        val known = running || now - openedAt > 1_300
        when (p.action) {
            ClockAction.PAUSE, ClockAction.START -> {
                val button = findButton(root, p.action) ?: return
                if (button.toggle) {
                    if (!known) return
                    val needed = if (p.action == ClockAction.PAUSE) running else !running
                    if (needed && !click(button.node)) return
                } else if (!click(button.node)) {
                    return
                }
            }
            ClockAction.RESET -> {
                val button = findButton(root, ClockAction.RESET)
                if (button == null) {
                    if (known && running) findButton(root, ClockAction.PAUSE)?.let { click(it.node) }
                    return
                }
                if (!click(button.node)) return
            }
        }
        // Что нажали, знаем сами — не ждём, придёт ли от «Часов» сообщение о нажатии (Compose его не шлёт)
        hint = p.action
        hintKind = want
        hintAt = now
        ownClickAt = now
        pending = null
        if (p.opened) {
            scope.launch {
                // Даём «Часам» встать и острову увидеть это, потом возвращаемся
                delay(600)
                runCatching { tick() }
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
    }

    /** Кнопка на экране «Часов»; [toggle] — одна кнопка и для «Старт», и для «Пауза» (круглая в Google Часах). */
    private class Button(val node: AccessibilityNodeInfo, val toggle: Boolean)

    /** Ищет кнопку по подписи (точнее), иначе по id вида. Вкладки и надписи со временем не трогает. */
    private fun findButton(root: AccessibilityNodeInfo, action: ClockAction): Button? {
        var byLabel: AccessibilityNodeInfo? = null
        var byId: AccessibilityNodeInfo? = null
        var byIdToggle = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (byLabel != null || depth > 40) return
            if (n.isVisibleToUser && !n.isSelected) {
                val label = listOfNotNull(n.text, n.contentDescription).joinToString(" ").trim()
                if (label.isNotEmpty() && label.length <= 40 && ClockScreenParser.parse(label) == null &&
                    ClockWords.action(label) == action
                ) {
                    byLabel = n
                    return
                }
                val id = n.viewIdResourceName.orEmpty().lowercase()
                if (byId == null && id.isNotEmpty()) {
                    val toggle = "fab" in id || ("play" in id && "pause" in id) || ("start" in id && "stop" in id)
                    val match = when (action) {
                        ClockAction.PAUSE -> toggle || "pause" in id || ("stop" in id && "stopwatch" !in id)
                        ClockAction.START -> toggle || "start" in id || "resume" in id || "play" in id
                        ClockAction.RESET -> "reset" in id || "delete" in id || "clear" in id
                    }
                    if (match) {
                        byId = n
                        byIdToggle = toggle
                    }
                }
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        byLabel?.let { return Button(it, false) }
        return byId?.let { Button(it, byIdToggle) }
    }

    /** Вкладка «Таймер»/«Секундомер» по подписи целиком. */
    private fun clickTab(root: AccessibilityNodeInfo, labels: List<String>): Boolean {
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (found != null || depth > 40) return
            val label = (n.text ?: n.contentDescription)?.toString()?.trim()
            if (n.isVisibleToUser && labels.any { it.equals(label, ignoreCase = true) }) {
                found = n
                return
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return found?.let { click(it) } ?: false
    }

    /** Нажать сам вид или ближайшего нажимаемого родителя. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var up = 0
        while (target != null && !target.isClickable && up < 4) {
            target = target.parent
            up++
        }
        return target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Таймер или секундомер дольше [INACTIVE_MS] на паузе — неактивен, плашка убирается. */
    private fun dropInactive(now: Long) {
        if (timer?.let { it.pausedMs != null && now - it.pausedAt > INACTIVE_MS } == true) timer = null
        if (stopwatch?.let { it.pausedMs != null && now - it.pausedAt > INACTIVE_MS } == true) stopwatch = null
    }

    /** Уведомление «Часов» снова приходит — остров берёт таймер из него, прочитанный с экрана больше не нужен. */
    fun onNotificationChrono(pkg: String) {
        if (timer == null && stopwatch == null) return
        timer = null
        stopwatch = null
        publish(pkg)
    }

    /** Ушли из «Часов»: читать перестаём, но таймер и секундомер остров ведёт дальше сам. */
    fun stopReading() {
        loop?.cancel()
        loop = null
        current = null
        displays = emptyList()
        seen.clear()
        hint = null
        needRescan = true
        scannedAt = 0L
    }

    fun stop() {
        stopReading()
        endJob?.cancel()
        timer = null
        stopwatch = null
        IslandBus.chrono.value = IslandBus.chrono.value - TIMER_KEY - STOPWATCH_KEY
    }

    private fun tick() {
        val pkg = current ?: return
        val now = SystemClock.elapsedRealtime()
        lastTick = now
        // Полный обход экрана дорог: не чаще двух раз в секунду, а если ничего не менялось — раз в 3 секунды
        if ((needRescan && now - scannedAt > 500) || now - scannedAt > 3_000) rescan(pkg, now)
        val wall = System.currentTimeMillis()
        val hinted = hint?.takeIf { now - hintAt < HINT_MS }

        var bestTimer: Pair<Display, Long>? = null
        var bestStopwatch: Pair<Display, Long>? = null
        for (d in displays) {
            val alive = runCatching { d.node.refresh() && d.node.isVisibleToUser }.getOrDefault(false)
            if (!alive) {
                needRescan = true
                continue
            }
            val text = (d.node.text ?: d.node.contentDescription)?.toString() ?: continue
            if (isTimeOfDay(text, wall)) continue
            val v = ClockScreenParser.parse(text) ?: continue
            // Секундомер с долями секунды меняется постоянно — его паузу видно почти сразу
            seen[d.key]?.fine = text.contains('.') || text.contains(',')
            when (observe(d.key, v, now, hinted)) {
                Kind.TIMER -> if (d.height > (bestTimer?.first?.height ?: -1)) bestTimer = d to v
                Kind.STOPWATCH -> if (d.height > (bestStopwatch?.first?.height ?: -1)) bestStopwatch = d to v
                null -> Unit
            }
        }
        bestTimer?.let { (d, v) -> timer = running(timer, d.key, wall + v) }
        bestStopwatch?.let { (d, v) -> stopwatch = running(stopwatch, d.key, wall - v) }

        // Нажали «Старт» на своей вкладке — показываем сразу, не дожидаясь, пока цифры сдвинутся
        val main = displays.maxByOrNull { it.height }
        val mainValue = main?.let { seen[it.key]?.value }
        if (hinted == ClockAction.START && main != null && mainValue != null) {
            val t = timer
            val w = stopwatch
            if (hintKind == Kind.TIMER && bestTimer == null && mainValue > 0 && (t == null || t.pausedMs != null)) {
                timer = Track(main.key, wall + mainValue, null)
                seen[main.key]?.changedAt = now
            }
            if (hintKind == Kind.STOPWATCH && bestStopwatch == null && (w == null || w.pausedMs != null)) {
                stopwatch = Track(main.key, wall - mainValue, null)
                seen[main.key]?.changedAt = now
            }
        }
        if (hinted == ClockAction.PAUSE && main != null && mainValue != null) {
            // Встаём на то время, что показывают «Часы», — оно точнее того, что остров успел насчитать сам
            if (hintKind == Kind.TIMER) timer?.let { timer = Track(it.key, it.base, mainValue, it.pausedAt.takeIf { at -> at > 0 } ?: now) }
            if (hintKind == Kind.STOPWATCH) stopwatch?.let { stopwatch = Track(it.key, it.base, mainValue, it.pausedAt.takeIf { at -> at > 0 } ?: now) }
        }
        if (hinted == ClockAction.RESET) {
            if (hintKind == Kind.TIMER) timer = null
            if (hintKind == Kind.STOPWATCH) stopwatch = null
        }

        // Сразу после «Старт» цифры ещё не успели сдвинуться — это не пауза
        if (hinted != ClockAction.START) {
            timer = pauseIfStill(timer, now)
            stopwatch = pauseIfStill(stopwatch, now)
        }
        dropInactive(now)
        publish(pkg)
        performPending()
    }

    /**
     * Новое значение надписи. Возвращает, что это — идущий таймер или секундомер, — когда это уже ясно:
     * две смены подряд с ходом часов, или одна, если открыта своя вкладка или только что нажали «Старт».
     */
    private fun observe(key: String, v: Long, now: Long, hinted: ClockAction?): Kind? {
        val s = seen[key]
        if (s == null) {
            seen[key] = Seen(v, now)
            return null
        }
        if (v == s.value) return null
        val dv = v - s.value
        val since = now - s.changedAt
        val paused = listOfNotNull(timer, stopwatch).firstOrNull { it.key == key && it.pausedMs != null }
        val kind = when {
            dv < 0 && -dv <= since + 1_100 && since <= -dv + 1_300 -> Kind.TIMER
            dv > 0 && dv <= since + 1_100 && since <= dv + 1_300 -> Kind.STOPWATCH
            // Сняли с паузы: после долгой стоянки цифры снова сдвинулись на шаг
            paused != null && kotlin.math.abs(dv) <= 1_500 -> if (dv < 0) Kind.TIMER else Kind.STOPWATCH
            else -> null
        }
        s.streak = if (kind != null && kind == s.kind) s.streak + 1 else if (kind != null) 1 else 0
        s.kind = kind
        s.value = v
        s.changedAt = now
        // Только что нажали «Пауза»: цифры могли сдвинуться ещё на шаг, пока «Часы» не успели встать, — это не ход
        if (hinted == ClockAction.PAUSE) return null
        if (kind == null) {
            // Скачок: таймер сбросили к исходному времени или секундомер обнулили
            if (timer?.key == key && dv > 1_500) timer = null
            if (v == 0L && stopwatch?.key == key) stopwatch = null
            return null
        }
        if (v == 0L) {
            if (timer?.key == key) timer = null
            return null
        }
        val sure = s.streak >= 2 || tab == kind || hinted == ClockAction.START || paused != null
        return if (sure) kind else null
    }

    /** Цифры показанного таймера или секундомера стоят на месте больше полутора секунд — пауза. */
    private fun pauseIfStill(track: Track?, now: Long): Track? {
        track ?: return null
        if (track.pausedMs != null) return track
        val d = displays.firstOrNull { it.key == track.key } ?: return track
        val s = seen[d.key] ?: return track
        val visible = runCatching { d.node.isVisibleToUser }.getOrDefault(false)
        val still = if (s.fine) 450 else 1_500
        return if (visible && now - s.changedAt > still) pausedTrack(track, s.value) else track
    }

    /**
     * Идёт. Цифры на экране округлены до секунды, поэтому новый расчёт гуляет на ±1 с — пока он близок
     * к прежнему, оставляем прежний, иначе время в острове дёргалось бы. Сильно сдвинулось (+1:00) — берём новое.
     */
    private fun running(old: Track?, key: String, base: Long): Track = when {
        old == null || old.pausedMs != null || abs(old.base - base) >= 1_500 -> Track(key, base, null)
        old.key == key -> old
        else -> Track(key, old.base, null)
    }

    /** Все надписи со временем на экране «Часов» и открытая вкладка. Дорого — только по необходимости. */
    private fun rescan(pkg: String, now: Long) {
        scannedAt = now
        needRescan = false
        val root = service.rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg } ?: windowRoot(pkg) ?: return
        val found = ArrayList<Display>()
        val tabs = arrayOfNulls<Kind>(1)
        collect(root, found, tabs, 0, HashMap())
        tab = tabs[0]
        @Suppress("DEPRECATION")
        displays.forEach { runCatching { it.node.recycle() } }
        displays = found.sortedByDescending { it.height }.take(6)
        // Пропал с открытой вкладки «Таймер»/«Секундомер» — его удалили
        timerMisses = if (tab == Kind.TIMER && timer != null && displays.none { it.key == timer?.key }) timerMisses + 1 else 0
        stopwatchMisses = if (tab == Kind.STOPWATCH && stopwatch != null && displays.none { it.key == stopwatch?.key }) stopwatchMisses + 1 else 0
        if (timerMisses >= 2) timer = null
        if (stopwatchMisses >= 2) stopwatch = null
    }

    /** Окно «Часов», даже если поверх уже открывается другое приложение. */
    private fun windowRoot(pkg: String): AccessibilityNodeInfo? = runCatching {
        service.windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == pkg } }
    }.getOrNull()

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Display>, tabs: Array<Kind?>, depth: Int, ids: HashMap<String, Int>) {
        if (out.size > 20 || depth > 40) return
        val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        var keep = false
        if (text.isNotEmpty() && node.isVisibleToUser) {
            if (ClockScreenParser.parse(text) != null) {
                val b = Rect()
                node.getBoundsInScreen(b)
                // У нескольких таймеров в списке id одинаковый — различаем по порядку
                val id = node.viewIdResourceName?.takeIf { it.isNotEmpty() } ?: "@${b.left},${b.top}"
                val n = (ids[id] ?: 0) + 1
                ids[id] = n
                out += Display("$id#$n", node, b.height())
                keep = true
            }
            if (node.isSelected && tabs[0] == null) {
                val t = text.lowercase()
                tabs[0] = when {
                    "секундомер" in t || "stopwatch" in t -> Kind.STOPWATCH
                    "таймер" in t || "timer" in t -> Kind.TIMER
                    else -> null
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, tabs, depth + 1, ids)
        }
        if (!keep && depth > 0) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /** Время суток на вкладке «Часы» и в мировом времени тоже похоже на «ч:мм» — его не путаем с таймером. */
    private fun isTimeOfDay(text: String, wall: Long): Boolean {
        val t = text.filterNot { it.isWhitespace() }
        return (-1..1).any { m ->
            val c = Calendar.getInstance().apply { timeInMillis = wall + m * 60_000L }
            val h24 = c.get(Calendar.HOUR_OF_DAY)
            val h12 = (h24 + 11) % 12 + 1
            val mm = "%02d".format(c.get(Calendar.MINUTE))
            t.startsWith("$h24:$mm") || t.startsWith("%02d:$mm".format(h24)) || t.startsWith("$h12:$mm") || t.startsWith("%02d:$mm".format(h12))
        } && t.length <= 8 && seenAsClock(t)
    }

    /** «Ч:мм» без долей секунды — формат часов; у секундомера всегда есть доли или он ещё не дошёл до часа. */
    private fun seenAsClock(t: String): Boolean = !t.contains('.') && !t.contains(',')

    private fun publish(pkg: String) {
        // Если уведомления «Часов» включены, остров берёт таймер из них — второй не нужен
        val fromNotification = IslandBus.chrono.value.any { (k, v) -> v.pkg == pkg && k != TIMER_KEY && k != STOPWATCH_KEY }
        val map = IslandBus.chrono.value.toMutableMap()
        val label = labels.getOrPut(pkg) { NotificationParser.appLabel(service, pkg) }
        val icon = NotificationParser.icon(service, pkg)
        val t = timer?.takeUnless { fromNotification }
        val w = stopwatch?.takeUnless { fromNotification }
        if (t != null) {
            val old = map[TIMER_KEY]
            map[TIMER_KEY] = ChronoInfo(TIMER_KEY, pkg, trf("Таймер · {0}", "Timer · {0}", label), icon, t.base, true,
                old?.postTime ?: System.currentTimeMillis(), pausedMs = t.pausedMs)
        } else {
            map.remove(TIMER_KEY)
        }
        if (w != null) {
            val old = map[STOPWATCH_KEY]
            map[STOPWATCH_KEY] = ChronoInfo(STOPWATCH_KEY, pkg, trf("Секундомер · {0}", "Stopwatch · {0}", label), icon, w.base, false,
                old?.postTime ?: System.currentTimeMillis(), pausedMs = w.pausedMs)
        } else {
            map.remove(STOPWATCH_KEY)
        }
        if (map != IslandBus.chrono.value) IslandBus.chrono.value = map
        scheduleEnd()
        scheduleCleanup(pkg)
    }

    /** Убрать неактивный (на паузе) таймер и после ухода из «Часов», когда чтение уже не идёт. */
    private fun scheduleCleanup(pkg: String) {
        cleanupJob?.cancel()
        val first = listOfNotNull(timer, stopwatch).filter { it.pausedMs != null }.minOfOrNull { it.pausedAt } ?: return
        cleanupJob = scope.launch {
            delay((first + INACTIVE_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 50)
            dropInactive(SystemClock.elapsedRealtime())
            publish(pkg)
        }
    }

    /** Таймер дошёл до нуля — дальше звонят сами «Часы», а остров его убирает. */
    private fun scheduleEnd() {
        endJob?.cancel()
        val t = timer ?: return
        if (t.pausedMs != null) return
        endJob = scope.launch {
            delay((t.base - System.currentTimeMillis()).coerceAtLeast(0) + 1_500)
            if (timer === t) {
                timer = null
                IslandBus.chrono.value = IslandBus.chrono.value - TIMER_KEY
            }
        }
    }

    companion object {
        /** Ключи таймера и секундомера, прочитанных с экрана, — в отличие от ключей уведомлений */
        const val KEY_PREFIX = "clock-screen:"
        /** Сколько после нажатия «Старт», «Пауза» или «Сброс» остров верит нажатию больше, чем цифрам */
        private const val HINT_MS = 2_000L
        /** Сколько неактивный (на паузе) таймер или секундомер ещё виден в острове — как любое событие («Показ событий») */
        val INACTIVE_MS: Long get() = SettingsRepo.current.noticeDurationMs.toLong()
        private const val TIMER_KEY = KEY_PREFIX + "timer"
        private const val STOPWATCH_KEY = KEY_PREFIX + "stopwatch"
        private val TIMER_TABS = listOf("Таймер", "Timer")
        private val STOPWATCH_TABS = listOf("Секундомер", "Stopwatch")
    }
}

/**
 * Кнопки острова для таймера и секундомера «Часов» — те же, что у своих: пауза или продолжить и сброс.
 * Есть кнопка в уведомлении «Часов» — остров нажимает её, не открывая приложение. Нет (уведомления
 * выключены) — нажимает кнопку на экране «Часов» сам. Результат в острове виден сразу.
 */
object ClockControl {
    var reader: ClockScreenReader? = null

    fun toggle(context: Context, c: ChronoInfo) = act(context, c, if (c.pausedMs != null) ClockAction.START else ClockAction.PAUSE)

    fun reset(context: Context, c: ChronoInfo) = act(context, c, ClockAction.RESET)

    private fun act(context: Context, c: ChronoInfo, action: ClockAction) {
        val own = c.key.startsWith(ClockScreenReader.KEY_PREFIX)
        val intent = when (action) {
            ClockAction.START -> c.resume
            ClockAction.PAUSE -> c.pause
            ClockAction.RESET -> c.reset
        }
        if (!own && intent != null && SettingsRepo.current.clockAutoTap && Controls.send(context, intent)) {
            optimistic(c, action)
            return
        }
        val r = reader
        // «Кнопки для «Часов»» выключены — кнопки острова просто открывают приложение
        if (r == null || !SettingsRepo.current.clockAutoTap || !ClockApps.isClock(context, c.pkg)) {
            Controls.openApp(context, c.pkg)
            return
        }
        if (!own) optimistic(c, action)
        r.control(c.pkg, c.countDown, action, own)
    }

    /** Показать результат нажатия сразу; новое уведомление «Часов» подтвердит его через мгновение. */
    private fun optimistic(c: ChronoInfo, action: ClockAction) {
        val now = System.currentTimeMillis()
        val value = c.elapsed(now)
        val next = when (action) {
            ClockAction.PAUSE -> c.copy(pausedMs = value)
            ClockAction.START -> c.copy(pausedMs = null, base = if (c.countDown) now + value else now - value)
            ClockAction.RESET -> null
        }
        IslandBus.chrono.value = if (next == null) IslandBus.chrono.value - c.key else IslandBus.chrono.value + (c.key to next)
        if (next?.pausedMs != null) dropPausedLater(c.key)
    }

    /** Уведомления «Часов» на паузе, которые остров уже убрал: ключ → застывшее время */
    private val dismissed = HashMap<String, Long>()

    /**
     * Таймер или секундомер из уведомления встал на паузу — через несколько секунд плашка убирается:
     * неактивный таймер в острове не висит. То же уведомление на той же паузе обратно не показывается.
     */
    fun dropPausedLater(key: String) {
        IslandBus.scope.launch {
            delay(ClockScreenReader.INACTIVE_MS)
            val c = IslandBus.chrono.value[key] ?: return@launch
            val paused = c.pausedMs ?: return@launch
            dismissed[key] = paused
            IslandBus.chrono.value = IslandBus.chrono.value - key
        }
    }

    /** Это уведомление на этой паузе остров уже убирал. */
    fun wasDismissed(key: String, pausedMs: Long?): Boolean {
        if (pausedMs == null) {
            dismissed.remove(key)
            return false
        }
        return dismissed[key] == pausedMs
    }

    fun forget(key: String) {
        dismissed.remove(key)
    }
}
