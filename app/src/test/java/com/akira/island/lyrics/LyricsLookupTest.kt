package com.akira.island.lyrics

import com.akira.island.core.LyricLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Collections

class LyricsLookupTest {
    @Before fun prepare() { Stub.responder = null; Stub.requests.clear(); resetLookup() }
    @After fun cleanUp() { Stub.responder = null; resetLookup() }

    @Test fun `failed exact endpoint still searches synchronized lyrics`() = runBlocking {
        Stub.responder = { u -> when (u.host + u.path) {
            "lrclib.net/api/get" -> Reply(503)
            "lrclib.net/api/search" -> Reply(body = JSONArray().put(record(true)).toString())
            else -> Reply(404)
        } }
        val found = find()
        assertTrue(found?.synced == true)
        assertEquals(11_440L, found!!.lines.first().timeMs)
        assertFalse(Stub.requests.any { it.host == "music.163.com" })
    }

    @Test fun `malformed exact response does not prevent field search`() = runBlocking {
        Stub.responder = { u -> if (u.path == "/api/get") Reply(body = "{invalid") else Reply(body = JSONArray().put(record(true)).toString()) }
        assertTrue(find()?.synced == true)
    }

    @Test fun `failed field search still tries free search`() = runBlocking {
        Stub.responder = { u -> when {
            u.path == "/api/get" -> Reply(404)
            u.query.orEmpty().startsWith("q=") -> Reply(body = JSONArray().put(record(true)).toString())
            else -> Reply(503)
        } }
        assertTrue(find()?.synced == true)
        assertEquals(3, Stub.requests.size)
    }

    @Test fun `network failure keeps plain text without caching the failed lookup`() = runBlocking {
        var healthy = false
        Stub.responder = { u -> when {
            u.host == "music.163.com" -> Reply(404)
            u.path == "/api/get" -> Reply(body = record(false).toString())
            healthy -> Reply(body = JSONArray().put(record(true)).toString())
            else -> Reply(503)
        } }
        val plain = find()!!
        assertFalse(plain.synced)
        assertTrue(plain.lines.all { it.timeMs == 0L })
        healthy = true
        Stub.requests.clear()
        assertTrue(find()?.synced == true)
        assertTrue(Stub.requests.isNotEmpty())
    }

    @Test fun `explicit revalidation upgrades a cached plain result and retains synced cache`() = runBlocking {
        var synced = false
        Stub.responder = { u -> when {
            u.host == "music.163.com" -> Reply(404)
            u.path == "/api/get" -> Reply(body = record(false).toString())
            else -> Reply(body = if (synced) JSONArray().put(record(true)).toString() else "[]")
        } }
        assertFalse(find()!!.synced)
        synced = true
        Stub.requests.clear()
        assertFalse(find()!!.synced)
        assertTrue(Stub.requests.isEmpty())
        assertTrue(find(refresh = true)!!.synced)
        Stub.requests.clear()
        assertTrue(find(refresh = true)!!.synced)
        assertTrue("Ready synced recordings must not be fetched again", Stub.requests.isEmpty())
    }

    @Test fun `cached missing result can be revalidated without inventing lyrics`() = runBlocking {
        Stub.responder = { Reply(404) }
        assertNull(find())
        Stub.requests.clear()
        assertNull(find())
        assertTrue(Stub.requests.isEmpty())
        Stub.responder = { u -> if (u.path == "/api/get") Reply(body = record(true).toString()) else Reply(404) }
        assertTrue(find(refresh = true)?.synced == true)
    }

    @Test fun `rate limit stops provider fallback and applies across tracks`() = runBlocking {
        Stub.responder = { u -> if (u.host == "lrclib.net") Reply(429, headers = mapOf("Retry-After" to "120")) else Reply(404) }
        assertNull(find())
        assertEquals(1, Stub.requests.count { it.host == "lrclib.net" })
        assertNull(LyricsRepo.find("Different track", ARTIST, ALBUM, 193_000))
        assertEquals("Changing tracks cannot bypass provider backoff", 1, Stub.requests.count { it.host == "lrclib.net" })
    }

    @Test fun `service retry after prevents immediate requests to remaining endpoints`() = runBlocking {
        Stub.responder = { u -> if (u.host == "lrclib.net") Reply(503, headers = mapOf("Retry-After" to "60")) else Reply(404) }
        assertNull(find())
        assertEquals(1, Stub.requests.count { it.host == "lrclib.net" })
    }

    @Test fun `cancellation is not converted to missing or followed by more requests`() = runBlocking {
        Stub.responder = { throw CancellationException("Synthetic cancelled request") }
        try {
            find()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, Stub.requests.size)
        Stub.responder = { Reply(body = record(true).toString()) }
        assertTrue(find()?.synced == true)
    }

    @Test fun `selection still rejects other recordings and artists`() = runBlocking {
        val choices = JSONArray()
            .put(record(true).put("trackName", "$TITLE (Live)"))
            .put(record(true).put("artistName", "Other Artist"))
            .put(record(true).put("duration", 121))
            .put(record(false))
            .put(record(true))
        Stub.responder = { u -> if (u.path == "/api/get") Reply(404) else Reply(body = choices.toString()) }
        assertEquals(11_440L, find()!!.lines.first().timeMs)
    }

