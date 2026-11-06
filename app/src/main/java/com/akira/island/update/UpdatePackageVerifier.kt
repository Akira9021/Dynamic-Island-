package com.akira.island.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Android still performs the final install verification; reject foreign APKs before opening it. */
internal fun trustedUpdate(context: Context, file: File, version: String): Boolean = runCatching {
    require(file.isFile && file.length() in 1..UpdateDownloadPolicy.MAX_APK_BYTES)
    val manager = context.packageManager
    @Suppress("DEPRECATION")
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val archive = manager.getPackageArchiveInfo(file.path, flags) ?: return false
    val installed = manager.getPackageInfo(context.packageName, flags)
    require(archive.packageName == context.packageName && archive.versionName == version)
    fun signers(info: PackageInfo, history: Boolean): Set<String> {
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.let {
            if (history && !it.hasMultipleSigners()) it.signingCertificateHistory else it.apkContentsSigners
        } else info.signatures
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }
    val current = signers(installed, false)
    val incoming = signers(archive, false)
    current.isNotEmpty() && (current == incoming || current.size == 1 && incoming.size == 1 &&
        signers(archive, true).containsAll(current))
}.getOrDefault(false)
