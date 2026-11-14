package com.akira.island.island

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.akira.island.core.IslandBus
import com.akira.island.data.ContactShortcut
import com.akira.island.face.FacePhase
import com.akira.island.face.FaceUnlockCard
import com.akira.island.ui.MainActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisualRefinementDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun clearEmulatorSystemAnr() {
        if (android.os.Build.HARDWARE !in setOf("ranchu","goldfish")) return
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        repeat(2) {
            val root=instrumentation.uiAutomation.rootInActiveWindow ?: return
            if(root.packageName?.toString() !in setOf("android","com.android.systemui")) return
            val systemAnr=listOf("Quickstep","System UI","com.android.launcher3").any { name ->
                root.findAccessibilityNodeInfosByText(name).any { node ->
                    node.text?.toString()?.let { text -> text.contains("responding") || text.contains("не отвечает") }==true
                }
            }
            if(!systemAnr) return
            val close=listOf("Close app","Закрыть приложение").flatMap { root.findAccessibilityNodeInfosByText(it) }
                .firstOrNull { it.isClickable } ?: return
            assertTrue("Close only the emulator's system ANR dialog",close.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(1000)
        }
    }

    @Test fun smallPopupDoesNotConsumeTheUnderlyingCardAndDismissesOutside() {
        var dismissed=false;var outside=false;var chosen=false
        compose.runOnUiThread { compose.activity.setContent {
            var visible by remember { mutableStateOf(true) }
            Box(Modifier.fillMaxSize().background(Color(0xFF232933))) {
                Box(Modifier.align(Alignment.TopStart).offset(y=128.dp).size(64.dp).testTag("outside-shortcuts")
                    .clickable { outside=true })
                if(visible) PinnedShortcutPopup(listOf("com.akira.island"),emptyList(),{chosen=true},{},
                    onDismiss={dismissed=true;visible=false})
            }
        } }
        compose.onNodeWithTag("shortcut-orbit").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("shortcut-orbit").fetchSemanticsNode().boundsInRoot
        val expected=176*compose.activity.resources.displayMetrics.density
        assertTrue(bounds.width<=expected+1);assertTrue(bounds.height<=expected+1)
        clearEmulatorSystemAnr()
        val target=compose.onNodeWithTag("outside-shortcuts").fetchSemanticsNode().boundsInRoot.center
        val location=IntArray(2)
        compose.runOnUiThread { compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).getLocationOnScreen(location) }
        val now=android.os.SystemClock.uptimeMillis()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,
                target.x+location[0],target.y+location[1],0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        compose.waitUntil(10_000) { dismissed }
        compose.onNodeWithTag("shortcut-orbit").assertDoesNotExist()
        assertTrue(outside);assertFalse(chosen)
    }

    @Test fun pinnedRowIsInsideTheSummaryWithoutAnExtraPopup() {
        val settings=com.akira.island.data.SettingsRepo.current
        val extras=com.akira.island.data.ExtrasRepo.state.value
        val expanded=IslandBus.expanded.value
        val locked=IslandBus.locked.value
        val notice=IslandBus.notice.value
        val bounds=java.util.concurrent.atomic.AtomicReference(androidx.compose.ui.geometry.Rect.Zero)
        try {
            compose.runOnUiThread {
                com.akira.island.data.SettingsRepo.update { com.akira.island.data.IslandSettings(
                    language=com.akira.island.data.AppLanguage.EN,reduceEffects=true,autoUpdateCheck=false,
                    cardCharging=false,cardMedia=false,cardAlarm=false,cardLive=false,cardCall=false,
                    cardProgress=false,historyMinutes=0,quickActions=emptyList()) }
                com.akira.island.data.ExtrasRepo.update { com.akira.island.data.ExtraSettings(
                    apps=listOf("com.akira.island"),contacts=listOf(ContactShortcut("Example contact","content://com.android.contacts/contacts/991"))) }
                IslandBus.locked.value=false;IslandBus.expanded.value=true;IslandBus.notice.value=null
                compose.activity.setContent { Box(Modifier.fillMaxSize().background(Color(0xFF293344))) {
                    IslandOverlay(onBodyBounds={bounds.set(it)})
                } }
            }
            compose.onNodeWithTag("shortcut-row").assertIsDisplayed()
            compose.onNodeWithTag("app:com.akira.island").assertIsDisplayed()
            compose.onNodeWithTag("contact:content://com.android.contacts/contacts/991").assertIsDisplayed()
            compose.onNodeWithContentDescription("Open summary").assertDoesNotExist()
            compose.onRoot().captureToImage()
            val row=compose.onNodeWithTag("shortcut-row").fetchSemanticsNode().boundsInRoot
            assertTrue(row.height <= 60*compose.activity.resources.displayMetrics.density)
            assertTrue(IslandBus.expanded.value)
        } finally { compose.runOnUiThread {
            com.akira.island.data.SettingsRepo.update{settings};com.akira.island.data.ExtrasRepo.update{extras}
            IslandBus.expanded.value=expanded;IslandBus.locked.value=locked;IslandBus.notice.value=notice
        } }
    }

    @Test fun transferFieldsAppearImmediatelyWithoutInventingAnInitialZero() {
        compose.runOnUiThread { compose.activity.setContent {
            Box(Modifier.width(240.dp).background(Color.Black)) {
                TransferRateLines(null,null,waitingForNetwork=true)
            }
        } }
        compose.onNodeWithTag("network-incoming").assertTextEquals("↓ —")
        compose.onNodeWithTag("network-outgoing").assertTextEquals("↑ —")
    }

    @Test fun faceCardPreviewNeverChangesRealAuthenticationState() {
        val saved=com.akira.island.face.FaceUnlockState.phase.value
        val notice=IslandBus.notice.value
        compose.runOnUiThread { compose.activity.setContent {
            Column(Modifier.fillMaxWidth().background(Color(0xFF293344)).padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                FaceUnlockCard(FacePhase.SCANNING,true)
                FaceUnlockCard(FacePhase.SUCCESS,true)
                FaceUnlockCard(FacePhase.FAILURE,true)
            }
        } }
        compose.onAllNodesWithText("Face ID").assertCountEquals(3)
        val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null),"face-cards-refined.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        assertEquals(saved,com.akira.island.face.FaceUnlockState.phase.value)
        assertEquals(notice,IslandBus.notice.value)
    }
}
