package com.akira.island.service

import android.content.Context
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.ExtrasRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

object WeatherRepo {
    val state = MutableStateFlow<WeatherInfo?>(null)
    val error = MutableStateFlow<String?>(null)
    private var coordinateKey: Pair<Double, Double>? = null
    fun start(context: Context, scope: CoroutineScope) {
        ExtrasRepo.init(context)
        scope.launch {
            combine(ExtrasRepo.state, IslandBus.screenOn) { s, screen -> s to screen }.collectLatest { (s, screen) ->
                val coordinates = s.latitude to s.longitude
                state.value = state.value?.takeIf { it.city == s.weatherCity && s.weatherEnabled && coordinateKey == coordinates }
                coordinateKey = coordinates
                if (!s.weatherEnabled || s.weatherCity.isBlank() || !s.latitude.isFinite() || !s.longitude.isFinite() || s.latitude !in -90.0..90.0 || s.longitude !in -180.0..180.0 || !screen) return@collectLatest
                while (true) {
                    try {
                        val old = state.value
                        if (old == null || System.currentTimeMillis() - old.updatedAt !in 0..900_000) {
                            val body = get("https://api.open-meteo.com/v1/forecast?latitude=${s.latitude}&longitude=${s.longitude}&current=temperature_2m,weather_code")
                            state.value = parse(body, s.weatherCity, System.currentTimeMillis())
                            error.value = null
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error.value = tr("Не удалось обновить погоду", "Weather update failed") }
                    // Never show old weather as a current reading after a long offline period.
                    if (state.value?.let { System.currentTimeMillis() - it.updatedAt !in 0..3_600_000 } == true) state.value = null
                    delay(900_000)
                }
            }
        }
    }
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
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 7000; c.readTimeout = 7000
            c.setRequestProperty("User-Agent", "DynamicIsland/1.7.0")
            check(c.responseCode == 200)
            c.inputStream.bufferedReader().use { r -> r.readText().also { require(it.length < 1_000_000) } }
        } finally { c.disconnect() }
    }
}
