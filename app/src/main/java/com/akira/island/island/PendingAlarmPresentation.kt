package com.akira.island.island

/** A scheduled alarm may occupy the main card only after an explicit selection. */
internal fun primaryWithPendingAlarm(acts: List<Act>, expanded: Boolean, focus: String?): Act =
    acts.firstOrNull { it !is Act.AlarmA || expanded && focus == it.id } ?: Act.Idle

internal fun secondaryWithPendingAlarm(acts: List<Act>, primary: Act): Act? =
    acts.firstOrNull { !it.transient && it.id != primary.id }
