package com.akira.island.core

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock

import com.akira.island.core.system.ALARM_SNOOZE_MINUTES
import com.akira.island.core.system.ClockBridge
import com.akira.island.core.system.alarmDaysFor
import com.akira.island.core.system.nextAlarmAt

/** Команды, которые капсула отдаёт системе: плеер, таймер, фонарик, громкость. */
object Controls {
    @Volatile
    var mediaController: MediaController? = null

    // Подставляет IslandService, пока он запущен
    var torchOn: () -> Unit = {}
    var torchOff: () -> Unit = {}
    var torchLevel: (Int) -> Unit = {}

    /** Системный мост к приложению «Часы» (AlarmClock intents / AlarmManager). Без него таймеры острова живут только внутри. */
    var clockBridge: ClockBridge? = null

    fun expand(id: String) {
        IslandBus.focus.value = id
        IslandBus.expanded.value = true
    }

    fun collapse() {
        IslandBus.expanded.value = false
        IslandBus.focus.value = null
        if (IslandBus.alarmDraft.value != null) {
            IslandBus.alarmDraft.value = null
            IslandBus.alarmCreateFailed.value = false
        }
    }

    // Плеер

    /** Своя кнопка плеера — «Нравится» или «Не нравится». */
    fun playerAction(action: String) {
        mediaController?.transportControls?.sendCustomAction(action, null)
    }

    fun playPause() {
        val c = mediaController ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        mediaController?.transportControls?.skipToNext()
    }

    fun prev() {
        mediaController?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        mediaController?.transportControls?.seekTo(ms)
    }

    // Таймер

    fun startTimer(minutes: Int) {
        val total = minutes.coerceIn(1, 99) * 60_000L
        IslandBus.timer.value = TimerState(total, SystemClock.elapsedRealtime() + total)
    }

    fun startTimerSeconds(seconds: Int) {
        val total = seconds * 1000L
        IslandBus.timer.value = TimerState(total, SystemClock.elapsedRealtime() + total)
    }

    /**
     * Таймер через системный Clock: создаётся в приложении «Часы» через AlarmClock.ACTION_SET_TIMER
     * (с системным звуком и завершением). Если подходящей «Часов» нет — остров ставит свой таймер
     * как fallback. Возвращает true, если таймер создан системой.
     */
    fun startSystemTimer(context: Context, seconds: Int, label: String? = null): Boolean {
        val bridge = clockBridge
        if (bridge != null) {
            // EXTRA_LENGTH di AlarmClock è in secondi, non millisecondi
            val created = bridge.createTimer(context, seconds.toLong(), label)
            if (created) {
                IslandBus.timer.value = TimerState(
                    seconds * 1000L,
                    SystemClock.elapsedRealtime() + seconds * 1000L,
                    fromSystem = true,
                )
                return true
            }
        }
        startTimerSeconds(seconds)
        return false
    }

    fun pauseTimer() {
        val t = IslandBus.timer.value ?: return
        if (t.pausedLeft == null && !t.ringing) {
            IslandBus.timer.value = t.copy(pausedLeft = t.left(SystemClock.elapsedRealtime()))
        }
    }

    fun resumeTimer() {
        val t = IslandBus.timer.value ?: return
        val left = t.pausedLeft ?: return
        if (!t.ringing) IslandBus.timer.value = t.copy(endAt = SystemClock.elapsedRealtime() + left, pausedLeft = null)
    }

    fun cancelTimer() {
        IslandBus.timer.value = null
        if (IslandBus.focus.value == "timer") collapse()
    }

    /**
     * Ferma il timer: se esiste nell'app «Часы» (fromSystem) lo rimuove dall'allarme di sistema
     * tramite ACTION_DISMISS_TIMER; altrimenti lo annulla internamente.
     */
    fun stopTimer(context: Context): Boolean {
        val t = IslandBus.timer.value ?: return false
        if (t.fromSystem) {
            val bridge = clockBridge
            if (bridge != null) {
                val ok = bridge.dismissTimer(context, t.totalMs / 1000L)
                if (ok) {
                    IslandBus.timer.value = null
                    if (IslandBus.focus.value == "timer") collapse()
                    return true
                }
            }
        }
        cancelTimer()
        return true
    }

    // Будильники

