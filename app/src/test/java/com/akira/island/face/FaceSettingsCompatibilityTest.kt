package com.akira.island.face

import com.akira.island.data.IslandSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class FaceSettingsCompatibilityTest {
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    @Test fun legacySettingsKeepTheirBehaviorAndDoNotEnableFaceReader() {
        val s=json.decodeFromString<IslandSettings>("""{"evUnlock":true,"width":130,"lyricsEnabled":false}""")
        assertTrue(s.evUnlock);assertFalse(s.faceUnlockOnly);assertEquals(130,s.width);assertFalse(s.lyricsEnabled)
    }
    @Test fun faceOptInRoundTripsWithoutOverwritingOtherSettings() {
        val s=IslandSettings(faceUnlockOnly=true,evUnlock=true,evHeadsetOn=false)
        assertEquals(s,json.decodeFromString<IslandSettings>(json.encodeToString(s)))
    }
}
