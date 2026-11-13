package com.akira.island.island

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.akira.island.core.*
import com.akira.island.data.*
import com.akira.island.ui.MainActivity
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PendingAlarmDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainTapOpensSummaryAndOnlyBubbleTapSelectsAlarmEvenWithHideIdle() {
        val settings=SettingsRepo.current; val extras=ExtrasRepo.state.value
        val alarm=IslandBus.nextAlarm.value; val focus=IslandBus.focus.value
        val expanded=IslandBus.expanded.value; val notice=IslandBus.notice.value
        val locked=IslandBus.locked.value
        val body=AtomicReference(Rect.Zero); val bubble=AtomicReference(Rect.Zero)
        try {
            compose.runOnUiThread {
                SettingsRepo.update { IslandSettings(language=AppLanguage.EN, cardAlarm=true, bubble=true,
                    reduceEffects=true, hideIdle=true, autoUpdateCheck=false, historyMinutes=0,
                    cardMedia=false,cardCharging=false,cardLive=false,cardCall=false,cardProgress=false,cardTorch=false) }
                ExtrasRepo.update { ExtraSettings() }
                IslandBus.notice.value=null; IslandBus.locked.value=false
                IslandBus.nextAlarm.value=AlarmInfo(System.currentTimeMillis()+3_600_000,null,null)
                Controls.collapse()
                compose.activity.setContent { Box(Modifier.fillMaxSize()) {
                    IslandOverlay(onBodyBounds={body.set(it)},onBubbleBounds={bubble.set(it)})
                } }
            }
            compose.onNodeWithContentDescription("Alarm").assertIsDisplayed()
            compose.onRoot().captureToImage()
            assertTrue(body.get().width>0); assertTrue(bubble.get().width>0)
            compose.onRoot().performTouchInput { click(body.get().center) }
            compose.runOnIdle { assertTrue(IslandBus.expanded.value); assertEquals("idle",IslandBus.focus.value) }
            compose.runOnUiThread { Controls.collapse() }
            compose.onNodeWithContentDescription("Alarm").performClick()
            compose.runOnIdle { assertTrue(IslandBus.expanded.value); assertEquals("alarm",IslandBus.focus.value) }
            compose.runOnUiThread { Controls.collapse() }
            compose.onNodeWithContentDescription("Alarm").assertIsDisplayed()
        } finally { compose.runOnUiThread {
            SettingsRepo.update { settings };ExtrasRepo.update { extras }
            IslandBus.nextAlarm.value=alarm;IslandBus.notice.value=notice;IslandBus.locked.value=locked
            IslandBus.focus.value=focus;IslandBus.expanded.value=expanded
        } }
    }
}
