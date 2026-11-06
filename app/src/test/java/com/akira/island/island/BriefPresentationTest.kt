package com.akira.island.island

import com.akira.island.data.BriefClockStyle
import com.akira.island.data.ExtraSettings
import com.akira.island.service.WeatherInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BriefPresentationTest {
    private val now = 1_800_000_000_000L
    private val extras = ExtraSettings(briefEnabled = true, steps = true, weatherEnabled = true,
        weatherCity = "Test city", latitude = 50.0, longitude = 30.0)
    private val weather = WeatherInfo("Test city", 12.0, 0, now)

    @Test fun oldExtrasKeepTheirSourcesAndDoNotOptIntoBrief() {
        val old = Json.decodeFromString<ExtraSettings>("""{"steps":true,"analogClock":true,"weatherEnabled":true,"weatherCity":"Test city","latitude":50.0,"longitude":30.0,"apps":["example.app"]}""")
        assertFalse(old.briefEnabled)
        assertEquals(BriefClockStyle.DIGITAL, old.briefClockStyle)
        assertTrue(old.steps); assertTrue(old.analogClock); assertTrue(old.weatherEnabled)
        assertEquals("Test city", old.weatherCity)
        assertEquals(listOf("example.app"), old.apps)
    }

    @Test fun briefStyleSurvivesSavingWithoutEnablingSourcesOrControls() {
        val chosen = ExtraSettings(briefEnabled = true, briefClockStyle = BriefClockStyle.ANALOG)
        val encoded = Json.encodeToString(ExtraSettings.serializer(), chosen)
        val restored = Json.decodeFromString<ExtraSettings>(encoded)
        assertEquals(chosen, restored)
        assertFalse(restored.steps); assertFalse(restored.weatherEnabled); assertFalse(restored.controls)
    }

    @Test fun lockRemovesValuesBeforeRenderingIncludingASelectedCity() {
        val hidden = briefDetails(extras, locked = true, steps = 2_345, weather, now)
        assertEquals(BriefDataStatus.HIDDEN, hidden.stepsStatus)
        assertEquals(BriefDataStatus.HIDDEN, hidden.weatherStatus)
        assertNull(hidden.steps); assertNull(hidden.weather)
    }

    @Test fun disabledSourcesCannotExposeTheirPreviousReadings() {
        val disabled = briefDetails(extras.copy(steps = false, weatherEnabled = false), false, 2_345, weather, now)
        assertEquals(BriefDataStatus.DISABLED, disabled.stepsStatus)
        assertEquals(BriefDataStatus.DISABLED, disabled.weatherStatus)
        assertNull(disabled.steps); assertNull(disabled.weather)
        assertEquals(BriefDataStatus.DISABLED,
            briefDetails(extras.copy(weatherCity = ""), false, 0, weather, now).weatherStatus)
    }

    @Test fun zeroStepsAreAReadingButMissingOrNegativeStepsAreNot() {
        assertEquals(0L, briefDetails(extras, false, 0, weather, now).steps)
        for (count in listOf(null, -1L)) {
            val result = briefDetails(extras, false, count, weather, now)
            assertEquals(BriefDataStatus.UNAVAILABLE, result.stepsStatus)
            assertNull(result.steps)
        }
    }

    @Test fun weatherMustMatchTheSelectedCityAndBeWithinTheProvidersFreshnessLimit() {
        val current = briefDetails(extras, false, null, weather.copy(updatedAt = now - 3_600_000), now)
        assertEquals(BriefDataStatus.READY, current.weatherStatus)
        assertNotNull(current.weather)
        for (reading in listOf(null, weather.copy(city = "Old city"), weather.copy(updatedAt = now - 3_600_001),
            weather.copy(updatedAt = now + 1), weather.copy(temperature = Double.NaN))) {
            val result = briefDetails(extras, false, null, reading, now)
            assertEquals(BriefDataStatus.UNAVAILABLE, result.weatherStatus)
            assertNull(result.weather)
        }
    }
}
