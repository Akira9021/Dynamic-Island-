package com.akira.island.island

import com.akira.island.core.*
import com.akira.island.data.IslandSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TransferGroupingTest {
    @Test fun groupCannotBorrowSpeedOrEtaFromTheFirstOperation() {
        val a = ProgressInfo("a", "test.a", "File", null, 50, 100, false, 1, TransferDetails(1000.0, etaSeconds = 40))
        assertEquals(a, groupedProgress(listOf(a)))
        val b = a.copy(key = "b", progress = 100, transferDetails = TransferDetails(100000.0, etaSeconds = 10))
        val group = groupedProgress(listOf(a, b))!!
        assertNull(group.transferDetails)
        assertEquals(0.75f, group.fraction, 0.001f)
        assertTrue(groupedProgress(listOf(a, b.copy(indeterminate = true)))!!.indeterminate)
    }
    @Test fun oldSettingsDoNotEnablePollingAndAllExistingChoicesSurvive() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = json.decodeFromString<IslandSettings>("""{"width":142,"backdropBlur":true,"showPlayerBattery":true,"cardProgress":true}""")
        assertFalse(old.showNetworkRate)
        assertTrue(old.backdropBlur); assertTrue(old.showPlayerBattery); assertEquals(142, old.width)
        val enabled = old.copy(showNetworkRate = true)
        assertEquals(enabled, json.decodeFromString<IslandSettings>(json.encodeToString(enabled)))
    }
}
