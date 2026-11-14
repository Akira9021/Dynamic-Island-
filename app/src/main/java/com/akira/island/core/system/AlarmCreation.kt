package com.akira.island.core.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import java.util.Calendar

internal enum class AlarmWeekday(val calendarDay: Int) {
    MONDAY(Calendar.MONDAY), TUESDAY(Calendar.TUESDAY), WEDNESDAY(Calendar.WEDNESDAY),
    THURSDAY(Calendar.THURSDAY), FRIDAY(Calendar.FRIDAY), SATURDAY(Calendar.SATURDAY), SUNDAY(Calendar.SUNDAY),
}

internal data class AlarmCreationRequest(
    val hour: Int,
    val minute: Int,
    val days: Set<AlarmWeekday> = emptySet(),
    val label: String = "",
) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }
    val calendarDays: List<Int> get() = AlarmWeekday.entries.filter { it in days }.map { it.calendarDay }
    val cleanLabel: String get() = label.trim().take(80)
}

/** Let Clock confirm the request. Dispatch is never evidence that an alarm was created. */
internal fun alarmCreationIntent(request: AlarmCreationRequest): Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
    putExtra(AlarmClock.EXTRA_HOUR, request.hour)
    putExtra(AlarmClock.EXTRA_MINUTES, request.minute)
    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    if (request.calendarDays.isNotEmpty()) putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(request.calendarDays))
    request.cleanLabel.takeIf { it.isNotEmpty() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
}

internal enum class AlarmCreationDispatch { OPENED_CLOCK, UNAVAILABLE, FAILED }

/** No silent timer replacement, private APIs, result inference or notification cancellation. */
internal fun openAlarmCreation(context: Context, request: AlarmCreationRequest, chooserTitle: String): AlarmCreationDispatch {
    val intent = alarmCreationIntent(request)
    val manager = context.packageManager
    @Suppress("DEPRECATION")
    val targets = runCatching { manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }
        .getOrElse { return AlarmCreationDispatch.FAILED }
        .mapNotNull { result -> result.activityInfo?.takeIf {
            it.enabled && it.exported && it.packageName != context.packageName
        }?.let { ComponentName(it.packageName, it.name) } }.distinct()
    if (targets.isEmpty()) return AlarmCreationDispatch.UNAVAILABLE
    @Suppress("DEPRECATION")
    val preferred = runCatching { manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo }
        .getOrNull()?.let { ComponentName(it.packageName, it.name) }?.takeIf { it in targets }
    val launch = when {
        preferred != null -> Intent(intent).setComponent(preferred)
        targets.size == 1 -> Intent(intent).setComponent(targets.first())
        else -> Intent.createChooser(Intent(intent).setComponent(targets.first()), chooserTitle).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).map { Intent(intent).setComponent(it) }.toTypedArray())
        }
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(launch); AlarmCreationDispatch.OPENED_CLOCK }
        .getOrDefault(AlarmCreationDispatch.FAILED)
}
