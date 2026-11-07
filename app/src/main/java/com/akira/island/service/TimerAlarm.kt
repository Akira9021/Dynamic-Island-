package com.akira.island.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.akira.island.core.IslandBus
import com.akira.island.core.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Доводит таймер острова до звонка и звонит, пока его не выключат. Пока экран погашен, телефон
 * засыпает и обычное ожидание стоит, поэтому к концу таймера его будит будильник системы.
 */
class TimerAlarm(private val context: Context, private val scope: CoroutineScope) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val wakeIntent = PendingIntent.getBroadcast(
        context, 0, Intent(ACTION_WAKE).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            ringIfDue()
            // Звонок включаем сразу, пока система держит телефон проснувшимся ради этого сообщения
            if (IslandBus.timer.value?.ringing == true) startRinging()
        }
    }

    private var collectJob: Job? = null
    private var finishJob: Job? = null
    private var autoStopJob: Job? = null
    private var ringtone: Ringtone? = null
    private var vibrating = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun start() {
        ContextCompat.registerReceiver(context, wakeReceiver, IntentFilter(ACTION_WAKE), ContextCompat.RECEIVER_NOT_EXPORTED)
        collectJob = scope.launch { IslandBus.timer.collect { onTimer(it) } }
    }

    fun stop() {
        collectJob?.cancel()
        finishJob?.cancel()
        cancelWake()
        runCatching { context.unregisterReceiver(wakeReceiver) }
        stopRinging()
    }

    private fun onTimer(t: TimerState?) {
        finishJob?.cancel()
        cancelWake()
        if (t == null || !t.ringing) stopRinging()
        if (t == null) return
        if (t.fromSystem) {
            // Таймер создан через системный Clock: остров не ставит свой будильник и не звонит —
            // звук и завершающий сигнал делает сама система через уведомление.
            if (t.left(SystemClock.elapsedRealtime()) > 100) return
            IslandBus.timer.value = null
            return
        }
        if (t.ringing) {
            startRinging()
        } else if (t.pausedLeft == null) {
            scheduleWake(t.endAt)
            finishJob = scope.launch {
                delay(t.left(SystemClock.elapsedRealtime()))
                ringIfDue()
            }
        }
    }

    /** Время вышло — таймер звонит. Срабатывает от ожидания или от будильника, что раньше. */
    private fun ringIfDue() {
        val t = IslandBus.timer.value ?: return
        if (t.ringing || t.pausedLeft != null || t.left(SystemClock.elapsedRealtime()) > 100) return
        IslandBus.timer.value = t.copy(pausedLeft = 0, ringing = true)
    }

    private fun scheduleWake(endAt: Long) {
        val am = alarms ?: return
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAt, wakeIntent)
            } else {
                // Точные будильники запрещены в настройках — система разбудит чуть позже, но разбудит
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAt, wakeIntent)
            }
        }
    }

    private fun cancelWake() {
        runCatching { alarms?.cancel(wakeIntent) }
    }

    private fun startRinging() {
        if (ringtone != null || vibrating) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = runCatching {
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }.getOrNull()
        vibrator?.let {
            runCatching { it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)) }
            vibrating = true
        }
        // Звонить вечно незачем: через минуту таймер гаснет сам
        autoStopJob = scope.launch {
            delay(60_000)
            if (IslandBus.timer.value?.ringing == true) IslandBus.timer.value = null
        }
    }

    private fun stopRinging() {
        autoStopJob?.cancel()
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
        if (vibrating) runCatching { vibrator?.cancel() }
        vibrating = false
    }

    private companion object {
        const val ACTION_WAKE = "com.akira.island.TIMER_WAKE"
    }
}
