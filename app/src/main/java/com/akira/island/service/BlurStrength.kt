package com.akira.island.service

/** 50% preserves the existing 28dp material; zero keeps glass without native blur. */
internal fun glassBlurRadiusDp(strength: Int): Float = 28f * strength.coerceIn(0, 100) / 50f
