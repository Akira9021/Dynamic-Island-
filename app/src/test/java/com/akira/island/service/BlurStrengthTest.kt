package com.akira.island.service

import com.akira.island.data.IslandSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BlurStrengthTest {
    @Test fun existingPreferencesKeepTheSameMaterial() {
        val old=Json.decodeFromString<IslandSettings>("""{"liquidGlass":true,"glassOpacity":68}""")
        assertEquals(50,old.glassBlurStrength)
        assertEquals(28f,glassBlurRadiusDp(old.glassBlurStrength),0f)
    }
    @Test fun strengthIsIndependentOfDensityAndSurvivesSerialization() {
        val settings=IslandSettings(liquidGlass=true,glassOpacity=73,glassBlurStrength=85)
        val restored=Json.decodeFromString(IslandSettings.serializer(),Json.encodeToString(IslandSettings.serializer(),settings))
        assertEquals(settings,restored)
        assertEquals(73,restored.glassOpacity)
        assertEquals(85,restored.glassBlurStrength)
    }
    @Test fun rangeIsBoundedAndZeroReallyDisablesTheBlurRadius() {
        assertEquals(0f,glassBlurRadiusDp(-30),0f)
        assertEquals(0f,glassBlurRadiusDp(0),0f)
        assertEquals(56f,glassBlurRadiusDp(100),0f)
        assertEquals(56f,glassBlurRadiusDp(999),0f)
    }
    @Test fun resetReturnsToTheExistingStandardStrength() {
        assertEquals(50,IslandSettings().glassBlurStrength)
        assertTrue(glassBlurRadiusDp(25)<glassBlurRadiusDp(50))
        assertTrue(glassBlurRadiusDp(50)<glassBlurRadiusDp(75))
    }
}
