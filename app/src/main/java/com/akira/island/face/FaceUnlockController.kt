package com.akira.island.face

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rikka.shizuku.Shizuku

enum class FaceAvailability { OFF, SERVICE_OFF, SHIZUKU_STOPPED, PERMISSION_NEEDED, BACKEND_UNSUPPORTED, PAUSED, CONNECTING, WAITING_FOR_EVENTS, EVENTS_SEEN, SOURCE_UNAVAILABLE }

object FaceUnlockState {
    val phase = MutableStateFlow(FacePhase.HIDDEN)
    val availability = MutableStateFlow(FaceAvailability.OFF)
    internal val revision = MutableStateFlow(0L)
    fun refresh() { revision.update { it + 1 } }
    fun access(): FaceAvailability = runCatching {
        when {
            !Shizuku.pingBinder() -> FaceAvailability.SHIZUKU_STOPPED
            Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> FaceAvailability.BACKEND_UNSUPPORTED
            Shizuku.getUid() != 2000 -> FaceAvailability.BACKEND_UNSUPPORTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> FaceAvailability.PERMISSION_NEEDED
            else -> FaceAvailability.CONNECTING
        }
    }.getOrDefault(FaceAvailability.SHIZUKU_STOPPED)
}

/** Owns the optional reader and session; existing music/events/WindowManager are independent. */
class FaceUnlockController(private val context: Context, parent: CoroutineScope) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val wake = FaceWakeSession()
    private val session get() = wake.session
    private var connection: ServiceConnection? = null
    private var source: IFaceSource? = null
    private var timer: Job? = null
    private var bindingTimeout: Job? = null
    private var generation = 0L
    private var started = false
    private val args = Shizuku.UserServiceArgs(ComponentName(context, FaceSourceService::class.java))
        .tag("island-face-events-v1").version(1).daemon(false).debuggable(false).processNameSuffix("face_events")
    private val binderReceived = Shizuku.OnBinderReceivedListener { FaceUnlockState.refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { FaceUnlockState.refresh() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> FaceUnlockState.refresh() }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    wake.screenOff(SettingsRepo.current.faceUnlockOnly && SettingsRepo.current.enabled,
                        keyguard?.isDeviceSecure == true, SystemClock.elapsedRealtime())
                    publish(); disconnect(); FaceUnlockState.refresh()
                }
                Intent.ACTION_SCREEN_ON -> { beginLock(); FaceUnlockState.refresh() }
                Intent.ACTION_USER_PRESENT -> {
                    beginLock()
                    session.userPresent(SystemClock.elapsedRealtime(), power?.isInteractive == true &&
                        keyguard?.isKeyguardLocked == false && keyguard.isDeviceLocked == false)
                    publish()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(context, screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permission)
        beginLock()
        scope.launch {
            combine(SettingsRepo.state.map { it.faceUnlockOnly && it.enabled }.distinctUntilChanged(),
                IslandBus.screenOn, FaceUnlockState.revision) { enabled, _, _ -> enabled }
                .collect { enabled ->
                    beginLock()
                    val status = when {
                        !enabled -> FaceAvailability.OFF
                        power?.isInteractive != true -> FaceAvailability.PAUSED
                        else -> FaceUnlockState.access()
                    }
                    if (status == FaceAvailability.CONNECTING) {
                        if (connection == null) connect()
                    } else {
                        disconnect()
                        if (status != FaceAvailability.PAUSED) wake.clear()
                        publish(); FaceUnlockState.availability.value = status
                    }
                }
        }
    }

    private fun beginLock() {
        wake.refresh(SettingsRepo.current.faceUnlockOnly && SettingsRepo.current.enabled,
            keyguard?.isKeyguardLocked == true && keyguard.isDeviceLocked, SystemClock.elapsedRealtime())
        publish()
    }

    private fun connect() {
        val token = ++generation
        FaceUnlockState.availability.value = FaceAvailability.CONNECTING
        val events = object : IFaceEvents.Stub() {
            override fun onSignal(kind: Int, elapsedMillis: Long) {
                scope.launch {
                    if (token != generation || !SettingsRepo.current.faceUnlockOnly || power?.isInteractive != true) return@launch
                    val signal = FaceSignal.entries.getOrNull(kind) ?: return@launch
                    if (signal != FaceSignal.OTHER_UNLOCK && signal != FaceSignal.NON_CREDENTIAL_ENTRY)
                        FaceUnlockState.availability.value = FaceAvailability.EVENTS_SEEN
                    session.signal(signal, elapsedMillis, SystemClock.elapsedRealtime()); publish()
                }
            }
            override fun onSourceState(state: Int) {
                scope.launch {
                    if (token != generation) return@launch
                    if (state == 1) FaceUnlockState.availability.value = FaceAvailability.WAITING_FOR_EVENTS
                    else failSource()
                }
            }
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (token != generation) return
                bindingTimeout?.cancel()
                runCatching {
                    binder.linkToDeath({ scope.launch { if (token == generation) failSource() } }, 0)
                    // Package visibility can hide this optional source. The independently trusted
                    // system-server source still requires explicit face authentication and entry.
                    val uiUid = runCatching {
                        context.packageManager.getApplicationInfo("com.android.systemui", 0)
                            .takeIf { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }?.uid ?: -1
                    }.getOrDefault(-1)
                    val remote = IFaceSource.Stub.asInterface(binder)
                    source = remote
                    remote.start(events, android.os.Process.myUid() / 100_000, uiUid)
                }.onFailure { failSource() }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (token == generation) failSource() }
        }
        connection = conn
        runCatching { Shizuku.bindUserService(args, conn) }.onFailure { failSource() }
        if (token == generation && connection != null) bindingTimeout = scope.launch {
            delay(10_000)
            if (token == generation && source == null) failSource()
        }
    }

    private fun publish() {
        FaceUnlockState.phase.value = session.phase
        timer?.cancel()
        val deadline = session.deadline ?: return
        timer = scope.launch {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            session.tick(SystemClock.elapsedRealtime()); publish()
        }
    }
    private fun failSource() {
        disconnect(); wake.clear(); publish()
        FaceUnlockState.availability.value = FaceAvailability.SOURCE_UNAVAILABLE
    }
    private fun disconnect() {
        ++generation
        bindingTimeout?.cancel(); bindingTimeout = null
        val remote = source; source = null
        // Reader cleanup cannot block the UI thread.
        if (remote != null) Thread({ runCatching { remote.stop() } }, "island-face-stop").apply {
            isDaemon = true
            start()
        }
        connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }
        connection = null
    }
    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(screen) }
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permission)
        disconnect(); wake.clear(); FaceUnlockState.phase.value = FacePhase.HIDDEN
        FaceUnlockState.availability.value = FaceAvailability.SERVICE_OFF
        scope.cancel()
    }
}
