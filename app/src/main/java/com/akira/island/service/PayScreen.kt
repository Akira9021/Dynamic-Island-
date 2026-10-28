package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Что видно на экране приложения оплаты: экран оплаты телефоном, суммы и слова об оплате.
 * Отдельно от чтения экрана, чтобы разбор можно было проверить без телефона.
 */
object PayScreenParser {
    private val spaces = Regex("[\\u00A0\\u202F\\u2007\\u2009]")

    /** Сумма с разрядами и копейками: «89,99», «1 250», «50.000», «12500.5» */
    private const val NUMBER = """\d{1,3}(?:[ .,]?\d{3})*(?:[.,]\d{1,2})?"""
    /** Валюта после суммы: «350 ₽», «12.50 USD», «500р» */
    private const val CURRENCY = """₽|руб\.?|р\.?(?!\p{L})|RUB|RUR|USD|EUR|GBP|KZT|UAH|BYN|CNY|TRY|INR|IDR|PHP|MYR|THB|VND|BRL|""" +
        """AMD|GEL|UZS|AZN|KGS|€|£|₸|₴|₹|₱|₩|¥|฿|₫|₺|\$"""
    /** Валюта перед суммой: «$12.50», «Rp 50.000», «₹500» */
    private const val PREFIX = """[$€£₹₱₩¥฿₫₺₸]|R\$|Rp|RM|RUB|USD|EUR|INR|IDR|PHP|MYR|THB|KZT"""

    /** Списание на экране: «−89,99 ₽», «- 1 250 ₽», «–$12.50». Нужна валюта или копейки — иначе это не деньги. */
    private val charge = Regex(
        """(?<![\p{L}\p{N}])[−–-]\s?(?:($NUMBER)\s?($CURRENCY)?|($PREFIX)\s?($NUMBER))""",
        RegexOption.IGNORE_CASE,
    )

