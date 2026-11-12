package com.akira.island.lyrics

import com.akira.island.core.LyricLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URI
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import com.akira.island.security.openTrustedHttps
import com.akira.island.security.readUtf8Bounded
import kotlin.math.abs

/** Найденный текст: [synced] — с настоящими метками времени; иначе обычный текст без караоке. */
data class FoundLyrics(val lines: List<LyricLine>, val synced: Boolean)

/**
 * Текст песен: сначала LRCLIB (https://lrclib.net), как в ПК-версии острова,
 * потом NetEase — там много русской и азиатской музыки.
 */
object LyricsRepo {
    private const val UA = "DynamicIslandAndroid/1.2 (https://github.com/Akira9021/Dynamic-Island-)"

    private val cache = LyricsCache()
    private val backoff = LyricsProviderBackoff()

    private val noise = Regex(
        """\s*[(\[][^)\]]*(official|video|lyric|audio|clip|visuali[sz]er|hd|4k|mv|prod\.?)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val feat = Regex("""\s*[(\[]?\s*(feat\.?|ft\.?|при уч\.?)\s.*$""", RegexOption.IGNORE_CASE)

    suspend fun find(title: String, artist: String, album: String?, durationMs: Long, refreshUnsynced: Boolean = false): FoundLyrics? =
        withContext(Dispatchers.IO) {
            val lookupContext = currentCoroutineContext()
            lookupContext.ensureActive()
            var t = title
            var a = artist
            // У видео из браузера исполнитель часто сидит в названии: «Исполнитель - Песня»
            if (a.isBlank() && " - " in t) {
                a = t.substringBefore(" - ").trim()
                t = t.substringAfter(" - ").trim()
            }
            t = t.replace(noise, "").replace(feat, "").trim()
            // «Исполнитель 1, Исполнитель 2» и «A & B» — для поиска хватит первого
            val mainArtist = a.split(",", "&", " x ", " feat", " ft.").first().trim()
            val key = LyricTiming.identity(t, mainArtist, album, durationMs)
            cache.get(key, refreshUnsynced)?.let { return@withContext it.lyrics }

            var failed = false
            var plain: String? = null
            val found = attempt({ failed = true }) {
                lrclib(t, mainArtist, album, durationMs,
                    onPlain = { plain = plain ?: it }, onError = { failed = true },
                    checkActive = { lookupContext.ensureActive() })
            }
                ?: attempt({ failed = true }) {
                    lookupContext.ensureActive()
                    netease(t, mainArtist, durationMs)
                }
                ?: plain?.let { FoundLyrics(LyricTiming.plain(it), synced = false) }
            currentCoroutineContext().ensureActive()
            // Сетевую ошибку не кешируем — попробуем при следующем включении трека
            if (found?.synced == true || !failed) cache.put(key, found)
            found
        }

    private inline fun attempt(onError: () -> Unit, stopOnBackoff: Boolean = false, block: () -> FoundLyrics?): FoundLyrics? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderBackoffException) {
            if (stopOnBackoff) throw e
            onError()
            null
        } catch (e: Exception) {
            onError()
            null
        }

    // LRCLIB

    private fun lrclib(
        title: String, artist: String, album: String?, durationMs: Long,
        onPlain: (String) -> Unit, onError: () -> Unit, checkActive: () -> Unit,
    ): FoundLyrics? {
        if (title.isBlank()) return null
        if (artist.isNotBlank() && durationMs > 0) {
            val params = buildMap {
                put("track_name", title)
                put("artist_name", artist)
                if (!album.isNullOrBlank()) put("album_name", album)
                put("duration", (durationMs / 1000).toString())
            }
            attempt(onError, stopOnBackoff = true) {
                checkActive()
                val body = get("https://lrclib.net/api/get?" + encode(params)) ?: return@attempt null
                val o = JSONObject(body)
                val lines = synced(o)?.let(::parse)?.takeIf { it.any { line -> line.text.isNotBlank() } }
                if (lines != null) FoundLyrics(lines, true) else {
                    plainOf(o)?.let(onPlain)
                    null
                }
            }?.let { return it }
        }
        val byFields = buildMap {
            put("track_name", title)
            if (artist.isNotBlank()) put("artist_name", artist)
        }
        attempt(onError, stopOnBackoff = true) {
            checkActive()
            pickLrclib(get("https://lrclib.net/api/search?" + encode(byFields)), title, artist, album, durationMs, onPlain)
        }?.let { return it }
        // Свободный поиск находит то, что не совпало по полям (другое написание, транслит)
        val q = mapOf("q" to "$artist $title".trim())
        return attempt(onError, stopOnBackoff = true) {
            checkActive()
            pickLrclib(get("https://lrclib.net/api/search?" + encode(q)), title, artist, album, durationMs, onPlain)
        }
    }

    private fun pickLrclib(body: String?, title: String, artist: String, album: String?, durationMs: Long, onPlain: (String) -> Unit): FoundLyrics? {
        body ?: return null
        val arr = JSONArray(body)
        var best: String? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!LyricTiming.matchesTitle(title, o.optString("trackName"))) continue
            if (artist.isNotBlank() && !LyricTiming.normalized(o.optString("artistName")).contains(LyricTiming.normalized(artist))) continue
            val diff = if (durationMs > 0) abs(o.optDouble("duration", 0.0) - durationMs / 1000.0) else 0.0
            if (diff > MAX_DIFF_S) continue
            val lrc = synced(o)
            if (lrc == null) {
                plainOf(o)?.let(onPlain)
                continue
            }
            val albumPenalty = if (!album.isNullOrBlank() && LyricTiming.normalized(album) != LyricTiming.normalized(o.optString("albumName"))) 4.0 else 0.0
            val score = diff + albumPenalty
            if (score < bestScore) {
                best = lrc
                bestScore = score
            }
        }
        return best?.let { parse(it) }?.takeIf { it.any { line -> line.text.isNotBlank() } }?.let { FoundLyrics(it, true) }
    }

    private fun synced(o: JSONObject): String? =
        if (o.isNull("syncedLyrics")) null else o.optString("syncedLyrics").takeIf { it.isNotBlank() }

    private fun plainOf(o: JSONObject): String? =
        if (o.isNull("plainLyrics")) null else o.optString("plainLyrics").takeIf { it.isNotBlank() }

    // NetEase

    private fun netease(title: String, artist: String, durationMs: Long): FoundLyrics? {
        if (title.isBlank()) return null
        val q = URLEncoder.encode("$artist $title".trim(), "UTF-8")
        val body = get("https://music.163.com/api/search/get?s=$q&type=1&limit=10", netease = true) ?: return null
        val songs = JSONObject(body).optJSONObject("result")?.optJSONArray("songs") ?: return null
        val wanted = norm(title)
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val name = norm(song.optString("name"))
            if (name.isEmpty() || name != wanted) continue
            val artists = song.optJSONArray("artists")
            if (artist.isNotBlank() && (artists == null || (0 until artists.length()).none {
                    norm(artists.optJSONObject(it)?.optString("name").orEmpty()).contains(norm(artist))
                })) continue
            val dur = song.optLong("duration", 0)
            if (durationMs > 0 && dur > 0 && abs(dur - durationMs) > MAX_DIFF_S * 1000) continue
            val lyric = get("https://music.163.com/api/song/lyric?id=${song.optLong("id")}&lv=1&kv=1&tv=-1", netease = true)
                ?: continue
            val lrc = JSONObject(lyric).optJSONObject("lrc")?.optString("lyric").orEmpty()
            val lines = parse(lrc).filterNot { isCredit(it.text) }
            if (lines.size >= 4) return FoundLyrics(lines, true)
        }
        return null
    }

    /** NetEase в начале пишет авторов: «作词 : …», «Composer: …» */
    private val credit = Regex("""^(作词|作曲|编曲|制作人|lyrics|composer|producer|arranger|автор|музыка|слова)\s*[:：]""", RegexOption.IGNORE_CASE)
    private fun isCredit(text: String): Boolean = credit.containsMatchIn(text)

    private fun norm(s: String) = s.lowercase().replace(noise, "").filter { it.isLetterOrDigit() }

    // Общее

    private fun encode(params: Map<String, String>) =
        params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    private fun get(url: String, netease: Boolean = false): String? {
        val provider = URI(url).host
        if (backoff.remainingMillis(provider) > 0) throw ProviderBackoffException()
        val headers = if (netease) mapOf("Referer" to "https://music.163.com",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        else mapOf("User-Agent" to UA)
        val c = openTrustedHttps(url, 6000, 8000, headers)
        return try {
            val status = c.responseCode
            val retryAfter = c.getHeaderField("Retry-After")
            if (status == 429 || (status != 200 && retryAfter != null)) {
                backoff.defer(provider, lyricsRetryAfterMillis(retryAfter))
                throw ProviderBackoffException()
            }
            if (status == 404) return null
            if (status != 200) error("HTTP $status")
            c.inputStream.use { it.readUtf8Bounded(2_000_000) }
        } finally {
            c.disconnect()
        }
    }

    fun parse(lrc: String): List<LyricLine> = LyricTiming.parse(lrc)

    /** Насколько может отличаться длительность, чтобы это была та же песня, секунд. */
    private const val MAX_DIFF_S = 3.0

    private class ProviderBackoffException : IOException("Lyrics provider temporarily unavailable")
}

/** Synced recordings keep their existing LRU lifetime; incomplete results can be revalidated. */
internal class LyricsCache(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    internal data class Entry(val lyrics: FoundLyrics?, val storedAt: Long)
    private val entries = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 40
    }

    @Synchronized fun get(key: String, refreshUnsynced: Boolean = false): Entry? {
        val entry = entries[key] ?: return null
        if (entry.lyrics?.synced != true && (refreshUnsynced || nowMillis() - entry.storedAt >= 120_000)) {
            entries.remove(key)
            return null
        }
        return entry
    }

    @Synchronized fun put(key: String, lyrics: FoundLyrics?) {
        entries[key] = Entry(lyrics, nowMillis())
    }
}

/** Provider deadlines are shared across tracks, preventing a track change from bypassing 429. */
internal class LyricsProviderBackoff(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val deadlines = HashMap<String, Long>()

    @Synchronized fun remainingMillis(provider: String): Long {
        val deadline = deadlines[provider] ?: return 0
        val now = nowMillis()
        if (now >= deadline) {
            deadlines.remove(provider)
            return 0
        }
        return (deadline - now).takeIf { it >= 0 } ?: Long.MAX_VALUE
    }

    @Synchronized fun defer(provider: String, delayMillis: Long) {
        val now = nowMillis()
        val delay = delayMillis.coerceAtLeast(1_000)
        val deadline = if (now > Long.MAX_VALUE - delay) Long.MAX_VALUE else now + delay
        deadlines[provider] = maxOf(deadlines[provider] ?: Long.MIN_VALUE, deadline)
    }
}

internal fun lyricsRetryAfterMillis(value: String?, nowWallMillis: Long = System.currentTimeMillis()): Long {
    val header = value?.trim().orEmpty()
    header.toLongOrNull()?.takeIf { it >= 0 }?.let { return TimeUnit.SECONDS.toMillis(it).coerceAtLeast(1_000) }
    return runCatching {
        val date = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        (date - nowWallMillis).coerceAtLeast(1_000)
    }.getOrDefault(60_000)
}
