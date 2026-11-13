package com.akira.island.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ShortcutsBatteryCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun oldSettingsRetainChoicesAndDoNotOptIntoPlayerBattery() {
        val old = """{"language":"FR","width":146,"preferMusic":true,"backdropBlur":true,"batteryPercentOnly":true,"quickActions":["TIMER"],"unknownFutureField":true}"""
        val s = json.decodeFromString<IslandSettings>(old)
        assertFalse(s.showPlayerBattery)
        assertTrue(s.backdropBlur)
        assertTrue(s.batteryPercentOnly)
        assertTrue(s.preferMusic)
        assertEquals(146, s.width)
        assertEquals(AppLanguage.FR, s.language)
        assertEquals(listOf(QuickAction.TIMER), s.quickActions)
        val enabled = s.copy(showPlayerBattery = true)
        assertEquals(enabled, json.decodeFromString<IslandSettings>(json.encodeToString(enabled)))
    }

    @Test fun allShortcutSelectionsAndUnrelatedExtrasSurviveSerialization() {
        val before = ExtraSettings(apps = (1..6).map { "example.app$it" },
            contacts = (1..6).map { ContactShortcut("Contact $it", "content://com.android.contacts/contacts/lookup/key$it/$it") },
            briefEnabled = true, briefClockStyle = BriefClockStyle.SPLIT, weatherEnabled = true,
            weatherCity = "Moscow", latitude = 55.75, longitude = 37.62, controls = true, steps = true)
        assertEquals(before, json.decodeFromString<ExtraSettings>(json.encodeToString(before)))
        val previous = """{"apps":["example.app"],"contacts":[{"name":"Анна","uri":"content://com.android.contacts/contacts/8"}],"weatherCity":"Moscow"}"""
        val restored = json.decodeFromString<ExtraSettings>(previous)
        assertEquals(listOf("example.app"), restored.apps)
        assertEquals("Анна", restored.contacts.single().name)
        assertEquals("Moscow", restored.weatherCity)
        assertEquals(restored, json.decodeFromString<ExtraSettings>(json.encodeToString(restored)))
    }
}
