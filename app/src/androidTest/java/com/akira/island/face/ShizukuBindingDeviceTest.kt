package com.akira.island.face

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional integration, explicitly requires the tester's approved non-root Shizuku backend. */
class ShizukuBindingDeviceTest {
    @Test fun approvedAdbBackendOpensTheTypedReaderAndDisablingStopsIt() {
        assumeTrue("Requires explicitly authorized Shizuku ADB backend",FaceUnlockState.access()==FaceAvailability.CONNECTING)
        val i=InstrumentationRegistry.getInstrumentation()
        fun main(block:()->Unit)=i.runOnMainSync(block)
        SettingsRepo.init(i.targetContext)
        val saved=SettingsRepo.current
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=FaceUnlockController(i.targetContext,scope)
        try {
            main { SettingsRepo.update{copy(enabled=true,faceUnlockOnly=true,autoUpdateCheck=false)};controller.start() }
            val deadline=SystemClock.elapsedRealtime()+20_000
            while(FaceUnlockState.availability.value==FaceAvailability.CONNECTING&&SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(50)
            assertEquals(FaceAvailability.WAITING_FOR_EVENTS,FaceUnlockState.availability.value)
            SystemClock.sleep(1500)
            assertEquals(FaceAvailability.WAITING_FOR_EVENTS,FaceUnlockState.availability.value)
            assertEquals("No biometric evidence, therefore no successful face effect",FacePhase.HIDDEN,FaceUnlockState.phase.value)
            main { SettingsRepo.update{copy(faceUnlockOnly=false)} }
            val end=SystemClock.elapsedRealtime()+5000
            while(FaceUnlockState.availability.value!=FaceAvailability.OFF&&SystemClock.elapsedRealtime()<end) SystemClock.sleep(25)
            assertEquals(FaceAvailability.OFF,FaceUnlockState.availability.value)
        } finally { main { controller.stop();scope.cancel();SettingsRepo.update{saved} } }
    }
}
