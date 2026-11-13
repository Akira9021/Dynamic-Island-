package com.akira.island.island

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.akira.island.core.*
import com.akira.island.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class TransferRateLayoutDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun largeTextWrapsDirectionsAndTheExampleDoesNotPublishRealTraffic() {
        val progress = IslandBus.progress.value
        val media = IslandBus.media.value
        val rate = com.akira.island.service.NetworkRates.state.value
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Column(Modifier.width(216.dp).padding(top = 24.dp)) {
                        TransferRateLines(TransferDetails(1_500_000.0, TransferDirection.DOWN, 120), null)
                        NetworkRateExample()
                    }
                }
            }
        }
        compose.waitForIdle(); compose.onRoot().captureToImage()
        compose.onNodeWithTag("operation-rate").assertIsDisplayed()
        compose.onNodeWithTag("operation-eta").assertIsDisplayed()
        compose.onNodeWithTag("network-incoming").assertIsDisplayed()
        compose.onNodeWithTag("network-outgoing").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(progress, IslandBus.progress.value)
            assertEquals(media, IslandBus.media.value)
            assertEquals(rate, com.akira.island.service.NetworkRates.state.value)
        }
    }
}
