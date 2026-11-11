package com.akira.island.island

import androidx.compose.ui.graphics.Color
import com.akira.island.data.IslandSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class IslandTextTest {
    @Test fun oldPreferencesKeepNativeTextAndRecordingIsIndependentOfTimers() {
        val old = Json.decodeFromString<IslandSettings>("{\"cardTimer\":false}")
        assertFalse(old.textColorEnabled)
        assertTrue(old.cardRecording)
        val custom = old.copy(textColorEnabled = true, textColor = 0xFF47D5F0)
        assertEquals(custom, Json.decodeFromString<IslandSettings>(Json.encodeToString(custom)))
    }

    @Test fun recoloringRetainsSecondaryOpacityAndSafetyColors() {
        val custom = Color(0xFF47D5F0)
        assertEquals(custom, resolveIslandTextColor(Color.White, custom))
        assertEquals(custom.copy(alpha = 0.55f), resolveIslandTextColor(Color.White.copy(alpha = 0.55f), custom))
        assertEquals(custom.copy(alpha = com.akira.island.core.Tints.Gray.red),
            resolveIslandTextColor(com.akira.island.core.Tints.Gray, custom))
        assertEquals(Color.Red, resolveIslandTextColor(Color.Red, custom))
        assertEquals(Color.Black, resolveIslandTextColor(Color.Black, custom))
        assertEquals(Color.White, resolveIslandTextColor(Color.White, null))
    }
}
