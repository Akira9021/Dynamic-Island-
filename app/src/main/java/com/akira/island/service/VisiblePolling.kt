package com.akira.island.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** Disabled readers suspend on their inputs instead of repeatedly waking an empty loop. */
internal suspend fun visiblePolling(
    screen: Flow<Boolean>, enabled: Flow<Boolean>, intervalMs: Long,
    poll: () -> Unit, pause: suspend (Long) -> Unit = { delay(it) },
) {
    combine(screen, enabled) { on, option -> on && option }.distinctUntilChanged().collectLatest { allowed ->
        if (!allowed) return@collectLatest
        while (currentCoroutineContext().isActive) {
            runCatching { poll() }
            pause(intervalMs)
        }
    }
}