    @Test fun `untrusted redirect is rejected while a separate trusted search can recover`() = runBlocking {
        Stub.responder = { u -> if (u.path == "/api/get") Reply(302, headers = mapOf("Location" to "https://untrusted.invalid/lyrics"))
            else Reply(body = JSONArray().put(record(true)).toString()) }
        assertTrue(find()?.synced == true)
        assertFalse(Stub.requests.any { it.host == "untrusted.invalid" })
    }

    @Test fun `plain and missing cache expire but synchronized cache survives refresh`() {
        var now = 1_000L
        val cache = LyricsCache { now }
        val plain = FoundLyrics(listOf(LyricLine(0, "Synthetic")), false)
        val synced = FoundLyrics(listOf(LyricLine(10, "Synthetic")), true)
        cache.put("plain", plain); cache.put("missing", null); cache.put("synced", synced)
        now += 119_999
        assertSame(plain, cache.get("plain")!!.lyrics)
        assertNotNull(cache.get("missing"))
        now++
        assertNull(cache.get("plain")); assertNull(cache.get("missing"))
        assertSame(synced, cache.get("synced", refreshUnsynced = true)!!.lyrics)
    }

    @Test fun `lyrics cache retains its forty item LRU bound`() {
        val cache = LyricsCache { 1_000L }
        repeat(40) { cache.put("$it", null) }
        assertNotNull(cache.get("0"))
        cache.put("40", null)
        assertNull(cache.get("1")); assertNotNull(cache.get("0")); assertNotNull(cache.get("40"))
    }

    @Test fun `provider cooldown expires independently and cannot be shortened`() {
        var now = 100L
        val backoff = LyricsProviderBackoff { now }
        backoff.defer("one", 120_000)
        now += 1_000
        backoff.defer("one", 1_000)
        assertEquals(119_000, backoff.remainingMillis("one"))
        assertEquals(0, backoff.remainingMillis("two"))
        now += 119_000
        assertEquals(0, backoff.remainingMillis("one"))
        backoff.defer("one", Long.MAX_VALUE)
        assertTrue(backoff.remainingMillis("one") > 0)
    }

    @Test fun `retry after accepts seconds and HTTP date with safe invalid fallback`() {
        val now = Instant.parse("2026-11-12T00:00:00Z")
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(120).atZone(ZoneOffset.UTC))
        assertEquals(120_000, lyricsRetryAfterMillis("120"))
        assertEquals(120_000, lyricsRetryAfterMillis(date, now.toEpochMilli()))
        assertEquals(1_000, lyricsRetryAfterMillis("0"))
        assertEquals(60_000, lyricsRetryAfterMillis("invalid"))
        assertEquals(60_000, lyricsRetryAfterMillis("-5"))
        assertEquals(Long.MAX_VALUE, lyricsRetryAfterMillis(Long.MAX_VALUE.toString()))
    }

    private suspend fun find(refresh: Boolean = false) = LyricsRepo.find(TITLE, ARTIST, ALBUM, 193_000, refresh)

    private fun record(synced: Boolean) = JSONObject().put("trackName", TITLE).put("artistName", ARTIST)
        .put("albumName", ALBUM).put("duration", 194.0).put("plainLyrics", "Synthetic plain text")
        .put("syncedLyrics", if (synced) "[00:11.44]Synthetic verse\n[00:20]Synthetic next verse" else JSONObject.NULL)

    /** Reset only this worker's in-memory test state, never app preferences or production files. */
    private fun resetLookup() {
        for ((field, map) in listOf("cache" to "entries", "backoff" to "deadlines")) {
            val owner = LyricsRepo::class.java.getDeclaredField(field).apply { isAccessible = true }.get(null)
            (owner.javaClass.getDeclaredField(map).apply { isAccessible = true }.get(owner) as MutableMap<*, *>).clear()
        }
    }

    private data class Reply(val status: Int = 200, val body: String = "", val headers: Map<String, String> = emptyMap())

    private object Stub {
        val requests = Collections.synchronizedList(mutableListOf<URL>())
        @Volatile var responder: ((URL) -> Reply)? = null
        private val original = URL("https://localhost/")
        init {
            URL.setURLStreamHandlerFactory { protocol -> if (protocol == "https") object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    val route = responder ?: return URL(original, url.toExternalForm()).openConnection()
                    requests.add(url)
                    val reply = route(url)
                    return object : HttpURLConnection(url) {
                        override fun connect() = Unit
                        override fun disconnect() = Unit
                        override fun usingProxy() = false
                        override fun getResponseCode() = reply.status
                        override fun getHeaderField(name: String?) = reply.headers[name]
                        override fun getInputStream() = ByteArrayInputStream(reply.body.toByteArray(Charsets.UTF_8))
                    }
                }
            } else null }
        }
    }

    private companion object {
        const val TITLE = "Буду твоей пальмой!"
        const val ARTIST = "MORGENSHTERN"
        const val ALBUM = "Улыбнись, дурак!"
    }
}
