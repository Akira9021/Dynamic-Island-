package com.akira.island.face

/** Only semantic events leave the read-only shell process. No log text or biometric IDs. */
internal enum class FaceSignal { START, ACCEPTED, REJECTED, STOPPED, OTHER_UNLOCK, NON_CREDENTIAL_ENTRY }
enum class FacePhase { HIDDEN, SCANNING, SUCCESS, FAILURE }

/** Face authentication, positive non-credential entry and verified device presence must agree. */
internal class FaceSession {
    var phase = FacePhase.HIDDEN
        private set
    private var began = -1L
    private var present = -1L
    private var accepted = -1L
    private var acceptedReceived = -1L
    private var entry = -1L
    private var entryReceived = -1L
    private var blocked = false
    private var finished = false
    private var expires = -1L
    val deadline: Long? get() = when {
        blocked -> null
        finished -> expires.takeIf { phase != FacePhase.HIDDEN }
        complete -> maxOf(acceptedReceived, entryReceived, present) + SETTLE_MS
        firstEvidence != null -> firstEvidence!! + PAIR_MS
        phase != FacePhase.HIDDEN -> expires
        else -> null
    }
    private val complete get() = accepted >= 0 && entry >= 0 && present >= 0
    private val firstEvidence get() = listOf(accepted, entry, present).filter { it >= 0 }.minOrNull()

    fun lock(now: Long) {
        clear()
        began = now
    }

    fun clear() {
        phase = FacePhase.HIDDEN
        began = -1; present = -1; accepted = -1; acceptedReceived = -1; expires = -1
        entry = -1; entryReceived = -1
        blocked = false; finished = false
    }

    fun signal(signal: FaceSignal, at: Long, now: Long) {
        if (began < 0 || at < began || at > now + 100) return
        if (signal == FaceSignal.OTHER_UNLOCK) {
            // Even a competing method arriving after our initial result withdraws the effect.
            blocked = true; accepted = -1; phase = FacePhase.HIDDEN; expires = -1
            return
        }
        if (now - at > PAIR_MS || blocked || finished) return
        when (signal) {
            FaceSignal.START -> if (present < 0 && accepted < 0) {
                phase = FacePhase.SCANNING; expires = now + 3_200
            }
            FaceSignal.ACCEPTED -> {
                if (accepted < 0) acceptedReceived = now
                accepted = at
                // Keep a genuinely started scanner visible during confirmation, avoiding a blink.
                if (phase != FacePhase.SCANNING) phase = FacePhase.HIDDEN
            }
            FaceSignal.NON_CREDENTIAL_ENTRY -> if (entry < 0) {
                entry = at
                entryReceived = now
            }
            FaceSignal.REJECTED -> {
                accepted = -1; acceptedReceived = -1
                phase = if (present < 0) FacePhase.FAILURE else FacePhase.HIDDEN
                expires = now + 700
            }
            FaceSignal.STOPPED -> if (accepted < 0) { phase = FacePhase.HIDDEN; expires = -1 }
            FaceSignal.OTHER_UNLOCK -> Unit
        }
        tick(now)
    }

    fun userPresent(now: Long, unlocked: Boolean) {
        if (began < 0 || now < began || blocked || finished || !unlocked) return
        if (present < 0) present = now
        if (phase != FacePhase.SCANNING) phase = FacePhase.HIDDEN
        tick(now)
    }

    fun tick(now: Long) {
        if (blocked) return
        if (phase != FacePhase.HIDDEN && now >= expires) phase = FacePhase.HIDDEN
        if (finished) return
        if (complete) {
            if (maxOf(accepted, entry, present) - minOf(accepted, entry, present) > PAIR_MS) {
                blocked = true; phase = FacePhase.HIDDEN
            } else if (now >= maxOf(acceptedReceived, entryReceived, present) + SETTLE_MS) {
                phase = FacePhase.SUCCESS; expires = now + 1_200; finished = true
            }
        } else if (firstEvidence?.let { now >= it + PAIR_MS } == true) {
            blocked = true; phase = FacePhase.HIDDEN
        }
    }

    companion object {
        const val PAIR_MS = 1_200L
        const val SETTLE_MS = 400L
    }
}
