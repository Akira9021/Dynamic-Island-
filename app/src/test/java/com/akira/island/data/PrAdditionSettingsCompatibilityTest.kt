package com.akira.island.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PrAdditionSettingsCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun oldPreferencesDoNotOptIntoNewActionsOrChangeExistingChoices() {
        val restored = json.decodeFromString<IslandSettings>("""{"language":"FR","width":146,"batteryPercentOnly":true,"quickActions":["TIMER","STOPWATCH"],"glassBlurStrength":85,"textColorEnabled":true,"textColor":4281554286}""")
        assertFalse(restored.showAlarmCreator)
        assertFalse(restored.adaptiveChargeEta)
        assertFalse(restored.keepExpandedOnOutsideTouch)
        assertEquals(146, restored.width)
        assertEquals(AppLanguage.FR, restored.language)
        assertTrue(restored.batteryPercentOnly)
        assertEquals(listOf(QuickAction.TIMER, QuickAction.STOPWATCH), restored.quickActions)
        assertEquals(85, restored.glassBlurStrength)
        assertEquals(4281554286L, restored.textColor)
    }

    @Test fun optInsAndCustomColorsPersistWithoutReplacingOtherSettings() {
        val before = IslandSettings(language = AppLanguage.ID, width = 142, quickActions = listOf(QuickAction.TIMER),
            liquidGlass = true, glassBlurStrength = 72, batteryPercentOnly = true,
            showAlarmCreator = true, adaptiveChargeEta = true, keepExpandedOnOutsideTouch = true,
            textColorEnabled = true, textColor = 0xFF3596D8L, showPlayerBattery = true)
        assertEquals(before, json.decodeFromString<IslandSettings>(json.encodeToString(before)))
    }
}
