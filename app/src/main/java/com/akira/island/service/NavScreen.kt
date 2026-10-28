package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.ImageBitmap
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveInfo
import com.akira.island.core.LiveKind
import com.akira.island.core.Turn
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Что удалось прочитать с экрана навигатора: расстояние до манёвра, улица, прибытие и поворот. */
data class ScreenNav(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?,
    val distance: String?,
    val street: String?,
    val eta: String?,
    val turn: Turn?,
    /** Подсказка навигатора («Поверните направо на …»), если она есть на экране */
    val instruction: String? = null,
)

/**
 * Данные навигатора с его собственного экрана. Многие навигаторы (2ГИС, Яндекс Навигатор) пишут
 * в уведомлении только «Навигатор запущен», а расстояние и стрелку показывают лишь у себя на экране.
 * Пока навигатор открыт, остров читает с экрана подписи (только у навигаторов) и дополняет ими
 * «живую активность»; стрелку поворота узнаёт по картинке манёвра.
 */
object NavScreen {
    val data = MutableStateFlow<Map<String, ScreenNav>>(emptyMap())

    /** Дополняет активность из уведомления тем, что видно на экране навигатора. */
    fun merge(l: LiveInfo, sn: ScreenNav?): LiveInfo {
        if (sn == null || l.kind != LiveKind.NAV) return l
        val turn = sn.turn ?: l.turn
        return l.copy(
            distance = sn.distance ?: l.distance,
            eta = sn.eta ?: l.eta,
            text = instructionText(sn.instruction, turn, sn.street) ?: l.text,
            turn = turn,
        )
    }

    /**
     * Что писать под расстоянием, как в примере: подсказка навигатора; если её нет на экране —
     * своя по стрелке («Поверните налево») и улица.
     */
    fun instructionText(instruction: String?, turn: Turn?, street: String?): String? {
        if (instruction != null) {
            return if (street != null && !instruction.contains(street, ignoreCase = true)) "$instruction · $street" else instruction
        }
        val phrase = when (turn) {
            Turn.LEFT -> tr("Поверните налево", "Turn left")
            Turn.RIGHT -> tr("Поверните направо", "Turn right")
            Turn.STRAIGHT -> tr("Двигайтесь прямо", "Go straight")
            Turn.UTURN -> tr("Развернитесь", "Make a U-turn")
            null -> null
        }
        return when {
            phrase != null && street != null -> "$phrase · $street"
            else -> phrase ?: street
        }
    }

    /** Навигатор без своего уведомления: активность только по экрану. */
    fun synthetic(sn: ScreenNav): LiveInfo = LiveInfo(
        key = "screen:${sn.pkg}",
        pkg = sn.pkg,
        kind = LiveKind.NAV,
        title = sn.label,
        text = instructionText(sn.instruction, sn.turn, sn.street).orEmpty(),
        sub = "",
        icon = sn.icon,
        art = null,
        postTime = 0L,
        open = null,
        distance = sn.distance,
        eta = sn.eta,
        turn = sn.turn,
    )

    /** Активность навигатора [pkg] с учётом экрана — для плашки при запуске. */
    fun mergedFor(pkg: String): LiveInfo? {
        val sn = data.value[pkg]
        val base = IslandBus.live.value.values.firstOrNull { it.pkg == pkg }
        return when {
            base != null -> merge(base, sn)
            sn != null -> synthetic(sn)
            else -> null
        }
    }
}

/** Надпись на экране навигатора и где она стоит; [image] — картинка без текста (значок манёвра). */
data class ScreenItem(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val image: Boolean = false) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
}

/** Разбор надписей экрана навигатора. Отдельно от Android, чтобы можно было проверить на примерах. */
object NavScreenParser {
    data class Result(
        val distance: String?,
        val street: String?,
        val eta: String?,
        val turn: Turn?,
        val icon: Rect?,
        val instruction: String? = null,
    )

