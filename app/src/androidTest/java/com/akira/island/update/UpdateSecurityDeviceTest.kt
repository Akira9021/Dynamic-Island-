package com.akira.island.update

import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class UpdateSecurityDeviceTest {
    @Test fun installerUsesTheChosenReleaseEvenAfterAFailedRecheck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val started = AtomicReference<Intent?>(null)
        val wrapper = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { started.set(intent) }
        }
        val version = UpdateChecker.currentVersion(context)
        val file = File(File(context.cacheDir, "update").apply { mkdirs() }, "DynamicIsland-$version.apk")
        val saved = UpdateChecker.state.value
        val savedDownload = UpdateChecker.download.value
        try {
            File(context.applicationInfo.sourceDir).copyTo(file, overwrite = true)
            val release = Release(version, "", "https://github.com/Akira9021/Dynamic-Island-/releases/download/v$version/DynamicIsland.apk", "", file.length())
            instrumentation.runOnMainSync {
                UpdateChecker.state.value = UpdateState.Error("offline recheck")
                UpdateChecker.download.value = DownloadState.Ready(file)
                UpdateChecker.install(wrapper, file, release)
            }
            val end = SystemClock.elapsedRealtime() + 30_000
            while (started.get() == null && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertNotNull("A failed recheck must not reject a verified cached APK", started.get())
            assertTrue(UpdateChecker.download.value is DownloadState.Ready)
        } finally {
            instrumentation.runOnMainSync { UpdateChecker.state.value = saved; UpdateChecker.download.value = savedDownload }
            file.delete()
        }
    }
    @Test fun archiveNeedsTheActualPackageVersionAndCertificate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val apk = File(context.applicationInfo.sourceDir)
        val version = UpdateChecker.currentVersion(context)
        assertTrue("The installed, correctly signed APK must be accepted", trustedUpdate(context, apk, version))
        assertFalse("A mismatched release version must be rejected", trustedUpdate(context, apk, "99.99.99"))
        assertFalse("Even a same-key test package is not an island update",
            trustedUpdate(context, File(instrumentation.context.applicationInfo.sourceDir), version))
        val broken = File(context.cacheDir, "broken-update.apk")
        try { broken.writeText("not an APK"); assertFalse(trustedUpdate(context, broken, version)) }
        finally { broken.delete() }
    }
    @Test fun apkWithAnotherSigningKeyCannotBeAnUpdate() {
        val path = InstrumentationRegistry.getArguments().getString("foreignSigningApk")
        org.junit.Assume.assumeTrue("Supply the disposable-key fixture described in the security report", path != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(path!!)
        assertTrue(file.isFile)
        assertFalse(trustedUpdate(context, file, UpdateChecker.currentVersion(context)))
    }
}
