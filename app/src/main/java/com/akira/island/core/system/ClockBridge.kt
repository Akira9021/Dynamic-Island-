package com.akira.island.core.system

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * Posticipo (snooze) di un allarme: durata costante in minuti, poi in millisecondi — e la passiamo a
 * [AlarmClock.EXTRA_ALARM_SNOOZE_DURATION] dell'Intent di posticipo.
 */
const val ALARM_SNOOZE_MINUTES = 10L

/** Parole che significano «поставить на потом» (snooze) per il tasto della notifica/будильника. */
private val snoozeRoots = listOf("snooze", "отлож", "postpon", "delay")

/** Parole che significano «снять / отменить» (dismiss) per il tasto della notifica/будильника. */
private val dismissRoots = listOf("dismiss", "cancel", "stop", "clear", "delete", "remove", "отклон", "reject", "отмен", "удалить", "останов", "выключ")

/** Logcat-канale del sistema bridge: tutta la diagnostica vive qui, senza spazzare il resto. */
private const val SYS_TAG = "SysBridge"

fun isSnoozeLabel(label: CharSequence?): Boolean {
    val words = label?.toString()?.lowercase()?.split(Regex("[^\\p{L}]+"))?.filter { it.isNotEmpty() } ?: return false
    return words.any { w -> snoozeRoots.any { w.startsWith(it) } }
}

fun isDismissLabel(label: CharSequence?): Boolean {
    val words = label?.toString()?.lowercase()?.split(Regex("[^\\p{L}]+"))?.filter { it.isNotEmpty() } ?: return false
    return words.any { w -> dismissRoots.any { w.startsWith(it) } }
}

fun snoozeDurationMs(): Long = ALARM_SNOOZE_MINUTES * 60_000L

/** Сдвиг времени будильника на [delta] минут с переходом через полночь: 23:55 + 10 → 00:05. */
fun shiftAlarmTime(hour: Int, minute: Int, delta: Int): Pair<Int, Int> {
    val total = (((hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59) + delta) % 1440) + 1440) % 1440
    return total / 60 to total % 60
}

/** Переключить день недели в маске (bit 0 = понедельник … 6 = воскресенье); null — ни одного дня. */
fun toggleAlarmDay(mask: Int?, day: Int): Int? {
    if (day !in 0..6) return mask?.takeIf { it != 0 }
    val toggled = (mask ?: 0) xor (1 shl day)
    return toggled.takeIf { it != 0 }
}

/**
 * Ближайшее срабатывание будильника (epoch ms) для часа, минут и маски дней, начиная с [now]:
 * тот же расчёт, что делает «Часы» при постановке будильника. Нужен, чтобы после создания
 * сверить с [AlarmManager.getNextAlarmClock] — поставился ли будильник на самом деле.
 */