    private val number = Regex("""^\d{1,4}(?:[.,]\d{1,2})?$""")
    private val unit = Regex("""^(км|м|мин|ч|km|m|mi|ft|min|h)\.?$""", RegexOption.IGNORE_CASE)
    private val distanceRe = Regex("""^\d{1,4}(?:[.,]\d{1,2})?\s?(км|м|km|m|mi|ft)\.?$""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""^\d{1,2}:\d{2}$""")
    private val minutes = Regex("""^\d{1,3}\s?(мин|min)\.?$""", RegexOption.IGNORE_CASE)
    private val hours = Regex("""^\d{1,3}\s?(ч|h)\.?(\s?\d{1,2}\s?(мин|min)\.?)?$""", RegexOption.IGNORE_CASE)
    private val letters = Regex("""\p{L}{3,}""")
    private val noise = setOf(
        "следуйте по шагам", "двигайтесь по шагам", "продолжить по gps", "прибытие", "в пути", "маршрут", "обзор", "меню", "поиск", "назад",
    )

    fun parse(raw: List<ScreenItem>): Result {
        val items = raw.map { it.copy(text = it.text.replace(' ', ' ').trim()) }.filter { it.text.isNotEmpty() || it.image }
        val texts = items.filter { it.text.isNotEmpty() }
        // «12» и «км» часто лежат отдельно: рядом в строке или одно под другим — склеиваем
        val joined = texts.filter { unit.matches(it.text) }.mapNotNull { u ->
            val beside = texts.filter { n ->
                number.matches(n.text) && n.right <= u.left + u.width / 3 && u.left - n.right < u.height * 2.5 &&
                    overlap(n.top, n.bottom, u.top, u.bottom) >= min(n.height, u.height) / 2
            }.minByOrNull { u.left - it.right }
            val above = texts.filter { n ->
                number.matches(n.text) && n.bottom <= u.top + u.height / 3 && u.top - n.bottom < n.height * 1.2 &&
                    abs(n.cx - u.cx) < max(n.width, u.width) * 0.7
            }.minByOrNull { u.top - it.bottom }
            val n = beside ?: above ?: return@mapNotNull null
            ScreenItem("${n.text} ${u.text}", min(n.left, u.left), min(n.top, u.top), max(n.right, u.right), max(n.bottom, u.bottom))
        }
        val all = texts + joined

        // Расстояние до манёвра — самое верхнее; самое нижнее — сколько осталось ехать всего
        val distances = all.filter { distanceRe.matches(it.text) }
        val dist = distances.minByOrNull { it.top }
        val total = distances.maxByOrNull { it.top }?.takeIf { it !== dist && it.top > (dist?.bottom ?: 0) }
        val street = dist?.let { d ->
            all.filter { s ->
                s !== d && letters.containsMatchIn(s.text) && !s.text.any { it.isDigit() } &&
                    s.text.lowercase() !in noise && s.top >= d.bottom - d.height / 4 && s.top - d.bottom < d.height * 2.5 &&
                    s.left < d.right + d.height * 2
            }.minByOrNull { it.top }?.text
        }
        val time = all.firstOrNull { clock.matches(it.text) }?.text
        val left = (all.firstOrNull { hours.matches(it.text) } ?: all.firstOrNull { minutes.matches(it.text) })
            ?.text?.replace(Regex("""\s+"""), " ")
        val eta = listOfNotNull(time?.let { trf("Прибытие в {0}", "Arrive {0}", it) }, left?.let(LiveParser::units),
            total?.text?.replace(Regex("""\s+"""), " ")?.let(LiveParser::units))
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")
        // Направление — только из подсказки: в подписях кнопок («← шаг влево») тоже бывают «влево» и «вправо»
        val instruction = LiveParser.instructionOf(texts.map { it.text })
        val turn = instruction?.let { LiveParser.detectTurn(listOf(it)) }

        // Значок манёвра — картинка слева от расстояния; если её нет в разметке, берём место слева
        val icon = dist?.let { d ->
            val img = items.filter { it.image && it.right <= d.left + d.height / 3 && overlap(it.top, it.bottom, d.top, d.bottom) >= d.height / 3 }
                .minByOrNull { d.left - it.right }
            if (img != null) {
                Rect(img.left, img.top, img.right, img.bottom)
            } else {
                Rect(d.left - (d.height * 1.8f).toInt(), d.top - d.height / 4, d.left - d.height / 10, d.bottom + d.height / 4)
            }
        }
        return Result(dist?.text?.replace(Regex("""\s+"""), " "), street, eta, turn, icon, instruction)
    }

