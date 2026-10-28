package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.akira.island.R
import com.akira.island.core.LiveInfo
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Turn
import com.akira.island.island.Ios
import com.akira.island.island.liveTint
import android.os.SystemClock
import com.akira.island.core.IslandBus
import com.akira.island.core.Lang
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Разбор постоянных уведомлений навигаторов, такси и доставки. Кроме обычных полей берётся то, что
 * приложение рисует своей раскладкой (RemoteViews): у Яндекс Навигатора расстояние и время часто
 * лежат только там, а в заголовке — одно «Навигатор запущен».
 */
object LiveParser {
    /** Что нашлось в раскладке уведомления: тексты по порядку и маленькая картинка (значок манёвра). */
    private class Remote(val texts: List<String>, val image: Bitmap?, val note: String)

    private val distance = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?(км|метров|метра|м|km|mi|ft|m)\.?(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""\b\d{1,2}:\d{2}\b""")
    private val minutes = Regex("""\b\d{1,3}\s?(мин|ч|min|h)\b""", RegexOption.IGNORE_CASE)

    fun parse(context: Context, sbn: StatusBarNotification, fallbackTitle: String): LiveInfo? {
        val n = sbn.notification
        val ex = n.extras
        val kind = LiveApps.kind(sbn.packageName, n.category) ?: return null
        val head = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val sub = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT))
            ?.toString()?.trim().orEmpty()
        val fields = listOf(head, text, big, sub)
        // Собрать чужую раскладку — дорого, а обычные уведомления (акции, «заказ доставлен») её не требуют
        val remote = if (!sbn.isOngoing) {
            Remote(emptyList(), null, tr("не разбиралась: уведомление не постоянное", "not parsed: the notification isn't ongoing"))
        } else if (findDistance(fields) != null && findEta(fields) != null) {
            Remote(emptyList(), null, tr("не разбиралась: расстояние и время есть в полях", "not parsed: distance and time are in the fields"))
        } else {
            remoteFor(context, sbn.key, n)
        }
        LiveDebug.record(context, sbn, head, text, big, sub, remote.texts, remote.note)
        if (!sbn.isOngoing) return null
        if (head.isEmpty() && text.isEmpty() && remote.texts.isEmpty()) return null

        val all = fields + remote.texts
        val hint = instructionOf(all)
        val turn = hint?.let { detectTurn(listOf(it)) }
        val art = if (turn == null) {
            runCatching { n.getLargeIcon()?.loadDrawable(context)?.toBitmap(128, 128) }.getOrNull() ?: remote.image
        } else {
            null
        }
        val instruction = hint ?: (listOf(text, big) + remote.texts)
            .firstOrNull { it.isNotBlank() && it != head && it.length > 8 && distance.matchEntire(it.trim()) == null }
            .orEmpty()
        return LiveInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            kind = kind,
            title = head.ifEmpty { fallbackTitle },
            text = instruction,
            sub = sub,
            icon = NotificationParser.icon(context, sbn.packageName),
            art = art?.asImageBitmap(),
            postTime = sbn.postTime,
            open = n.contentIntent,
            distance = findDistance(all),
            eta = findEta(all),
            turn = turn,
        )
    }

    private val lastBanner = HashMap<String, Long>()

    /** Показать широкую плашку, но не чаще раза в минуту для одного приложения. */
    fun postBanner(context: Context, l: LiveInfo) {
        if (!SettingsRepo.current.cardLive) return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastBanner[l.pkg] ?: -120_000L) < 60_000) return
        lastBanner[l.pkg] = now
        IslandBus.post(banner(context, l))
    }

    /**
     * Новая активность из уведомления: плашку показываем через полторы секунды — за это время остров
     * успевает прочитать экран навигатора, и на плашке уже есть расстояние и стрелка.
     */
    fun postBannerLater(context: Context, key: String) {
        IslandBus.scope.launch {
            delay(1600)
            val l = IslandBus.live.value[key] ?: return@launch
            postBanner(context, NavScreen.merge(l, NavScreen.data.value[l.pkg]))
        }
    }

    /** Широкая плашка при запуске: iPhone показывает новую «живую активность» развёрнутой, потом сворачивает. */
    fun banner(context: Context, l: LiveInfo): Notice = Notice(
        title = l.title,
        icon = Ios.load(context, R.drawable.ios_navigate),
        tint = liveTint(l.kind),
        style = NoticeStyle.LIVE,
        live = l,
        extraMs = 2_000,
    )

    /** «300 м», «3,7 км» — сначала короткие подписи, где расстояние стоит отдельно, потом любое упоминание. */
    internal fun findDistance(texts: List<String>): String? {
        val cleaned = texts.map { it.trim() }.filter { it.isNotEmpty() }
        cleaned.firstOrNull { it.length <= 12 && distance.matchEntire(it) != null }?.let { return normalize(it) }
        for (t in cleaned) distance.find(t)?.let { return normalize(it.value) }
        return null
    }

    private fun normalize(s: String) = units(s.trim().trimEnd('.').replace(Regex("""\s+"""), " "))

    private val ruUnit = Regex("""(\d+)(?:,(\d+))?\s?(км|метров|метра|мин|м|ч)\.?(?!\p{L})""")

    /**
     * Единицы на языке острова: по-английски «4,2 км» → «4.2 km», «12 мин» → «12 min», по-индонезийски и
     * по-португальски — латиницей, но с запятой. По-русски и по-казахски — как прислал навигатор.
     */
    fun units(s: String): String {
        val c = Lang.code
        if (c == "ru" || c == "kk") return s
        return ruUnit.replace(s) { m ->
            val (whole, frac, unit) = m.destructured
            val n = if (frac.isEmpty()) whole else if (c == "en") "$whole.$frac" else "$whole,$frac"
            val u = when (unit) {
                "км" -> "km"
                "мин" -> if (c == "id") "mnt" else "min"
                "ч" -> if (c == "id") "j" else "h"
                else -> "m"
            }
            "$n $u"
        }
    }

    /** «15:52 · 9 мин» из любых подписей уведомления. */
    internal fun findEta(texts: List<String>): String? {
        val joined = texts.joinToString(" ")
        val time = clock.find(joined)?.value
        val min = minutes.find(joined)?.value
        return listOfNotNull(time?.let { trf("Прибытие в {0}", "Arrive {0}", it) }, min?.replace(Regex("""\s+"""), " ")?.let(::units))
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val maneuverWords = listOf(
        "поверн", "держит", "съезд", "съехать", "развер", "разворот", "прямо", "налево", "направо", "левее", "правее",
        "turn", "keep", "exit", "u-turn", "continue",
    )
    private val notInstruction = listOf("по шагам", "по gps", "шаг ")

    /** Подсказка навигатора: «Через 300 м поверните направо на Тверскую», «Держитесь левее», «Съезд 2». */
    internal fun instructionOf(texts: List<String>): String? = texts.map { it.trim() }.firstOrNull { t ->
        val l = t.lowercase()
        t.length in 6..90 && maneuverWords.any { it in l } && notInstruction.none { it in l }
    }

    /** Куда поворачивать, по тексту подсказки или описанию значка манёвра. */
    internal fun detectTurn(texts: List<String>): Turn? {
        val t = texts.joinToString(" ").lowercase()
        return when {
            listOf("разворот", "развернитесь", "u-turn", "uturn", "make a u").any { it in t } -> Turn.UTURN
            listOf("налево", "влево", "левее", "left").any { it in t } -> Turn.LEFT
            listOf("направо", "вправо", "правее", "right").any { it in t } -> Turn.RIGHT
            listOf("прямо", "straight", "продолжайте", "continue").any { it in t } -> Turn.STRAIGHT
            else -> null
        }
    }

    /** Раскладка, которую приложение рисует само: достаём тексты и описания картинок по порядку. */
    @Suppress("DEPRECATION")
    /** Последняя разобранная раскладка по ключу уведомления и когда её разобрали */
    private val remoteCache = HashMap<String, Pair<Long, Remote>>()

    /**
     * Навигатор обновляет уведомление каждую секунду, а собрать его раскладку — это создать целое дерево
     * View. Поэтому разбираем её не чаще раза в 2 секунды, а при погашенном экране берём прошлую.
     */
    private fun remoteFor(context: Context, key: String, n: Notification): Remote {
        val now = SystemClock.elapsedRealtime()
        val cached = remoteCache[key]
        if (cached != null && (now - cached.first < 2000 || !IslandBus.screenOn.value)) return cached.second
        return remoteViews(context, n).also { remoteCache[key] = now to it }
    }

    fun forget(key: String) {
        remoteCache.remove(key)
    }

    private fun remoteViews(context: Context, n: Notification): Remote {
        val rv = n.bigContentView ?: n.contentView ?: n.headsUpContentView
            ?: return Remote(emptyList(), null, tr("своей раскладки нет", "no custom layout"))
        return runCatching {
            val root = rv.apply(context, FrameLayout(context))
            val texts = mutableListOf<String>()
            var image: Bitmap? = null
            fun walk(v: View) {
                when (v) {
                    is TextView -> v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                    is ImageView -> {
                        v.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                        val d = v.drawable
                        if (image == null && d != null && d.intrinsicWidth in 1..220) image = d.toBitmap(96, 96)
                    }
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(root)
            Remote(texts, image, trf("раскладка есть, текстов: {0}", "custom layout, texts: {0}", texts.size))
        }.getOrElse {
            Remote(emptyList(), null, trf("раскладку не удалось прочитать: {0}", "couldn't read the layout: {0}", it.javaClass.simpleName))
        }
    }
}

/**
 * Что остров видит в уведомлениях навигаторов, такси и доставки. Если у вашего приложения расстояние
 * не показывается, этот текст (О приложении → Диагностика навигации) покажет, что именно оно присылает.
 */
object LiveDebug {
    val dumps = MutableStateFlow<Map<String, String>>(emptyMap())

    fun record(
        context: Context,
        sbn: StatusBarNotification,
        title: String,
        text: String,
        bigText: String,
        sub: String,
        remote: List<String>,
        remoteNote: String,
    ) {
        val n = sbn.notification
        val body = buildString {
            appendLine(NotificationParser.appLabel(context, sbn.packageName) + " (" + sbn.packageName + ")")
            val category = n.category ?: "—"
            appendLine(trf("постоянное: {0}, категория: {1}", "ongoing: {0}, category: {1}", sbn.isOngoing, category))
            appendLine(tr("заголовок: ", "title: ") + title.ifEmpty { "—" })
            appendLine(tr("текст: ", "text: ") + text.ifEmpty { "—" })
            if (bigText.isNotEmpty() && bigText != text) appendLine(tr("большой текст: ", "big text: ") + bigText)
            appendLine(tr("подпись: ", "subtext: ") + sub.ifEmpty { "—" })
            appendLine(tr("раскладка: ", "layout: ") + remoteNote)
            remote.forEachIndexed { i, t -> appendLine("  [$i] $t") }
            append(tr("кнопки: ", "buttons: ") + (n.actions?.joinToString { it.title?.toString().orEmpty() } ?: "—"))
        }
        put(sbn.key, body)
    }

    /** Уведомление убрали — его разбор больше не нужен. */
    fun forget(key: String) {
        if (key in dumps.value) dumps.value = dumps.value - key
    }

    /** Храним только последние записи: приложения доставки присылают много обычных уведомлений. */
    private fun put(key: String, body: String) {
        val next = (dumps.value - key) + (key to body)
        dumps.value = if (next.size > MAX_DUMPS) next.entries.drop(next.size - MAX_DUMPS).associate { it.toPair() } else next
    }

    private const val MAX_DUMPS = 20

    /** Подписи экрана навигатора — чтобы было видно, откуда остров берёт расстояние. */
    fun recordScreen(context: Context, pkg: String, items: List<ScreenItem>, r: NavScreenParser.Result) {
        val body = buildString {
            appendLine(tr("Экран: ", "Screen: ") + NotificationParser.appLabel(context, pkg) + " (" + pkg + ")")
            val dist = r.distance ?: "—"
            val street = r.street ?: "—"
            val eta = r.eta ?: "—"
            val turn = r.turn?.toString() ?: tr("по картинке", "from the picture")
            appendLine(trf(
                "нашлось: расстояние {0}, улица {1}, прибытие {2}, поворот {3}",
                "found: distance {0}, street {1}, arrival {2}, turn {3}", dist, street, eta, turn,
            ))
            items.filter { it.text.isNotEmpty() }.sortedWith(compareBy({ it.top }, { it.left })).take(40).forEach {
                appendLine("  «${it.text}» ${it.left},${it.top}–${it.right},${it.bottom}")
            }
        }.trimEnd()
        val key = "screen:$pkg"
        if (dumps.value[key] != body) put(key, body)
    }
}