    /**
     * Ставит будильник в системных «Часах» через [ClockBridge.createAlarm] и примерно через 3 секунды
     * сверяет ближайший будильник с ожидаемым временем: не появился — показываем в карточке подсказку
     * «открыть Часы». Возвращает false сразу, если будильник создать не удалось.
     */
    fun startAlarm(context: Context, hour: Int, minutes: Int, daysMask: Int?, label: String?): Boolean {
        val bridge = clockBridge ?: return false
        IslandBus.alarmCreateFailed.value = false
        IslandBus.alarmCreating.value = true
        val clean = label?.trim()?.takeIf { it.isNotEmpty() }
        val ok = bridge.createAlarm(context, hour, minutes, alarmDaysFor(daysMask), clean)
        debugAlarm("startAlarm hour=$hour min=$minutes days=$daysMask created=$ok")
        if (!ok) {
            IslandBus.alarmCreating.value = false
            IslandBus.alarmCreateFailed.value = true
            return false
        }
        val expected = nextAlarmAt(hour, minutes, daysMask, System.currentTimeMillis())
        IslandBus.scope.launch {
            delay(ALARM_VERIFY_MS)
            val next = bridge.nextAlarm(context)
            IslandBus.nextAlarm.value = next
            val exists = next != null && kotlin.math.abs(next.triggerAt - expected) <= ALARM_VERIFY_SLACK_MS
            debugAlarm("startAlarm verify expected=$expected next=${next?.triggerAt} exists=$exists")
            IslandBus.alarmCreateFailed.value = !exists
            IslandBus.alarmCreating.value = false
            if (exists) IslandBus.alarmDraft.value = null
        }
        return true
    }

    /** Остановить звонящий будильник: intent AlarmClock, иначе кнопка «Выключить» из уведомления «Часов». */
    fun stopAlarm(context: Context, alarm: AlarmInfo?): Boolean {
        val bridge = clockBridge ?: return false
        val ok = bridge.dismissAlarm(context, alarm ?: IslandBus.nextAlarm.value, IslandBus.ringingAlarmDismiss.value)
        debugAlarm("stopAlarm ok=$ok")
        if (ok) afterAlarmRang(bridge, context)
        return ok
    }

    /** Отложить звонящий будильник на [ALARM_SNOOZE_MINUTES]: intent AlarmClock, иначе кнопка из уведомления. */
    fun snoozeAlarm(context: Context, alarm: AlarmInfo?): Boolean {
        val bridge = clockBridge ?: return false
        val ok = bridge.snoozeAlarm(
            context, alarm ?: IslandBus.nextAlarm.value, ALARM_SNOOZE_MINUTES.toInt(), IslandBus.ringingAlarmSnooze.value,
        )
        debugAlarm("snoozeAlarm ok=$ok")
        if (ok) afterAlarmRang(bridge, context)
        return ok
    }

    private fun afterAlarmRang(bridge: ClockBridge, context: Context) {
        IslandBus.ringingAlarmDismiss.value = null
        IslandBus.ringingAlarmSnooze.value = null
        IslandBus.scope.launch { IslandBus.nextAlarm.value = bridge.nextAlarm(context) }
        if (IslandBus.focus.value == "alarm") collapse()
    }

