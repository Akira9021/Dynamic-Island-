package com.akira.island.service

import android.content.Context
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.ExtrasRepo
import com.akira.island.data.ExtraSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import com.akira.island.security.openTrustedHttps
import com.akira.island.security.readUtf8Bounded

/** No location permission: users select the city sent to Open-Meteo. */
data class WeatherInfo(val city: String, val temperature: Double, val code: Int, val updatedAt: Long) {
    fun summary(): String = when (code) {
        0 -> tr("Ясно", "Clear sky")
        1, 2, 3 -> tr("Облачно", "Cloudy")
        45, 48 -> tr("Туман", "Fog")
        in 51..67, in 80..82 -> tr("Дождь", "Rain")
        in 71..77, 85, 86 -> tr("Снег", "Snow")
        in 95..99 -> tr("Гроза", "Thunderstorm")
        else -> tr("Погода", "Weather")
    }
}
data class WeatherCity(val name: String, val latitude: Double, val longitude: Double)

internal const val WEATHER_REFRESH_MS = 900_000L
internal const val WEATHER_MAX_AGE_MS = 3_600_000L

internal data class WeatherTarget(val city: String, val latitude: Double, val longitude: Double)
internal data class WeatherQuery(val target: WeatherTarget?, val screenOn: Boolean)

internal fun weatherTarget(settings: ExtraSettings): WeatherTarget? = settings.run {
    if (!weatherEnabled || weatherCity.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
        latitude !in -90.0..90.0 || longitude !in -180.0..180.0) null
    else WeatherTarget(weatherCity, latitude, longitude)
}

internal fun weatherRetryDelay(failures: Int): Long = when (failures) {
    in Int.MIN_VALUE..1 -> 15_000L
    2 -> 30_000L
    3 -> 60_000L
    4 -> 120_000L
    else -> 300_000L
}

internal fun weatherRefreshDelay(reading: WeatherInfo?, now: Long): Long {
    val age = reading?.let { now - it.updatedAt } ?: return 0L
    return if (age in 0 until WEATHER_REFRESH_MS) WEATHER_REFRESH_MS - age else 0L
}

/** Shared by service polling and an explicit refresh in settings/the unlocked Brief page. */
internal class WeatherLoader(
    private val selected: () -> WeatherTarget?,
    private val fetch: suspend (WeatherTarget) -> WeatherInfo,
    private val failureMessage: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    val state = MutableStateFlow<WeatherInfo?>(null)
    val error = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    private val request = Mutex()
    private var cachedTarget: WeatherTarget? = null
    @Volatile private var generation = 0L

    private fun syncTarget(target: WeatherTarget?) {
        if (cachedTarget != target) {
            cachedTarget = target
            state.value = null
            error.value = null
            loading.value = false
        }
    }

    suspend fun refresh(force: Boolean = true): Boolean {
        val target = selected() ?: run { syncTarget(null); return false }
        val observedGeneration = generation
        return request.withLock {
            if (selected() != target) return@withLock false
            syncTarget(target)
            // A request that finished while we waited already satisfies this refresh.
            if ((!force || generation != observedGeneration) && weatherRefreshDelay(state.value, now()) > 0) return@withLock true
            loading.value = true
            error.value = null
            try {
                val result = fetch(target)
                currentCoroutineContext().ensureActive()
                if (selected() != target) return@withLock false
                state.value = result
                generation++
                true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (selected() == target) {
                    state.value = state.value?.takeIf { now() - it.updatedAt in 0..WEATHER_MAX_AGE_MS }
                    error.value = failureMessage()
                }
                false
            } finally {
                loading.value = false
                syncTarget(selected())
            }
        }
    }

    suspend fun observe(queries: Flow<WeatherQuery>) {
        queries.distinctUntilChanged().collectLatest { query ->
            syncTarget(query.target)
            if (query.target == null || !query.screenOn) return@collectLatest
            var failures = 0
            while (true) {
                val remaining = weatherRefreshDelay(state.value, now())
                if (remaining > 0) {
                    pause(remaining)
                    continue
                }
                val success = refresh(force = false)
                failures = if (success) 0 else (failures + 1).coerceAtMost(5)
                pause(if (success) weatherRefreshDelay(state.value, now()).coerceAtLeast(1L) else weatherRetryDelay(failures))
            }
        }
    }
}

object WeatherRepo {
    private val loader = WeatherLoader(
        selected = { weatherTarget(ExtrasRepo.state.value) },
        fetch = { target ->
            val body = get("https://api.open-meteo.com/v1/forecast?latitude=${target.latitude}&longitude=${target.longitude}&current=temperature_2m,weather_code")
            parse(body, target.city, System.currentTimeMillis())
        },
        failureMessage = { tr("Не удалось обновить погоду. Проверьте подключение.", "Weather update failed. Check your connection.") },
    )
    val state = loader.state
    val error = loader.error
    val loading = loader.loading
    private var job: Job? = null

    /** Owned by the accessibility service; screen changes cancel work and wake retries immediately. */
    @Synchronized fun start(context: Context, scope: CoroutineScope) {
        ExtrasRepo.init(context)
        if (job?.isActive == true) return
        job = scope.launch {
            loader.observe(combine(ExtrasRepo.state, IslandBus.screenOn) { settings, screen ->
                WeatherQuery(weatherTarget(settings), screen)
            })
        }
    }

    /** One user-requested fetch; never starts a background polling loop without the service. */
    suspend fun refresh(force: Boolean = true): Boolean = loader.refresh(force)
    fun parse(body: String, city: String, time: Long): WeatherInfo {
        val c = JSONObject(body).getJSONObject("current")
        val temperature = c.getDouble("temperature_2m")
        require(temperature.isFinite() && temperature in -100.0..70.0)
        return WeatherInfo(city, temperature, c.getInt("weather_code"), time)
    }
    suspend fun cities(query: String): List<WeatherCity> {
        require(query.trim().length in 2..100)
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val a = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=5&format=json")).optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val c = a.getJSONObject(i)
            WeatherCity(listOf(c.getString("name"), c.optString("admin1"), c.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", "), c.getDouble("latitude"), c.getDouble("longitude"))
        }
    }
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val c = openTrustedHttps(url, 7000, 7000, mapOf("User-Agent" to "DynamicIsland/1.7.0"))
        try {
            check(c.responseCode == 200)
            c.inputStream.use { it.readUtf8Bounded(1_000_000) }
        } finally { c.disconnect() }
    }
}
