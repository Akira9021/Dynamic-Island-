package com.akira.island.security

import com.akira.island.update.UpdateDownloadPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class NetworkBoundsTest {
    @Test fun oversizedAndTruncatedBodiesAreRejectedBeforeTheyFillStorage() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(100)).copyBoundedTo(output, 40) }.isFailure)
        assertTrue(output.size() <= 40)
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(20)).copyBoundedTo(output, 100, 40) }.isFailure)
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(50)).copyBoundedTo(output, 100, 40) }.isFailure)
    }
    @Test fun normalUtf8AndExactLengthBodiesAreAccepted() {
        val body = "Погода".toByteArray()
        assertEquals("Погода", ByteArrayInputStream(body).readUtf8Bounded(body.size))
        val output = ByteArrayOutputStream()
        assertEquals(body.size.toLong(), ByteArrayInputStream(body).copyBoundedTo(output, 100, body.size.toLong()))
        assertArrayEquals(body, output.toByteArray())
    }
    @Test fun redirectPolicyRejectsHttpCredentialsOtherOriginsAndPorts() {
        val original = "https://lrclib.net/api/search"
        assertTrue(sameHttpsOrigin(original, "https://lrclib.net/api/get?q=test"))
        for (url in listOf("http://lrclib.net/get", "https://evil.test/get", "https://lrclib.net:8443/get", "https://x@lrclib.net/get", "https://lrclib.net.evil.test/get"))
            assertFalse(url, sameHttpsOrigin(original, url))
    }
    @Test fun updaterOnlyAcceptsAssetsFromTheActualRepository() {
        val path = "https://github.com/Akira9021/Dynamic-Island-/releases/download/v1.7.0/DynamicIsland.apk"
        assertTrue(UpdateDownloadPolicy.isReleaseAssetUrl(path))
        assertTrue(UpdateDownloadPolicy.isDownloadHop("https://release-assets.githubusercontent.com/a.apk?signature=test"))
        for (url in listOf(path.replace("https", "http"), path.replace("Akira9021", "SomeoneElse"), path.replace("github.com", "github.com.evil.test"), path+"?redirect=elsewhere", path.replace("v1.7.0", "%2e%2e")))
            assertFalse(url, UpdateDownloadPolicy.isReleaseAssetUrl(url))
        assertFalse(UpdateDownloadPolicy.isDownloadHop("https://evil.test/a.apk"))
    }
}
