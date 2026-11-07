package com.akira.island.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.akira.island.R
import com.akira.island.core.AlarmInfo
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios

/**
 * Ближайший будильник. Его знает сама система (AlarmManager.getNextAlarmClock) — уведомления «Часов»
 * для этого не нужны. Поставили новый будильник — остров показывает плашку со временем подъёма;
 * следующий по расписанию (прошлый прозвенел или отложен) — молча.
 * Будильником системы пользуются и календари, напоминалки, трекеры сна — их «будильник» человек не ставил,
 * поэтому берём только поставленные приложением-будильником («Часы», Alarmy и т. п.).
 */
class AlarmMonitor(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = refresh(announce = true)
    }

    fun start() {
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        refresh(announce = false)
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        IslandBus.nextAlarm.value = null
    }

    private fun refresh(announce: Boolean) {
        val info = runCatching { alarms?.nextAlarmClock }.getOrNull()
        // Будильник от «Часов» узнаём по создателю showIntent; если прошивка его не положила,
        // будильник всё равно показываем — время срабатывания известно
        val next = info?.let { a ->
            val pkg = a.showIntent?.creatorPackage
            if (pkg != null && !ClockApps.isAlarmApp(context, pkg)) null
            else AlarmInfo(a.triggerTime, pkg, a.showIntent)
        }
        val prev = IslandBus.nextAlarm.value
        IslandBus.nextAlarm.value = next
        if (!announce || next == null || prev?.triggerAt == next.triggerAt) return
        val now = System.currentTimeMillis()
        if (next.triggerAt <= now) return
        // Прошлый будильник уже прозвенел или его отложили — это не новый, а очередной по расписанию
        if (prev != null && prev.triggerAt <= now + 60_000) return
        val s = SettingsRepo.current
        if (!s.enabled || !s.evAlarmSet) return
        IslandBus.post(
            Notice(
                title = tr("Будильник", "Alarm"),
                icon = Ios.load(context, R.drawable.ios_alarm),
                tint = Tints.Orange,
                style = NoticeStyle.ALARM,
                alarm = next,
                extraMs = 2_000,
            ),
        )
    }
}
