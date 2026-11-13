package com.akira.island.face

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.core.IslandBus
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FaceFeatureDeviceTest {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=i.runOnMainSync(block)
    @Test fun providerIsProtectedAndDoesNotExportTheUpdateFileProvider() {
        val context=i.targetContext
        @Suppress("DEPRECATION")
        val face=context.packageManager.getProviderInfo(ComponentName(context,FaceShizukuProvider::class.java),0)
        assertTrue(face.exported);assertEquals("android.permission.INTERACT_ACROSS_USERS_FULL",face.readPermission)
        assertEquals(context.packageName+".shizuku",face.authority)
        @Suppress("DEPRECATION")
        val file=context.packageManager.resolveContentProvider(context.packageName+".fileprovider",0)!!
        assertFalse(file.exported);assertTrue(file.grantUriPermissions)
        assertNotEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission("android.permission.READ_LOGS"))
    }
    @Test fun shellReaderRefusesToRunInsideAnUnprivilegedAppProcess() {
        val context=i.targetContext
        val states=mutableListOf<Int>();val signals=mutableListOf<Int>()
        val service=FaceSourceService(context)
        service.start(object:IFaceEvents.Stub() {
            override fun onSignal(kind:Int,elapsedMillis:Long) { signals+=kind }
            override fun onSourceState(state:Int) { states+=state }
        },android.os.Process.myUid()/100000,1000)
        assertEquals(listOf(2),states);assertTrue(signals.isEmpty());service.stop()
    }
    @Test fun unavailableAccessOrDisabledSettingNeverBecomesAFaceUnlockAndStopsCleanly() {
        val context=i.targetContext;SettingsRepo.init(context)
        val saved=SettingsRepo.current
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=FaceUnlockController(context,scope)
        try {
            main { SettingsRepo.update{copy(faceUnlockOnly=false,enabled=true,autoUpdateCheck=false)};controller.start();controller.start() }
            if(FaceUnlockState.access()!=FaceAvailability.CONNECTING) {
                main { SettingsRepo.update{copy(faceUnlockOnly=true)} }
                val end=SystemClock.elapsedRealtime()+5000
                while(FaceUnlockState.availability.value in setOf(FaceAvailability.OFF,FaceAvailability.SERVICE_OFF)&&SystemClock.elapsedRealtime()<end) SystemClock.sleep(25)
                assertEquals(FacePhase.HIDDEN,FaceUnlockState.phase.value)
                assertTrue(FaceUnlockState.availability.value in setOf(FaceAvailability.SHIZUKU_STOPPED,
                    FaceAvailability.PAUSED,FaceAvailability.PERMISSION_NEEDED,FaceAvailability.BACKEND_UNSUPPORTED))
            }
            main { SettingsRepo.update{copy(faceUnlockOnly=false)} }
            SystemClock.sleep(200)
            assertEquals(FaceAvailability.OFF,FaceUnlockState.availability.value)
            assertEquals(FacePhase.HIDDEN,FaceUnlockState.phase.value)
        } finally { main { controller.stop();controller.stop();scope.cancel();SettingsRepo.update{saved} } }
        assertEquals(FacePhase.HIDDEN,FaceUnlockState.phase.value)
    }
}
