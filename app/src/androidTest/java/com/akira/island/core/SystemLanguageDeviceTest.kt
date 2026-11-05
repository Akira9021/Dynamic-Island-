package com.akira.island.core

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.data.AppLanguage
import com.akira.island.data.SettingsRepo
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemLanguageDeviceTest {
    @Test fun systemChoiceHonorsAndroidAppLocaleAndTranslatesNewSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsRepo.init(context)
        val saved = SettingsRepo.current.language
        try {
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("id-ID")) }
            Lang.init(context.createConfigurationContext(config))
            SettingsRepo.update { copy(language = AppLanguage.SYSTEM) }
            assertEquals("id", Lang.code)
            assertEquals("Warna dan garis tepi", tr("Цвет и рамка", "Color and border"))
            assertEquals("Cari pengaturan", tr("Поиск по настройкам", "Search settings"))
        } finally {
            Lang.init(context)
            SettingsRepo.update { copy(language = saved) }
        }
    }
}
