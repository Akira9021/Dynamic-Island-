package com.akira.island.security

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Follow only explicitly allowed HTTPS redirects, retaining normal platform TLS validation. */
internal fun openTrustedHttps(
    address: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    headers: Map<String, String> = emptyMap(),
    allowed: (String) -> Boolean = { sameHttpsOrigin(address, it) },
): HttpURLConnection {
    var next = address
    repeat(6) {
        require(allowed(next)) { "Untrusted download address" }
        val connection = (URL(next).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location") ?: error("Missing redirect destination")
            next = URL(URL(next), location).toExternalForm()
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
        connection.disconnect()
    }
    error("Too many redirects")
}

internal fun sameHttpsOrigin(origin: String, destination: String): Boolean = runCatching {
    val first = URI(origin)
    val next = URI(destination)
    first.scheme.equals("https", true) && next.scheme.equals("https", true) &&
        !first.host.isNullOrBlank() && first.host.equals(next.host, true) &&
        next.rawUserInfo == null && next.port in setOf(-1, 443) && next.fragment == null
}.getOrDefault(false)
