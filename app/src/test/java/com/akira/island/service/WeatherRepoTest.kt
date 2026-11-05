package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class WeatherRepoTest {
    @Test fun parsesCurrentWeatherWithTimestamp() {
        val result = WeatherRepo.parse("""{"current":{"temperature_2m":-12.5,"weather_code":73}}""", "Astana", 123L)
        assertEquals("Astana", result.city)
        assertEquals(-12.5, result.temperature, 0.001)
        assertEquals(73, result.code)
        assertEquals(123L, result.updatedAt)
    }
    @Test(expected = Exception::class) fun missingTemperatureIsNotShownAsZero() {
        WeatherRepo.parse("""{"current":{"weather_code":0}}""", "Paris", 1L)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsImpossibleTemperature() {
        WeatherRepo.parse("""{"current":{"temperature_2m":999,"weather_code":0}}""", "Paris", 1L)
    }
}
