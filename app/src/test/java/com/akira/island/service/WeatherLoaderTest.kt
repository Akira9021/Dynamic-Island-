package com.akira.island.service

import com.akira.island.data.ExtraSettings
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class WeatherLoaderTest {
    private val now = 1_800_000_000_000L
    private val moscow = WeatherTarget("Moscow, Russia", 55.7522, 37.6156)
    private fun reading(target: WeatherTarget = moscow, at: Long = now) = WeatherInfo(target.city, 12.0, 0, at)

    @Test fun firstFailureRetriesInSecondsAndSuccessReturnsToTheNormalRefreshInterval() = runBlocking {
        var attempts = 0
        val waits = Channel<Long>(Channel.UNLIMITED)
        val resume = Channel<Unit>(Channel.UNLIMITED)
        val loader = WeatherLoader({ moscow }, {
            if (++attempts == 1) throw IOException("offline")
            reading()
        }, { "Connection failed" }, { now }, { waits.send(it); resume.receive() })
        val worker = launch { loader.observe(MutableStateFlow(WeatherQuery(moscow, true))) }
        try {
            assertEquals(15_000L, withTimeout(2_000) { waits.receive() })
            assertNull(loader.state.value); assertFalse(loader.loading.value)
            assertEquals("Connection failed", loader.error.value)
            resume.send(Unit)
            assertEquals(WEATHER_REFRESH_MS, withTimeout(2_000) { waits.receive() })
            assertEquals(2, attempts); assertEquals(reading(), loader.state.value)
            assertNull(loader.error.value)
        } finally { worker.cancelAndJoin() }
    }

    @Test fun retriesBackOffAndNeverPollContinuouslyOnALongOutage() {
        assertEquals(listOf(15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 300_000L),
            (1..6).map(::weatherRetryDelay))
    }

    @Test fun existingWeatherUsesTheRemainingTtlInsteadOfAnotherFullFifteenMinutes() {
        assertEquals(60_000L, weatherRefreshDelay(reading(at = now - 840_000), now))
        assertEquals(0L, weatherRefreshDelay(reading(at = now - WEATHER_REFRESH_MS), now))
        assertEquals(0L, weatherRefreshDelay(reading(at = now + 1), now))
        assertEquals(0L, weatherRefreshDelay(null, now))
    }

    @Test fun simultaneousManualAndAutomaticRequestsShareOneSuccessfulFetch() = runBlocking {
        val response = CompletableDeferred<WeatherInfo>()
        var requests = 0
        val loader = WeatherLoader({ moscow }, { requests++; response.await() }, { "Failed" }, { now })
        val first = async(start = CoroutineStart.UNDISPATCHED) { loader.refresh() }
        assertTrue(loader.loading.value)
        val second = async(start = CoroutineStart.UNDISPATCHED) { loader.refresh() }
        response.complete(reading())
        assertTrue(first.await()); assertTrue(second.await())
        assertEquals(1, requests); assertFalse(loader.loading.value)
    }

    @Test fun changingCoordinatesCannotPublishThePreviousCityRequest() = runBlocking {
        var selected = moscow
        val response = CompletableDeferred<WeatherInfo>()
        val loader = WeatherLoader({ selected }, { response.await() }, { "Failed" }, { now })
        val pending = async(start = CoroutineStart.UNDISPATCHED) { loader.refresh() }
        selected = moscow.copy(latitude = 56.0)
        response.complete(reading())
        assertFalse(pending.await()); assertNull(loader.state.value)
        assertFalse(loader.loading.value); assertNull(loader.error.value)
    }

    @Test fun screenOffCancelsAnInFlightRequestAndScreenOnStartsAgain() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val waits = Channel<Long>(Channel.UNLIMITED)
        val query = MutableStateFlow(WeatherQuery(moscow, true))
        var attempts = 0
        val loader = WeatherLoader({ moscow }, {
            if (++attempts == 1) {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            reading()
        }, { "Failed" }, { now }, { waits.send(it); awaitCancellation() })
        val worker = launch { loader.observe(query) }
        try {
            withTimeout(2_000) { entered.await() }
            query.value = WeatherQuery(moscow, false)
            withTimeout(2_000) { cancelled.await() }
            query.value = WeatherQuery(moscow, true)
            assertEquals(WEATHER_REFRESH_MS, withTimeout(2_000) { waits.receive() })
            assertEquals(2, attempts); assertEquals(reading(), loader.state.value)
            assertNull(loader.error.value); assertFalse(loader.loading.value)
        } finally { worker.cancelAndJoin() }
    }

    @Test fun staleWeatherIsRemovedAfterFailureButARecentReadingCanStayVisible() = runBlocking {
        var clock = now
        var fail = false
        val loader = WeatherLoader({ moscow }, {
            if (fail) throw IOException("offline")
            reading()
        }, { "Failed" }, { clock })
        assertTrue(loader.refresh())
        fail = true; clock = now + WEATHER_MAX_AGE_MS
        assertFalse(loader.refresh()); assertNotNull(loader.state.value)
        clock++
        assertFalse(loader.refresh()); assertNull(loader.state.value)
    }

    @Test fun disabledOrInvalidCityNeverBecomesARequestTarget() {
        val valid = ExtraSettings(weatherEnabled = true, weatherCity = moscow.city,
            latitude = moscow.latitude, longitude = moscow.longitude)
        assertEquals(moscow, weatherTarget(valid))
        for (settings in listOf(valid.copy(weatherEnabled = false), valid.copy(weatherCity = ""),
            valid.copy(latitude = Double.NaN), valid.copy(latitude = 91.0), valid.copy(longitude = -181.0))) {
            assertNull(weatherTarget(settings))
        }
    }
}
