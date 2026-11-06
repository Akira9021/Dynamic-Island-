package com.akira.island.security

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Limit the bytes while reading, before a remote response can exhaust memory or storage. */
internal fun InputStream.copyBoundedTo(
    output: OutputStream,
    maxBytes: Long,
    expectedBytes: Long = 0,
    onChunk: (Long) -> Unit = {},
): Long {
    require(maxBytes > 0 && expectedBytes in 0..maxBytes)
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        copied += count
        require(copied <= maxBytes && (expectedBytes == 0L || copied <= expectedBytes)) { "Response exceeds its size limit" }
        output.write(buffer, 0, count)
        onChunk(copied)
    }
    require(expectedBytes == 0L || copied == expectedBytes) { "Incomplete response" }
    return copied
}

internal fun InputStream.readUtf8Bounded(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    copyBoundedTo(output, maxBytes.toLong())
    return output.toString(Charsets.UTF_8.name())
}