    /** Сумма с валютой где угодно в тексте. Перед ней не цифра и не звёздочка — «*1234» это номер карты. */
    private val amount = Regex(
        """(?<![\d*])$NUMBER\s?(?:$CURRENCY)|(?<!\p{L})(?:$PREFIX)\s?$NUMBER""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Экран оплаты телефоном: «Для оплаты поднесите телефон к терминалу», «Приложите смартфон к платёжному
     * терминалу», «Hold your phone up to the terminal», «Hold near reader», «Готово к оплате».
     */
    private val readyWords = Regex(
        "(подне|прилож|поднос|приклад)\\S*\\s+(\\S+\\s+){0,3}?к\\s+(\\S+\\s+)?(терминал|считыва|ридер)|" +
            "hold\\s+(\\S+\\s+){0,4}?(to|near|close to|against)\\s+(the\\s+)?(payment\\s+)?(terminal|reader)|" +
            "tap\\s+(\\S+\\s+){0,3}?(to|on)\\s+(the\\s+)?(terminal|reader)|ready to pay|готов\\S*\\s+к\\s+оплате",
        RegexOption.IGNORE_CASE,
    )

    /** Оплата прошла — так её подписывают на экране результата. */
    private val paidWords = Regex(
        "оплачено|оплачена|оплата прошла|оплата выполнена|оплата совершена|оплата успешна|успешная оплата|покупка совершена|" +
            "покупка прошла|платёж выполнен|платеж выполнен|платёж прошёл|платеж прошел|payment successful|payment complete|" +
            "payment approved|\\bpaid\\b|pembayaran berhasil|pago realizado|pagamento aprovado",
        RegexOption.IGNORE_CASE,
    )

    private val failureWords = Regex(
        "не прошл|не удал|отклон|отказ|ошибк|недостаточно|не хватает|не оплач|declined|failed|error|insufficient|unsuccess|try again|повтор",
        RegexOption.IGNORE_CASE,
    )

    /** Все списания в строке — как их показать: «89,99 ₽», «$12.50». */
    fun charges(text: String): List<String> {
        val t = normalize(text)
        return charge.findAll(t).mapNotNull { m ->
            val (num, cur, preCur, preNum) = m.destructured
            when {
                preNum.isNotEmpty() -> "$preCur$preNum"
                cur.isNotEmpty() -> "$num ${cur.trimEnd('.')}"
                num.contains(',') || num.contains('.') -> num
                else -> null
            }
        }.toList()
    }

    /** Пробелы внутри чисел (неразрывные, узкие) — обычные, чтобы суммы находились одинаково. */
    fun normalize(text: String) = text.replace(spaces, " ")

    /** Первая сумма с валютой в тексте: «89,99 ₽», «1 250 руб.», «$12.50», «Rp 50.000». */
    fun amount(text: String): String? = amount.find(normalize(text))?.value?.trim()

    /** Экран оплаты телефоном. */
    fun ready(text: String) = readyWords.containsMatchIn(normalize(text))

    /** «Оплачено», «Оплата прошла» — и не «Оплата не прошла». */
    fun paid(text: String) = paidWords.containsMatchIn(text) && !failureWords.containsMatchIn(text)

    fun failure(text: String) = failureWords.containsMatchIn(text)
}

/**
 * Экран приложения оплаты, пока оно открыто. Остров ждёт двух вещей:
 *  - экрана оплаты телефоном («Поднесите телефон к терминалу», T-Pay, SberPay, Mir Pay, Яндекс Пэй…) —
 *    тогда в острове «Оплата · К терминалу», и она держится, пока не заплатите или не выйдете;
 *  - суммы оплаты («−89,99 ₽») — тогда сразу «Оплачено», не дожидаясь уведомления банка. Учитывается только
 *    списание, которого не было на экране, когда показался экран оплаты, и только если оно на экране одно —
 *    так история операций не принимается за оплату. Если приложение само открылось уже с результатом
 *    (заплатили, не открывая его), нужно ещё «Оплачено» на экране.
 * Изменения экрана приходят, только пока такое приложение открыто; разбирается он не чаще раза в 0,4 с.
 */
class PayScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    val active: Boolean get() = current != null

    private var current: String? = null
    private var since = 0L
    private var readyAt = 0L
    private var baseline: MutableSet<String>? = null
    private var done = false
    /** Новые надписи прошлого разбора — тот же экран второй раз не разбираем */
    private var lastFresh: List<String>? = null
    private var lastScan = 0L
    private var scanJob: Job? = null
    private var stopJob: Job? = null

    /** Новое окно: другое приложение — начинаем заново; окно оплаты в том же — экран оплаты. */
    fun onForeground(pkg: String, cls: String) {
        // Системное окно поверх приложения оплаты — читаем дальше, когда оно уйдёт
        if (current != null && PayMonitor.overlay(pkg)) return
        if (pkg == current) {
            if (payWindow(cls)) markReady(pkg)
            schedule(150)
            return
        }
        stop()
        if (!PayMonitor.isPayApp(service, pkg)) return
        start(pkg)
        if (payWindow(cls)) markReady(pkg)
    }

    /** Окно «Оплата» сервисов Google поверх любого приложения: читаем его минуту, пока оно на экране. */
    fun onTapAndPay() {
        if (current != PayMonitor.GMS) {
            stop()
            start(PayMonitor.GMS)
        }
        markReady(PayMonitor.GMS)
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(60_000)
            if (current == PayMonitor.GMS) stop()
        }
    }

    fun onContentChanged(event: AccessibilityEvent) {
        if (done || event.packageName?.toString() != current) return
        schedule(if (readyAt != 0L) 150 else 400)
    }

    fun stop() {
        scanJob?.cancel()
        stopJob?.cancel()
        scanJob = null
        current = null
        baseline = null
    }

    private fun start(pkg: String) {
        val s = SettingsRepo.current
        if (!s.enabled || (!s.evPayReady && !s.evPaySuccess)) return
        current = pkg
        since = SystemClock.elapsedRealtime()
        readyAt = 0L
        baseline = null
        lastFresh = null
        done = false
        schedule(300)
    }

    /** Окна оплаты телефоном у некоторых приложений называются прямо так — NFC, HCE, бесконтактная оплата. */
    private fun payWindow(cls: String) = PayWindow.containsMatchIn(cls)

    private fun markReady(pkg: String, texts: Set<String>? = null) {
        if (readyAt == 0L) {
            readyAt = SystemClock.elapsedRealtime()
            screenLogs = 0
            baseline = texts?.toMutableSet() ?: HashSet()
        }
        PayMonitor.onPayScreen(service, pkg)
    }

    private fun schedule(wait: Long) {
        if (scanJob?.isActive == true) return
        // Не чаще раза в 0,4 с, пока ждём экран оплаты, — банк листают долго, а батарею жалко
        val gap = if (readyAt != 0L) 0L else (400 - (SystemClock.elapsedRealtime() - lastScan)).coerceAtLeast(0)
        scanJob = scope.launch {
            delay(maxOf(wait, gap))
            runCatching { scan() }
        }
    }

    private fun scan() {
        val pkg = current ?: return
        if (done) return
        val now = SystemClock.elapsedRealtime()
        lastScan = now
        // Оплату после экрана оплаты ждём не дольше трёх минут — дальше экран не разбираем
        if (readyAt != 0L && now - readyAt > PayMonitor.PAY_WINDOW_MS) {
            done = true
            return
        }
        val root = rootFor(pkg) ?: return
        val texts = LinkedHashSet<String>()
        collect(root, texts, 0)

        // Экран оплаты телефоном — подсказка в острове
        if (readyAt == 0L && texts.any { PayScreenParser.ready(it) }) {
            markReady(pkg, texts)
            return
        }
        val all = texts.flatMap { PayScreenParser.charges(it) }.distinct()
        if (readyAt != 0L) {
            val base = baseline ?: return
            // Экран оплаты ещё дорисовывается — всё на нём «как было», а не оплата
            if (now - readyAt < 500) {
                base += texts
                return
            }
            val fresh = texts.filter { it !in base }
            // Экран не изменился с прошлого раза — разбирать нечего (пока ждём оплату, он меняется часто)
            if (fresh == lastFresh) return
            lastFresh = fresh
            logAfterReady(pkg, fresh, now)
            if (fresh.any { PayScreenParser.failure(it) }) return
            val freshCharges = fresh.flatMap { PayScreenParser.charges(it) }.distinct()
            // У кошелька сумма на экране — это и есть оплата. У банка в нём бывает и история операций,
            // поэтому нужно ещё «Оплачено» или чтобы экран оплаты был на виду
            if (all.size == 1 && freshCharges.size == 1 &&
                (PayMonitor.isWallet(pkg) || texts.any { PayScreenParser.paid(it) || PayScreenParser.ready(it) })
            ) {
                return paid(pkg, freshCharges[0])
            }
            // Экран результата часто без минуса: «Оплата прошла» и одна новая сумма с валютой — «350 ₽»
            if (fresh.any { PayScreenParser.paid(it) }) {
                val sums = fresh.mapNotNull { PayScreenParser.amount(it) }.distinct()
                if (sums.size == 1) return paid(pkg, sums[0])
                // Экран говорит «оплата прошла», но суммы на нём нет — показываем «Оплачено» без суммы
                if (sums.isEmpty()) return paid(pkg, "")
                PayDebug.add(trf("«Оплачено» на экране, но сумм: {0}", "“Paid” on screen, but amounts: {0}", sums.size), pkg, fresh.joinToString(" | "))
            }
        } else if (now - since < 4_000 && all.size == 1 && texts.any { PayScreenParser.paid(it) } && texts.none { PayScreenParser.failure(it) }) {
            // Приложение открылось само уже с результатом: заплатили, не открывая его
            paid(pkg, all[0])
        }
    }

    private var screenLogs = 0
    private var screenLoggedAt = 0L

    /**
     * Для «Диагностики оплаты»: что появилось на экране после экрана оплаты. Не чаще раза в две секунды и не
     * больше шести записей за оплату; длинные цифры (номера карт) скрыты, хранится только в памяти.
     */
    private fun logAfterReady(pkg: String, fresh: List<String>, now: Long) {
        if (fresh.isEmpty() || screenLogs >= 6 || now - screenLoggedAt < 2_000) return
        screenLogs++
        screenLoggedAt = now
        val line = fresh.take(14).joinToString(" | ") { it.replace(Regex("\\d{9,}"), "…") }
        PayDebug.add(tr("Экран после оплаты", "Screen after the payment screen"), pkg, line)
    }

    private fun paid(pkg: String, sum: String) {
        done = true
        PayMonitor.onScreenPaid(service, pkg, sum)
    }

    private fun rootFor(pkg: String): AccessibilityNodeInfo? =
        service.rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }
            ?: runCatching { service.windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == pkg } } }.getOrNull()

    private fun collect(node: AccessibilityNodeInfo, out: MutableSet<String>, depth: Int) {
        if (depth > 30 || out.size > 300) return
        if (node.isVisibleToUser) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 200 }?.let { out += it }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 200 }?.let { out += it }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    private companion object {
        val PayWindow = Regex("nfc|hce|contactless|tapandpay|tap_and_pay|tappay|tpay|sberpay|mirpay", RegexOption.IGNORE_CASE)
    }
}
