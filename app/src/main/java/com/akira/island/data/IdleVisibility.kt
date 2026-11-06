package com.akira.island.data

/** Only a visible idle capsule can expire; never hide active cards, calibration or the lock screen. */
internal fun shouldTimeOutIdle(
    settings: IslandSettings,
    idle: Boolean,
    expanded: Boolean,
    calibrating: Boolean,
    screenOn: Boolean,
    keyguard: Boolean,
    shadeOpen: Boolean,
): Boolean = settings.enabled && settings.idleHideSec > 0 && !settings.hideIdle && idle && !expanded &&
    !calibrating && screenOn && !keyguard && !(settings.collapseOnShade && shadeOpen)
