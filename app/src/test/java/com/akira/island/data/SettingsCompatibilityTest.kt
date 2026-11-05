package com.akira.island.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SettingsCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun previousSettingsKeepUserChoicesAndInheritNewAppearance() {
        val previous = """{"width":120,"height":34,"outline":true,"outlineColor":4281554280,"outlineWidth":2.5,"language":"KK","landscape":"CAMERA","messageStyle":"PILL","quickActions":["TIMER","DND"]}"""
        val settings = json.decodeFromString<IslandSettings>(previous)
        assertEquals(120, settings.width)
        assertEquals(34, settings.height)
        assertEquals(2.5f, settings.outlineWidth, 0f)
        assertEquals(AppLanguage.KK, settings.language)
        assertEquals(LandscapeMode.CAMERA, settings.landscape)
        assertEquals(MessageStyle.PILL, settings.messageStyle)
        assertEquals(listOf(QuickAction.TIMER, QuickAction.DND), settings.quickActions)
        assertEquals(-1, settings.cornerRadius)
        assertEquals(-1f, settings.expandedOutlineWidth, 0f)
        assertEquals(-1f, settings.outlineLeftWidth, 0f)
        assertFalse(settings.expandedColorEnabled)
        assertFalse(settings.swipeActivities)
    }

    @Test fun newAppearanceAndLanguagesSurviveRestart() {
        val before = IslandSettings(
            language = AppLanguage.FR,
            cornerRadius = 8, expandedCornerRadius = 24,
            outlineTopWidth = 0f, outlineRightWidth = 2f, outlineBottomWidth = 4f, outlineLeftWidth = 1f,
            expandedOutlineWidth = 3f, expandedOutlineOpacity = 45,
            expandedColorEnabled = true, expandedColor = 0xFF1A2238,
            expandedMaxHeight = 240, messageIconMode = MessageIconMode.NONE,
            idleClockBattery = true, hideOnAod = true,
        )
        assertEquals(before, json.decodeFromString<IslandSettings>(json.encodeToString(before)))
        assertEquals(AppLanguage.ES, json.decodeFromString<IslandSettings>("""{"language":"ES"}""").language)
    }
}
