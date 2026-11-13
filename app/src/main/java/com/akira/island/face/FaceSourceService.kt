package com.akira.island.face

import android.content.Context
import android.os.Binder
import android.os.Process
import android.os.SystemClock
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/** A public Shizuku UserService. Fixed read-only commands; no shell interpolation or settings writes. */
@Keep
class FaceSourceService(context: Context) : IFaceSource.Stub() {
    private val callerUid = context.applicationInfo.uid
    private val serial = AtomicLong()
    @Volatile private var reader: java.lang.Process? = null
    @Volatile private var worker: Thread? = null

    private fun checkCaller() {
        check(Binder.getCallingUid() == callerUid || Binder.getCallingUid() == 2000) { "Caller not allowed" }
    }

    @Synchronized override fun start(events: IFaceEvents, userId: Int, systemUiUid: Int) {
        checkCaller()
        stop()
        if (Process.myUid() != 2000 || userId != callerUid / 100_000) {
            runCatching { events.onSourceState(2) }; return
        }
        val token = serial.get()
        // If the client disappears, a non-daemon service must not retain its reader.
        val death = android.os.IBinder.DeathRecipient { stopInternal() }
        runCatching { events.asBinder().linkToDeath(death, 0) }.onFailure { return }
        worker = Thread({
            try {
                val sources = FaceLogSources(pid("system_server"), pid("com.android.systemui"), systemUiUid,
                    pid("keystore2"), userId)
                // A PID only identifies a candidate source. Success additionally needs a positive
                // parsed non-credential entry from it, so missing/unknown formats fail closed.
                if (sources.serverPid <= 0 || sources.keyPid <= 0) {
                    events.onSourceState(2); return@Thread
                }
                if (serial.get() != token) return@Thread
                val wallNow = System.currentTimeMillis()
                val wallFloor = wallNow - FaceSession.PAIR_MS
                val offset = wallNow - SystemClock.elapsedRealtime()
                val parser = FaceLogParser(sources)
                // Bounded catch-up covers a fast face result during binding. The app also enforces
                // its fresh lock-session epoch; history can never authenticate a later unlock.
                val process = ProcessBuilder("/system/bin/logcat", "-B", "-b", "main", "-b", "system", "-T", "128",
                    "Biometrics/AuthenticationClient:V", "BiometricStatusRepositoryImpl:D", "keystore2:I", "*:S")
                    .redirectError(java.io.File("/dev/null")).start()
                synchronized(this) {
                    if (serial.get() != token) { process.destroy(); return@Thread }
                    reader = process
                }
                events.onSourceState(1) // Stream opened, NOT proof of device compatibility.
                process.inputStream.buffered().use { input ->
                    while (serial.get() == token && !Thread.currentThread().isInterrupted) {
                        val record = FaceLogReader.read(input) ?: break
                        val now = SystemClock.elapsedRealtime()
                        // Time changes and old buffered entries must never authenticate a new session.
                        if (kotlin.math.abs((System.currentTimeMillis() - now) - offset) > 250) break
                        val at = record.wallMs - offset
                        if (at > now + 100) continue
                        val signal = parser.parse(record) ?: continue
                        // A delayed competing method must still withdraw a result from this lock.
                        // The client applies its session epoch to vetoes as well as positive events.
                        if (signal != FaceSignal.OTHER_UNLOCK &&
                            (record.wallMs < wallFloor || now - at > FaceSession.PAIR_MS)) continue
                        if (serial.get() == token) events.onSignal(signal.ordinal, at)
                    }
                }
            } catch (_: Exception) {
                // Deliberately do not log exception messages, journal text or captured identifiers.
            } finally {
                synchronized(this) { if (serial.get() == token) { reader?.destroy(); reader = null } }
                runCatching { events.asBinder().unlinkToDeath(death, 0) }
                if (serial.get() == token) runCatching { events.onSourceState(3) }
            }
        }, "island-face-events").apply { isDaemon = true; start() }
    }

    private fun pid(name: String): Int {
        val p = ProcessBuilder("/system/bin/pidof", name).redirectError(java.io.File("/dev/null")).start()
        return try {
            if (!p.waitFor(1, TimeUnit.SECONDS)) return -1
            val values = p.inputStream.bufferedReader().readText().trim().split(Regex("\\s+"))
            values.singleOrNull()?.toIntOrNull() ?: -1
        } finally { p.destroy() }
    }

    @Synchronized private fun stopInternal() {
        serial.incrementAndGet()
        reader?.destroy(); reader = null
        worker?.interrupt(); worker = null
    }
    override fun stop() { checkCaller(); stopInternal() }
    override fun destroy() { checkCaller(); stopInternal(); exitProcess(0) }
}
