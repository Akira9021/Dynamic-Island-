package com.akira.island.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class VisiblePollingTest {
    @Test fun optionAndScreenCancelWaitingAndResumeExactlyOneReader() = runBlocking {
        withTimeout(5000) {
            val screen = MutableStateFlow(true)
            val option = MutableStateFlow(true)
            val ticks = Channel<Unit>(Channel.UNLIMITED)
            val waiting = Channel<Long>(Channel.UNLIMITED)
            val cancelled = Channel<Unit>(Channel.UNLIMITED)
            val job = launch {
                visiblePolling(screen, option, 250, { ticks.trySend(Unit) }, { interval ->
                    waiting.send(interval)
                    try { awaitCancellation() } finally { cancelled.trySend(Unit) }
                })
            }
            try {
                ticks.receive(); assertEquals(250L, waiting.receive())
                option.value = false
                cancelled.receive()
                assertTrue(ticks.tryReceive().isFailure)
                option.value = true
                ticks.receive(); waiting.receive()
                screen.value = false
                cancelled.receive()
                option.value = false
                screen.value = true
                // Reenable only after the previous reader has been cancelled.
                option.value = true
                ticks.receive(); waiting.receive()
                assertTrue("No duplicate polling loop", ticks.tryReceive().isFailure)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun failedReadDoesNotPreventTheNextReadOrCancellation() = runBlocking {
        withTimeout(5000) {
            val permits = Channel<Unit>()
            val attempts = Channel<Int>(Channel.UNLIMITED)
            var count = 0
            val job = launch {
                visiblePolling(MutableStateFlow(true), MutableStateFlow(true), 1500, {
                    attempts.trySend(++count)
                    if (count == 1) error("Synthetic unavailable window")
                }, { permits.receive() })
            }
            try {
                assertEquals(1, attempts.receive())
                permits.send(Unit)
                assertEquals(2, attempts.receive())
            } finally { job.cancelAndJoin() }
        }
    }
}
