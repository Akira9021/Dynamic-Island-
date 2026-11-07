package com.akira.island.core.system

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.akira.island.core.AlarmInfo

/**
 * Giorni selezionati nell'UI dell'isola (bit i, 0 = lunedì … 6 = domenica). Restituisce i valori
 * [AlarmClock.EXTRA_DAYS] dell'Intent (numeralazione Calendar: domenica = 1 … sabato = 7), oppure null
 * per un allarme una tantum.
 */

fun alarmDaysFor(selected: Int?): List<Int>? {
    if (selected == null) return null
    val days = (0..6).filter { (selected shr it) and 1 == 1 }.map { if (it == 6) 1 else it + 2 }
    return days.ifEmpty { null }
}

/**
 * Pacchetto dell'app «OClock» a cui indirizzare gli intent AlarmClock: preferisce il creatore della
 * prossima sveglia ([nextAlarmOwner]), poi il primo tra i candidati, sempre escludendo il nostro package.
 */
fun pickClockPackage(nextAlarmOwner: String?, candidates: List<String>, selfPackage: String): String? {
    val pool = (listOfNotNull(nextAlarmOwner) + candidates)
        .filter { it.isNotBlank() && it != selfPackage }
        .distinct()
    return pool.firstOrNull()
}

/**
 * Bridge to the system Clock / Alarm apps: create alarms and timers through the public
 * AlarmClock intents, read the next alarm through AlarmManager, dismiss / snooze.
 * Runtime capability detection with a graceful fallback.
 *
 * Intents are always addressed to the resolved Clock app package (excluding our own package)
 * so they never resolve back into the Dynamic Island itself.
 */
interface ClockBridge {
    fun isSupported(context: Context): Boolean
    fun canScheduleExactAlarms(context: Context): Boolean
    fun createAlarm(context: Context, hour: Int, minutes: Int, days: Int?, label: String?): Boolean
    fun createTimer(context: Context, seconds: Long, label: String?): Boolean
    fun nextAlarm(context: Context): AlarmInfo?
    fun dismissAlarm(context: Context, alarm: AlarmInfo?): Boolean
    fun snoozeAlarm(context: Context, alarm: AlarmInfo?, minutes: Int): Boolean
    fun dismissTimer(context: Context, seconds: Long): Boolean
}

class SystemClockBridge(private val context: Context) : ClockBridge {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun isSupported(context: Context): Boolean = resolveClockPackage(context) != null

    override fun canScheduleExactAlarms(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            false
        }

    /** Resolve a real Clock app (Alarm clock / Desk clock / …) excluding our own package. */
    private fun resolveClockPackage(context: Context): String? {
        val actions = listOf(
            AlarmClock.ACTION_SET_ALARM,
            AlarmClock.ACTION_SET_TIMER,
            AlarmClock.ACTION_SHOW_ALARMS,
            AlarmClock.ACTION_SHOW_TIMERS,
        )
        val packages = actions.flatMap { action ->
            runCatching {
                context.packageManager.queryIntentActivities(Intent(action), 0)
                    .map { it.activityInfo.packageName }
            }.getOrNull() ?: emptyList()
        }
        return pickClockPackage(null, packages, context.packageName)
    }

    override fun createAlarm(context: Context, hour: Int, minutes: Int, days: Int?, label: String?): Boolean {
        val pkg = resolveClockPackage(context) ?: return false
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            days?.let { putExtra(AlarmClock.EXTRA_DAYS, ArrayList<Int>(listOf(it))) }
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return start(context, intent)
    }

    override fun createTimer(context: Context, seconds: Long, label: String?): Boolean {
        val pkg = resolveClockPackage(context) ?: return false
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt().coerceIn(1, 86_400))
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return start(context, intent)
    }

    override fun nextAlarm(context: Context): AlarmInfo? = alarmManager?.nextAlarmClock?.let { info ->
        val pkg = info.showIntent?.creatorPackage
        if (pkg.isNullOrBlank()) null else AlarmInfo(info.triggerTime, pkg, info.showIntent)
    }

    override fun dismissAlarm(context: Context, alarm: AlarmInfo?): Boolean {
        val pendingIntent = alarm?.show ?: return false
        alarmManager?.cancel(pendingIntent)
        return true
    }

    override fun snoozeAlarm(context: Context, alarm: AlarmInfo?, minutes: Int): Boolean {
        val pkg = resolveClockPackage(context) ?: return false
        val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, minutes)
        }
        return start(context, intent)
    }

    override fun dismissTimer(context: Context, seconds: Long): Boolean {
        val pkg = resolveClockPackage(context) ?: return false
        val intent = Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt().coerceIn(1, 86_400))
        }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
