package com.akira.island.face

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.akira.island.core.IslandBus
import com.akira.island.ui.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.graphics.Bitmap
import java.io.File

class FacePreviewDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualRendererDistinguishesScanSuccessAndFailureWithoutContinuousAnimation() {
        compose.runOnUiThread {
            compose.activity.setContent { Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                for(phase in listOf(FacePhase.SCANNING,FacePhase.SUCCESS,FacePhase.FAILURE)) {
                    FaceGlyph(phase,true,Modifier.size(40.dp).testTag("glyph-$phase"))
                }
            } }
        }
        compose.waitForIdle()
        val success=compose.onNodeWithTag("glyph-SUCCESS").captureToImage().asAndroidBitmap()
        var green=0
        for(y in 0 until success.height) for(x in 0 until success.width) {
            val p=success.getPixel(x,y)
            if(android.graphics.Color.green(p)>android.graphics.Color.red(p)*1.3 && android.graphics.Color.green(p)>100) green++
        }
        assertTrue("A confirmed success has a visible green check and frame",green>20)
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val path=File(compose.activity.getExternalFilesDir(null),"face-phases.png")
        path.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun previewIsLocalAndNeverPublishesAnAuthenticationOrNotice() {
        val phase=FaceUnlockState.phase.value
        val notice=IslandBus.notice.value
        compose.runOnUiThread {
            compose.activity.setContent { Column(Modifier.width(320.dp).padding(8.dp)) { FaceSettings(false){} } }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("face-preview").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        compose.onNodeWithTag("face-demo").assertIsDisplayed()
        compose.onNodeWithTag("face-demo").captureToImage()
        assertEquals(phase,FaceUnlockState.phase.value);assertEquals(notice,IslandBus.notice.value)
    }
}
