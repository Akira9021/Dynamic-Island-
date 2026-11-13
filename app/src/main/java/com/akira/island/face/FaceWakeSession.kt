package com.akira.island.face

/** Keeps a fresh secure-screen-off boundary even when face unlock beats the wake broadcast. */
internal class FaceWakeSession {
    val session = FaceSession()
    private var hasEpoch = false

    fun refresh(enabled: Boolean, knownLocked: Boolean, now: Long) {
        if (!enabled) clear()
        else if (!hasEpoch && knownLocked) begin(now)
    }

    fun screenOff(enabled: Boolean, secure: Boolean, now: Long) {
        clear()
        if (enabled && secure) begin(now)
    }

    fun clear() {
        hasEpoch = false
        session.clear()
    }

    private fun begin(now: Long) {
        hasEpoch = true
        session.lock(now)
    }
}
