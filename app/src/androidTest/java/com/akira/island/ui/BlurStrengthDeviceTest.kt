package com.akira.island.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.data.*
import com.akira.island.ui.screens.ColorsScreen
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlurStrengthDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun sliderChangesStrengthWithoutChangingDensityAndPersistsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var saved: IslandSettings
        compose.runOnUiThread {
            SettingsRepo.init(context)
            saved = SettingsRepo.current
            SettingsRepo.update { copy(language = AppLanguage.EN, liquidGlass = true,
                glassBlurStrength = 50, glassOpacity = 73, autoUpdateCheck = false) }
            compose.activity.setContent { IslandTheme { ColorsScreen {} } }
        }
        try {
            val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and
                hasAnyAncestor(hasTestTag("glass-blur-strength")))
            for (value in listOf(0, 100, 50)) {
                slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
                compose.runOnIdle {
                    assertEquals(value, SettingsRepo.current.glassBlurStrength)
                    assertEquals(73, SettingsRepo.current.glassOpacity)
                    val raw = context.getSharedPreferences("island", 0).getString("settings_v1", null)!!
                    val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<IslandSettings>(raw)
                    assertEquals(value, decoded.glassBlurStrength)
                }
            }
            compose.onNodeWithTag("glass-blur-preview").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Sharp text").assertIsDisplayed()
            compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        } finally {
            compose.runOnUiThread { SettingsRepo.update { saved } }
        }
    }
}