fun nextAlarmAt(
    hour: Int,
    minutes: Int,
    daysMask: Int?,
    now: Long,
    zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
): Long {
    val cal = java.util.Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(java.util.Calendar.MINUTE, minutes.coerceIn(0, 59))
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val days = alarmDaysFor(daysMask) ?: return cal.timeInMillis
    repeat(8) {
        if (cal.get(java.util.Calendar.DAY_OF_WEEK) in days) return cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
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
    fun createAlarm(context: Context, hour: Int, minutes: Int, days: List<Int>?, label: String?): Boolean
    fun createTimer(context: Context, seconds: Long, label: String?): Boolean
    fun nextAlarm(context: Context): AlarmInfo?
    fun clockPackage(context: Context): String?
    fun dismissAlarm(context: Context, alarm: AlarmInfo?, fallbackIntent: PendingIntent?): Boolean
    fun snoozeAlarm(context: Context, alarm: AlarmInfo?, minutes: Int, fallbackIntent: PendingIntent?): Boolean
    fun dismissTimer(context: Context, seconds: Long): Boolean
}

class SystemClockBridge(private val context: Context) : ClockBridge {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun isSupported(context: Context): Boolean {
        val supported = resolveClockPackage(context) != null
        debug(context, "SysBridge isSupported=$supported")
        return supported
    }

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

    override fun createAlarm(context: Context, hour: Int, minutes: Int, days: List<Int>?, label: String?): Boolean {
        val pkg = resolveClockPackage(context) ?: return false.also { debug(context, "SysBridge createAlarm pkg=null") }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            days?.let { putExtra(AlarmClock.EXTRA_DAYS, ArrayList(it)) }
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val ok = start(context, intent)
        debug(context, "SysBridge createAlarm pkg=$pkg hour=$hour min=$minutes days=$days ok=$ok")
        return ok
    }

    override fun createTimer(context: Context, seconds: Long, label: String?): Boolean {
        val pkg = resolveClockPackage(context) ?: return false
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt().coerceIn(1, 86_400))
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val ok = start(context, intent)
        debug(context, "SysBridge createTimer pkg=$pkg seconds=${seconds}s ok=$ok")
        return ok
    }

    override fun nextAlarm(context: Context): AlarmInfo? = alarmManager?.nextAlarmClock?.let { info ->
        // showIntent может отсутствовать (некоторые прошивки не кладут его в AlarmClockInfo) —
        // это не повод скрывать будильник: время срабатывания всё равно известно
        val pkg = info.showIntent?.creatorPackage
        debug(context, "SysBridge nextAlarm=$pkg triggerAt=${info.triggerTime}")
        AlarmInfo(info.triggerTime, pkg, info.showIntent)
    }

    override fun clockPackage(context: Context): String? {
        val pkg = resolveClockPackage(context)
        debug(context, "SysBridge clockPackage=$pkg")
        return pkg
    }

    override fun dismissAlarm(context: Context, alarm: AlarmInfo?, fallbackIntent: PendingIntent?): Boolean {
        val pkg = alarm?.pkg?.takeIf { it.isNotBlank() } ?: resolveClockPackage(context)
        if (pkg == null) {
            debug(context, "SysBridge dismissAlarm pkg=null fallback=${fallbackIntent != null}")
            return sendPending(context, fallbackIntent, "dismissAlarm")
        }
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            setPackage(pkg)
        }
        val ok = start(context, intent)
        debug(context, "SysBridge dismissAlarm pkg=$pkg ok=$ok fallback=${!ok && fallbackIntent != null}")
        return ok || sendPending(context, fallbackIntent, "dismissAlarm")
    }

    override fun snoozeAlarm(context: Context, alarm: AlarmInfo?, minutes: Int, fallbackIntent: PendingIntent?): Boolean {
        val pkg = alarm?.pkg?.takeIf { it.isNotBlank() } ?: resolveClockPackage(context)
        if (pkg == null) {
            debug(context, "SysBridge snoozeAlarm pkg=null fallback=${fallbackIntent != null}")
            return sendPending(context, fallbackIntent, "snoozeAlarm")
        }
        val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM).apply {
            setPackage(pkg)
            putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, minutes)
        }
        val ok = start(context, intent)
        debug(context, "SysBridge snoozeAlarm pkg=$pkg minutes=$minutes ok=$ok fallback=${!ok && fallbackIntent != null}")
        return ok || sendPending(context, fallbackIntent, "snoozeAlarm")
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

    /**
     * Esegue l'azione di una notifica (PendingIntent) quando il Clock app ignora l'intent AlarmClock.
     * Da Android 14 serve la variante con le opzioni: la versione senza Bundle viene bloccata.
     */
    private fun sendPending(context: Context, pi: PendingIntent?, where: String): Boolean {
        if (pi == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                pi.send(context, 0, null, null, null, null, ActivityOptions.makeBasic().toBundle())
            } else {
                pi.send()
            }
            debug(context, "SysBridge $where pendingIntent=sent")
            true
        } catch (e: Exception) {
            debug(context, "SysBridge $where pendingIntent=failed ${e.javaClass.simpleName}")
            false
        }
    }

    private fun debug(context: Context, msg: String) {
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.util.Log.d(SYS_TAG, msg)
        }
    }
}
