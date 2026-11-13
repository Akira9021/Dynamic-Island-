package com.akira.island.face

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** logd supplies UID/PID; applications cannot authenticate their messages by spoofing a tag. */
internal class SystemLogRecord(val uid: Int, val pid: Int, val wallMs: Long, val tag: String, val message: String)
internal data class FaceLogSources(val serverPid: Int, val uiPid: Int, val uiUid: Int, val keyPid: Int, val user: Int)

/** Fixed Android logger_entry format. Unknown/old headers fail closed, never fall back to raw text. */
internal object FaceLogReader {
    fun read(input: InputStream): SystemLogRecord? {
        val first = input.read()
        if (first < 0) return null
        val prefix = byteArrayOf(first.toByte(), input.byte(), input.byte(), input.byte())
        val b = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN)
        val length = b.short.toInt() and 0xffff
        val headerSize = b.short.toInt() and 0xffff
        require(headerSize in 28..64 && length in 3..16_384) { "Unsupported system event format" }
        val header = ByteBuffer.wrap(input.bytes(headerSize - 4)).order(ByteOrder.LITTLE_ENDIAN)
        val pid = header.int
        header.int // tid
        val seconds = header.int.toLong() and 0xffffffffL
        val nanos = header.int.toLong() and 0xffffffffL
        header.int // log buffer id
        val uid = header.int
        require(nanos < 1_000_000_000L) { "Invalid event time" }
        val payload = input.bytes(length)
        val endTag = payload.indexOf(0.toByte())
        require(endTag in 2 until payload.size) { "Invalid event tag" }
        val tag = String(payload, 1, endTag - 1, Charsets.UTF_8)
        val start = endTag + 1
        val end = (start until payload.size).firstOrNull { payload[it] == 0.toByte() } ?: payload.size
        return SystemLogRecord(uid, pid, seconds * 1_000 + nanos / 1_000_000, tag,
            String(payload, start, end - start, Charsets.UTF_8))
    }
    private fun InputStream.byte(): Byte = read().takeIf { it >= 0 }?.toByte() ?: throw EOFException()
    private fun InputStream.bytes(size: Int): ByteArray = ByteArray(size).also { target ->
        var offset = 0
        while (offset < size) {
            val n = read(target, offset, size - offset)
            if (n < 0) throw EOFException()
            if (n > 0) offset += n
        }
    }
}

/** Original strict parser for documented AOSP message fields. Unknown OEM formats are ignored. */
internal class FaceLogParser(private val sources: FaceLogSources) {
    fun parse(record: SystemLogRecord): FaceSignal? {
        val m = record.message
        if (m.length > 8_192) return null
        if (sources.serverPid > 0 && record.pid == sources.serverPid && record.uid == 1000 &&
            record.tag == "Biometrics/AuthenticationClient") {
            val result = authentication.matchEntire(m) ?: return null
            if (result.groupValues[3] != "com.android.systemui" || result.groupValues[4] != "false") return null
            if (field(m, "userId")?.toIntOrNull() != sources.user) return null
            val face = result.groupValues[5].contains(".face.")
            if (result.groupValues[1] != "true") return if (face) FaceSignal.REJECTED else null
            if ((result.groupValues[2].toLongOrNull() ?: 0L) <= 0) return null
            return if (face) FaceSignal.ACCEPTED else FaceSignal.OTHER_UNLOCK
        }
        if (sources.uiPid > 0 && sources.uiUid >= 1000 && record.pid == sources.uiPid && record.uid == sources.uiUid &&
            record.tag == "BiometricStatusRepositoryImpl") {
            val result = state.matchEntire(m) ?: return null
            val body = result.groupValues[2]
            val type = field(body, "biometricSourceType") ?: return null
            val reason = field(body, "requestReason") ?: return null
            if (type !in setOf("FACE", "FINGERPRINT")) return null
            if (reason != "DeviceEntryAuthentication" && !(result.groupValues[1] == "Stopped" && reason == "NotRunning")) return null
            val user = field(body, "userId")?.toIntOrNull()
            if (result.groupValues[1] in setOf("Succeeded", "Failed") && user != sources.user) return null
            if (type == "FINGERPRINT") return if (result.groupValues[1] == "Succeeded") FaceSignal.OTHER_UNLOCK else null
            return when (result.groupValues[1]) {
                "Started" -> FaceSignal.START
                "Succeeded" -> FaceSignal.ACCEPTED
                "Failed", "Error" -> FaceSignal.REJECTED
                "Stopped" -> FaceSignal.STOPPED
                else -> null
            }
        }
        // Positive non-credential entry is required; absence of a credential message proves nothing.
        if (sources.keyPid > 0 && record.pid == sources.keyPid && record.uid == 1017 && record.tag == "keystore2") {
            val match = credential.find(m) ?: return null
            if (match.groupValues[1].toIntOrNull() == sources.user) {
                return if (match.groupValues[2] == "true") FaceSignal.OTHER_UNLOCK else FaceSignal.NON_CREDENTIAL_ENTRY
            }
        }
        return null
    }
    private fun field(body: String, key: String): String? {
        val matches = Regex("(?:^|[, {])" + key + "=([^,)}]+)").findAll(body).toList()
        return matches.singleOrNull()?.groupValues?.get(1)?.trim()
    }
    private companion object {
        val authentication = Regex("onAuthenticated\\((true|false)\\), ID:(-?\\d+), Owner: ([A-Za-z0-9_.]+), isBP: (true|false), .*clientMonitor: \\{\\[\\d+\\] (com\\.android\\.server\\.biometrics\\.sensors\\.(?:face|fingerprint)\\.(?:aidl|hidl)\\.[A-Za-z]*AuthenticationClient), .*\\}")
        val state = Regex("authenticationState updated: (Started|Succeeded|Failed|Error|Stopped)\\((.*)\\)")
        val credential = Regex("on_device_unlocked\\(user_id=(\\d+), password\\.is_some\\(\\)=(true|false)\\)")
    }
}