    /** Открыть список будильников «Часов» — запасной путь, если создать будильник из острова не вышло. */
    fun openClock(context: Context) {
        val bridge = clockBridge
        val pkg = bridge?.clockPackage(context)
        debugAlarm("openClock pkg=$pkg")
        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) intent.setPackage(pkg)
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        debugAlarm("openClock ok=$ok")
        if (ok) collapse()
    }

    private fun debugAlarm(msg: String) {
        android.util.Log.d("SysBridge", msg)
    }

    private const val ALARM_VERIFY_MS = 3_000L
    /** «Часы» могут округлить время срабатывания — допускаем минуту расхождения при проверке */
    private const val ALARM_VERIFY_SLACK_MS = 60_000L

    // Секундомер

    fun startStopwatch() {
        IslandBus.stopwatch.value = StopwatchState(SystemClock.elapsedRealtime(), 0)
    }

    fun toggleStopwatch() {
        val w = IslandBus.stopwatch.value ?: return
        val now = SystemClock.elapsedRealtime()
        IslandBus.stopwatch.value =
            if (w.running) StopwatchState(null, w.elapsed(now)) else StopwatchState(now, w.accumulated)
    }

    fun stopStopwatch() {
        IslandBus.stopwatch.value = null
        if (IslandBus.focus.value == "stopwatch") collapse()
    }

    // Прочее

    fun openApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
        collapse()
    }

    /** Нажать кнопку из уведомления (ответить, отклонить, открыть). */
    fun send(context: Context, intent: PendingIntent?): Boolean {
        intent ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                intent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
        }.isSuccess
    }

    /** Отправить ответ через кнопку «Ответить» из уведомления — так же, как это делает шторка. */
    fun sendReply(context: Context, action: MsgAction, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || action.inputs.isEmpty()) return false
        return runCatching {
            val inputs = action.inputs.toTypedArray()
            val results = android.os.Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, clean) }
            val fill = Intent()
            android.app.RemoteInput.addResultsToIntent(inputs, fill, results)
            action.intent.send(context, 0, fill)
        }.isSuccess
    }

    // Звонки: сначала кнопка из уведомления звонилки, иначе системный сервис звонков

    @Suppress("DEPRECATION", "MissingPermission")
    fun answerCall(context: Context, call: CallInfo) {
        if (call.key == TEST_CALL) {
            IslandBus.calls.value = IslandBus.calls.value + (TEST_CALL to call.copy(incoming = false, base = System.currentTimeMillis()))
            return
        }
        if (send(context, call.answer)) return
        val ok = runCatching { context.getSystemService(TelecomManager::class.java)!!.acceptRingingCall() }.isSuccess
        if (!ok) showCallScreen(context, call)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    fun endCall(context: Context, call: CallInfo) {
        if (call.key == TEST_CALL) {
            IslandBus.calls.value = IslandBus.calls.value - TEST_CALL
            collapse()
            return
        }
        if (send(context, if (call.incoming) call.decline else call.hangUp)) return
        val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { context.getSystemService(TelecomManager::class.java)!!.endCall() }.getOrDefault(false)
        if (!ok) showCallScreen(context, call)
    }

    /**
     * Ответить или сбросить из острова не вышло — скорее всего, не дали разрешение «Звонки». Тогда открываем
     * экран звонка телефона: там кнопки работают всегда. Нет и его — звонилку.
     */
    @Suppress("MissingPermission")
    private fun showCallScreen(context: Context, call: CallInfo) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (telecom != null && runCatching { telecom.showInCallScreen(false) }.isSuccess) return
        if (send(context, call.open)) return
        val dialer = runCatching { telecom?.defaultDialerPackage }.getOrNull()?.takeIf { it.isNotEmpty() } ?: call.pkg
        if (dialer.isNotEmpty()) openApp(context, dialer)
    }

    /** Смахнули входящий звонок вверх — он уходит из острова (звонок продолжается, ответить можно в звонилке). */
    fun hideIncomingCall() {
        IslandBus.callHidden.value = true
    }

    /** Скриншот без острова — остров на миг прячется, снимок делает Android (9+) */
    fun screenshot() {
        IslandBus.commands.tryEmit(ServiceCommand.SCREENSHOT)
    }

    fun lockScreen() {
        IslandBus.commands.tryEmit(ServiceCommand.LOCK)
    }

    /** Выключить службу острова, например на время банковского приложения. Включить её снова можно только в настройках */
    fun turnOffIsland() {
        IslandBus.commands.tryEmit(ServiceCommand.TURN_OFF)
    }

    fun openCamera(context: Context) {
        runCatching {
            context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Suppress("MissingPermission")
    fun openCall(context: Context, call: CallInfo) {
        if (call.key == TEST_CALL) return
        if (send(context, call.open)) return
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (runCatching { telecom?.showInCallScreen(false) }.isSuccess && telecom != null) return
        if (call.pkg.isNotEmpty()) openApp(context, call.pkg)
    }

    /** Пробный входящий звонок из приложения: кнопки работают, через 20 секунд он пропадает сам. */
    fun showTestCall() {
        val now = System.currentTimeMillis()
        IslandBus.calls.value = IslandBus.calls.value +
            (TEST_CALL to CallInfo(TEST_CALL, "", tr("Анна", "Anna"), null, true, now, now, null, null, null, null))
        IslandBus.scope.launch {
            delay(20_000)
            IslandBus.calls.value = IslandBus.calls.value - TEST_CALL
        }
    }

    const val TEST_CALL = "test-call"

    /** Пробная навигация: широкая плашка, потом капсула; через 10 секунд пропадает сама, даже если выйти из приложения. */
    fun showTestLive(context: Context) {
        val live = LiveInfo(
            key = TEST_LIVE, pkg = context.packageName, kind = LiveKind.NAV,
            title = tr("300 м", "300 m"), text = tr("Поверните направо на Тверскую улицу", "Turn right onto Main Street"), sub = "",
            icon = com.akira.island.service.NotificationParser.icon(context, context.packageName), art = null,
            postTime = System.currentTimeMillis(), open = null,
            distance = tr("300 м", "300 m"), eta = tr("Прибытие в 18:42 · 12 мин · 4,2 км", "Arrive 18:42 · 12 min · 4.2 km"), turn = Turn.RIGHT,
            expiresAt = System.currentTimeMillis() + TEST_LIVE_MS,
        )
        IslandBus.live.value = IslandBus.live.value + (TEST_LIVE to live)
        IslandBus.post(com.akira.island.service.LiveParser.banner(context, live))
        IslandBus.scope.launch {
            delay(TEST_LIVE_MS)
            IslandBus.live.value = IslandBus.live.value - TEST_LIVE
        }
    }

    private const val TEST_LIVE_MS = 10_000L
    const val TEST_LIVE = "test-live"

    /** Куда выводится звук: системная панель громкости с выбором устройства. */
    fun openOutput(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_VOLUME)
        } else {
            Intent(Settings.ACTION_SOUND_SETTINGS)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        collapse()
    }

}
