package com.akira.island.update

import java.net.URI

internal object UpdateDownloadPolicy {
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    private val assetHosts = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")

    fun isReleaseAssetUrl(value: String): Boolean = secureUri(value)?.let { uri ->
        val pieces = uri.path.orEmpty().trim('/').split('/')
        uri.host.equals("github.com", ignoreCase = true) && uri.fragment == null && uri.query == null &&
            pieces.size == 6 && pieces.take(4) == listOf("Akira9021", "Dynamic-Island-", "releases", "download") &&
            pieces[4].isNotBlank() && pieces[4] !in setOf(".", "..") && pieces[5].endsWith(".apk", ignoreCase = true)
    } == true

    fun isDownloadHop(value: String): Boolean = isReleaseAssetUrl(value) || secureUri(value)?.let {
        it.host.lowercase() in assetHosts && it.fragment == null
    } == true

    private fun secureUri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.rawUserInfo == null && it.port in setOf(-1, 443)
    }
}
