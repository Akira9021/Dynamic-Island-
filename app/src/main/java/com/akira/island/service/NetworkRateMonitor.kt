package com.akira.island.service

import android.net.TrafficStats
import android.os.SystemClock
import com.akira.island.core.IslandBus
import com.akira.island.core.NetworkRate
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class TrafficSnapshot(val rx: Long, val tx: Long, val elapsed: Long)

internal fun networkRateAllowed(option: Boolean, enabled: Boolean, progressCard: Boolean, hasProgress: Boolean,
    screen: Boolean, locked: Boolean, saving: Boolean, running: Boolean, offSchedule: Boolean, expanded: Boolean = true): Boolean =
    option && enabled && progressCard && hasProgress && screen && !locked && !saving && running && !offSchedule && expanded

/** The service owns collection; consumers never start polling by opening a preview. */
internal class NetworkRateMonitor(
    private val allowed: Flow<Boolean>,
    private val read: () -> TrafficSnapshot = { TrafficSnapshot(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes(), SystemClock.elapsedRealtime()) },
    private val publish: (NetworkRate?) -> Unit = { NetworkRates.value.value = it },
    private val interval: Long = 1000,
) {
    private var job: Job? = null
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                allowed.distinctUntilChanged().collectLatest { enabled ->
                    publish(null)
                    if (!enabled) return@collectLatest
                    val counter = NetworkRateCounter()
                    var warmSamples = 0
                    while (currentCoroutineContext().isActive) {
                        val snapshot = runCatching { read() }.getOrNull()
                        val rate = if (snapshot == null) { counter.reset(); null }
                            else counter.sample(snapshot.rx, snapshot.tx, snapshot.elapsed)
                        publish(rate)
                        delay(if (rate == null && warmSamples++ < 6) minOf(interval, 250L) else interval)
                    }
                }
            } finally { publish(null) }
        }
    }
    fun stop() { job?.cancel(); job = null; publish(null) }
}

internal object NetworkRates {
    internal val value = MutableStateFlow<NetworkRate?>(null)
    val state = value.asStateFlow()
    fun monitor(): NetworkRateMonitor {
        val settingsAndProgress = combine(SettingsRepo.state, IslandBus.progress) { s, p -> s to p.isNotEmpty() }
        val visibility = combine(IslandBus.screenOn, IslandBus.locked, IslandBus.powerSave,
            IslandBus.serviceRunning, IslandBus.offSchedule) { screen, locked, saving, running, off ->
            booleanArrayOf(screen, locked, saving, running, off)
        }
        return NetworkRateMonitor(combine(settingsAndProgress, visibility, IslandBus.expanded) { (s, active), v, expanded ->
            networkRateAllowed(s.showNetworkRate, s.enabled, s.cardProgress, active, v[0], v[1], v[2], v[3], v[4], expanded)
        })
    }
}
