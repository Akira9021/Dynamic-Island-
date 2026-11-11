package com.akira.island.island

import com.akira.island.data.ExtraSettings
import com.akira.island.service.WeatherInfo
import com.akira.island.service.WEATHER_MAX_AGE_MS

internal enum class BriefDataStatus { HIDDEN, DISABLED, UNAVAILABLE, READY }

/** Values are removed here, before either visible text or accessibility semantics are composed. */
internal data class BriefDetails(
    val stepsStatus: BriefDataStatus,
    val steps: Long?,
    val weatherStatus: BriefDataStatus,
    val weather: WeatherInfo?,
    val weatherLoading: Boolean = false,
    val weatherError: String? = null,
    val weatherCity: String? = null,
)

internal fun briefDetails(
    extras: ExtraSettings,
    locked: Boolean,
    steps: Long?,
    weather: WeatherInfo?,
    now: Long,
    weatherLoading: Boolean = false,
    weatherError: String? = null,
): BriefDetails {
    if (locked) return BriefDetails(BriefDataStatus.HIDDEN, null, BriefDataStatus.HIDDEN, null)
    val visibleSteps = steps?.takeIf { extras.steps && it >= 0 }
    val stepsStatus = when {
        !extras.steps -> BriefDataStatus.DISABLED
        visibleSteps == null -> BriefDataStatus.UNAVAILABLE
        else -> BriefDataStatus.READY
    }
    val weatherEnabled = extras.weatherEnabled && extras.weatherCity.isNotBlank()
    val visibleWeather = weather?.takeIf {
        weatherEnabled && it.city == extras.weatherCity && it.temperature.isFinite() &&
            now - it.updatedAt in 0..WEATHER_MAX_AGE_MS
    }
    val weatherStatus = when {
        !weatherEnabled -> BriefDataStatus.DISABLED
        visibleWeather == null -> BriefDataStatus.UNAVAILABLE
        else -> BriefDataStatus.READY
    }
    return BriefDetails(stepsStatus, visibleSteps, weatherStatus, visibleWeather,
        weatherLoading = weatherEnabled && weatherLoading,
        weatherError = weatherError.takeIf { weatherEnabled },
        weatherCity = extras.weatherCity.takeIf { weatherEnabled })
}