    private fun overlap(a1: Int, a2: Int, b1: Int, b2: Int) = min(a2, b2) - max(a1, b1)
}

/**
 * Куда показывает стрелка манёвра на картинке: сравниваем, где «чернила» стрелки сверху и снизу.
 * Наконечник левее основания — налево, правее — направо, над основанием — прямо. Если на картинке
 * есть цветная стрелка (основной путь) и серые (другие полосы), смотрим только на цветную.
 */
object TurnClassifier {
    fun classify(px: IntArray, w: Int, h: Int): Turn? {
        if (w < 8 || h < 8 || px.size < w * h) return null
        val border = ArrayList<Int>()
        for (x in 0 until w) { border += px[x]; border += px[(h - 1) * w + x] }
        for (y in 0 until h) { border += px[y * w]; border += px[y * w + w - 1] }
        val br = median(border.map { (it shr 16) and 0xFF })
        val bg = median(border.map { (it shr 8) and 0xFF })
        val bb = median(border.map { it and 0xFF })

        val weight = FloatArray(w * h)
        val colored = BooleanArray(w * h)
        var total = 0f
        var colorTotal = 0f
        var inkCount = 0
        for (i in 0 until w * h) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val d = abs(r - br) + abs(g - bg) + abs(b - bb)
            if (d < 80) continue
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
            weight[i] = d * (0.35f + sat)
            colored[i] = sat > 0.35f
            total += weight[i]
            if (colored[i]) colorTotal += weight[i]
            inkCount++
        }
        if (inkCount < 12 || inkCount > w * h * 0.7f) return null
        val onlyColor = colorTotal > total * 0.3f
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (weight[i] <= 0f || (onlyColor && !colored[i])) continue
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
        if (maxY - minY < 4 || maxX < minX) return null
        val band = (maxY - minY) * 0.38f
        var topW = 0f; var topX = 0f; var botW = 0f; var botX = 0f
        for (y in minY..maxY) for (x in minX..maxX) {
            val i = y * w + x
            val wt = weight[i]
            if (wt <= 0f || (onlyColor && !colored[i])) continue
            if (y <= minY + band) { topW += wt; topX += wt * x }
            if (y >= maxY - band) { botW += wt; botX += wt * x }
        }
        if (topW <= 0f || botW <= 0f) return null
        val dx = (topX / topW - botX / botW) / max(1, maxX - minX)
        return when {
            dx < -0.10f -> Turn.LEFT
            dx > 0.10f -> Turn.RIGHT
            else -> Turn.STRAIGHT
        }
    }

    private fun median(v: List<Int>): Int = v.sorted()[v.size / 2]
}

/**
 * Пока открыт навигатор, раз в полторы секунды читает подписи его экрана. Работает только для
 * приложений из списка навигаторов; другие экраны остров не читает. Стрелку поворота определяет
 * по снимку экрана (Android 11+) — только области значка манёвра и только когда манёвр сменился.
 */
class NavScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private var job: Job? = null
    private var clearJob: Job? = null
    private var current: String? = null
    private var lastShot = 0L
    private var lastStreet: String? = null
    private var lastMeters = -1.0
    private val turns = HashMap<String, Turn>()

    fun onForeground(pkg: String) {
        val nav = LiveApps.kind(pkg, null) == LiveKind.NAV
        if (!nav) {
            stop()
            return
        }
        if (pkg == current && job?.isActive == true) return
        stop()
        clearJob?.cancel()
        current = pkg
        job = scope.launch {
            while (isActive) {
                // Экран погашен — читать нечего, ждём, пока его включат
                if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
                if (SettingsRepo.current.cardLive) runCatching { read(pkg) }
                delay(1500)
            }
        }
    }

    /** Навигатор ушёл с экрана: данные быстро устаревают, через 15 секунд убираем их. */
    fun stop() {
        job?.cancel()
        job = null
        val pkg = current ?: return
        current = null
        clearJob = scope.launch {
            delay(15_000)
            NavScreen.data.value = NavScreen.data.value - pkg
        }
    }

    private fun read(pkg: String) {
        val root = service.rootInActiveWindow ?: return
        val items = ArrayList<ScreenItem>()
        if (root.packageName?.toString() == pkg) collect(root, items, 0)
        @Suppress("DEPRECATION")
        root.recycle()
        if (items.isEmpty()) return
        val r = NavScreenParser.parse(items)
        LiveDebug.recordScreen(service, pkg, items, r)

        val meters = r.distance?.let(::meters) ?: -1.0
        val newManeuver = r.street != lastStreet || (meters > 0 && lastMeters > 0 && meters > lastMeters * 1.3)
        lastStreet = r.street
        lastMeters = meters
        if (r.turn != null) {
            turns[pkg] = r.turn
        } else if (r.icon != null && Build.VERSION.SDK_INT >= 30) {
            val now = SystemClock.elapsedRealtime()
            if ((newManeuver || turns[pkg] == null) && now - lastShot > 4000) {
                lastShot = now
                classifyFromScreen(pkg, r.icon)
            }
        }

        val prev = NavScreen.data.value[pkg]
        val sn = ScreenNav(
            pkg = pkg,
            label = prev?.label ?: NotificationParser.appLabel(service, pkg),
            icon = prev?.icon ?: NotificationParser.icon(service, pkg),
            distance = r.distance?.let(LiveParser::units),
            street = r.street,
            eta = r.eta,
            turn = r.turn ?: turns[pkg],
            instruction = r.instruction,
        )
        if (sn != prev) NavScreen.data.value = NavScreen.data.value + (pkg to sn)
        // Расстояние появилось впервые — показываем широкую плашку, потом остров сожмётся в капсулу
        if (prev?.distance == null && sn.distance != null) {
            NavScreen.mergedFor(pkg)?.let { LiveParser.postBanner(service, it) }
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<ScreenItem>, depth: Int) {
        if (out.size > 400 || depth > 40) return
        val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        val cls = node.className?.toString().orEmpty()
        if (node.isVisibleToUser && (text.isNotEmpty() || "Image" in cls)) {
            val b = Rect()
            node.getBoundsInScreen(b)
            if (b.width() > 0 && b.height() > 0) {
                out += ScreenItem(text, b.left, b.top, b.right, b.bottom, image = text.isEmpty() && "Image" in cls)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }

    private fun classifyFromScreen(pkg: String, icon: Rect) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val turn = runCatching {
                        val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return@runCatching null
                        val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                        hw.recycle()
                        val x = icon.left.coerceIn(0, soft.width - 1)
                        val y = icon.top.coerceIn(0, soft.height - 1)
                        val w = (icon.right.coerceAtMost(soft.width) - x).coerceAtLeast(1)
                        val h = (icon.bottom.coerceAtMost(soft.height) - y).coerceAtLeast(1)
                        val px = IntArray(w * h)
                        soft.getPixels(px, 0, w, x, y, w, h)
                        soft.recycle()
                        TurnClassifier.classify(px, w, h)
                    }.getOrNull()
                    buffer.close()
                    if (turn != null) {
                        turns[pkg] = turn
                        NavScreen.data.value[pkg]?.let { sn ->
                            if (sn.turn != turn) NavScreen.data.value = NavScreen.data.value + (pkg to sn.copy(turn = turn))
                        }
                    }
                }

                override fun onFailure(errorCode: Int) = Unit
            })
        }
    }

    private fun meters(d: String): Double {
        val m = Regex("""(\d+(?:[.,]\d+)?)\s?(км|km|м|m|mi|ft)""", RegexOption.IGNORE_CASE).find(d) ?: return -1.0
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return -1.0
        return when (m.groupValues[2].lowercase()) {
            "км", "km" -> v * 1000
            "mi" -> v * 1609
            "ft" -> v * 0.3048
            else -> v
        }
    }
}
