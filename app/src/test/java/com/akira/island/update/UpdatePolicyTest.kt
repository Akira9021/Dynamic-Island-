package com.akira.island.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun releaseIgnoresForeignAndDebugAssetsAndKeepsTheRealApk() {
        val result = UpdateChecker.parseRelease("""{
            "tag_name":"v1.8.0","body":"New version","assets":[
                {"name":"DynamicIsland.apk","browser_download_url":"https://evil.test/app.apk","size":100},
                {"name":"app-debug.apk","browser_download_url":"https://github.com/Akira9021/Dynamic-Island-/releases/download/v1.8.0/app-debug.apk","size":100},
                {"name":"DynamicIsland.apk","browser_download_url":"https://github.com/Akira9021/Dynamic-Island-/releases/download/v1.8.0/DynamicIsland.apk","size":100}
            ]}""")!!
        assertEquals("1.8.0", result.version)
        assertTrue(result.apkUrl!!.endsWith("/v1.8.0/DynamicIsland.apk"))
        assertEquals(100L, result.sizeBytes)
    }
    @Test fun oversizedReleaseCanBeShownButItsApkCannotBeDownloaded() {
        val result = UpdateChecker.parseRelease("""{"tag_name":"v1.8.0","assets":[
            {"name":"app.apk","browser_download_url":"https://github.com/Akira9021/Dynamic-Island-/releases/download/v1.8.0/app.apk","size":999999999999}
        ]}""")!!
        assertNull(result.apkUrl)
    }
}
