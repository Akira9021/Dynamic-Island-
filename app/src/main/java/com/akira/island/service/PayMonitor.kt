package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.SystemClock
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.PayReady
import com.akira.island.core.Tints
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Бесконтактная оплата телефоном, как Apple Pay на iPhone. Подсказка «Оплата · К терминалу» появляется,
 * только когда приложение оплаты показывает экран оплаты телефоном («Поднесите телефон к терминалу»)
 * или всплывает окно «Оплата» сервисов Google, — а не когда просто открыли банк. Держится она, пока
 * оплата не пройдёт или вы не выйдете из приложения. «Оплачено» с суммой — по экрану приложения
 * (PayScreenReader), уведомлению банка или СМС с его номера.
 */
object PayMonitor {
    /** Известные банки и кошельки — на случай, если система не отдаст список платёжных служб. */
    private val known = setOf(
        "com.google.android.apps.walletnfcrel", // Google Кошелёк / Google Pay
        "com.samsung.android.spay", // Samsung Wallet
        "ru.nspk.mirpay", // Mir Pay
        "com.idamob.tinkoff.android", // Т-Банк (T-Pay)
        "ru.alfabank.mobile.android", // Альфа-Банк (Alfa Pay)
        "ru.sberbankmobile", // СберБанк (SberPay)
        "ru.vtb24.mobilebanking.android", // ВТБ
        "ru.raiffeisennews", // Райффайзен
        "ru.gazprombank.android.mobilebank.app", // Газпромбанк
        "ru.sovcomcard.halva.v1", // Халва
        "ru.mts.money", // МТС Банк
        "com.paypal.android.p2pmobile",
        "com.revolut.revolut",
    )

    /** Кошельки: показали сумму у себя на экране — значит, это и есть оплата. */
    private val wallets = setOf(
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "ru.nspk.mirpay",
    )

    /**
     * Три минуты: столько держится «К терминалу», столько ждём оплату после экрана оплаты
     * и столько одна и та же сумма не показывается второй раз.
     */
    const val PAY_WINDOW_MS = 180_000L

    /** Само окно «Оплата» из сервисов Google: появляется, когда телефон поднесли к терминалу. */
    const val GMS = "com.google.android.gms"

    /** Окна системы поверх приложения оплаты — окна Google, вопрос NFC «Оплатить через…?»: из приложения не ушли. */
    fun overlay(pkg: String) = pkg == GMS || pkg == "com.android.nfc"

    /** Уведомления этих приложений тоже разбираются как оплата: банки, которых нет в списке выше. */
    private val bankish = Regex(
        "bank|banking|sber|tinkoff|tbank|vtb|alfa|raiff|sovcom|halva|gazprom|psbank|otkritie|pochta|rshb|uralsib|" +
            "rosbank|akbars|mkb|yoomoney|qiwi|wallet|\\.pay|pay\\.|mirpay|spay|fintech|" +
            "kaspi|halyk|revolut|paypal|gopay|paytm|phonepe|gcash|belkart|priorbank|mtbank|belinvest|belarus",
        RegexOption.IGNORE_CASE,
    )

    /** Отправитель СМС — банк: короткий номер (900) или название. */
    private val bankSender = Regex(
        "^\\d{3,6}$|bank|банк|sber|сбер|tinkoff|т-банк|t-bank|vtb|втб|alfa|альфа|raiff|райф|gazprom|газпром|sovcom|совком|" +
            "pochta|почта|otkritie|открытие|mts|мтс|ozon|озон|psb|псб|rshb|россельхоз|uralsib|уралсиб|rosbank|росбанк|halva|халва|" +
            "yoomoney|юmoney",
        RegexOption.IGNORE_CASE,
    )

    private var readyPkg: String? = null
    /**
     * Когда и в каком приложении в последний раз показали «К терминалу»: сумма в уведомлении этого приложения,
     * кошелька или СМС банка сразу после этого — та самая оплата. После «Оплачено» окно закрывается.
     */
    private var readyShownAt = 0L
    private var readyShownPkg: String? = null
    private var readyTimeout: Job? = null
    private var paidKey: String? = null
    private var paidAt = 0L

    /** Приложения, которые умеют платить телефоном (объявляют службу эмуляции карты, HCE), — из системы. */
    private var nfcApps: Set<String> = emptySet()
    private var nfcCheckedAt = 0L

    /** Умеет ли приложение платить телефоном: Mir Pay, T-Pay, SberPay, Яндекс Пэй и любые другие. */
    fun isPayApp(context: Context, pkg: String): Boolean = pkg in known || pkg in nfcApps(context)

    fun isWallet(pkg: String) = pkg in wallets || pkg == GMS

    /** Список платёжных приложений меняется редко — спрашиваем систему раз в десять минут. */
    private fun nfcApps(context: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (nfcCheckedAt == 0L || now - nfcCheckedAt > 10 * 60_000) {
            nfcCheckedAt = now
            nfcApps = runCatching {
                context.packageManager.queryIntentServices(Intent(HostApduService.SERVICE_INTERFACE), 0)
                    .mapNotNull { it.serviceInfo?.packageName }
                    .toSet() - context.packageName - "com.android.nfc"
            }.getOrDefault(emptySet())
        }
        return nfcApps
    }

    /** Чей значок показывать: у окна Google — Google Кошелёк. */
    private fun appFor(pkg: String) = if (pkg == GMS) "com.google.android.apps.walletnfcrel" else pkg

    /**
     * На экране — оплата телефоном: подсказка в острове, пока не заплатите или не уйдёте из приложения.
     * На всякий случай (если о выходе не узнаем) через три минуты она уходит сама.
     */
    fun onPayScreen(context: Context, pkg: String) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPayReady || IslandBus.payReady.value?.pkg == pkg) return
        readyPkg = pkg
        readyShownAt = SystemClock.elapsedRealtime()
        readyShownPkg = appFor(pkg)
        PayDebug.add(tr("Экран оплаты", "Payment screen"), pkg)
        IslandBus.payReady.value = PayReady(pkg, NotificationParser.icon(context, appFor(pkg)))
        readyTimeout?.cancel()
        readyTimeout = IslandBus.scope.launch {
            delay(PAY_WINDOW_MS)
            clearReady()
        }
    }

    /** Окно «Оплата» из сервисов Google — телефон поднесли к терминалу. */
    fun onTapAndPay(context: Context) = onPayScreen(context, GMS)

    /** Открыли приложение оплаты. С «Подсказкой при открытии» — сразу, как раньше; без неё ждём экран оплаты. */
    fun onAppOpened(context: Context, pkg: String) {
        if (SettingsRepo.current.payHintOnOpen && isPayApp(context, pkg)) onPayScreen(context, pkg)
    }

    /** Открыто другое приложение — ушли, так и не заплатив: подсказка больше не нужна. */
    fun onForeground(pkg: String) {
        val ready = readyPkg ?: return
        if (pkg == ready || overlay(pkg)) return
        clearReady()
    }

    /** Убрать подсказку: оплатили, ушли из приложения или погасили экран. */
    fun clearReady() {
        readyPkg = null
        readyTimeout?.cancel()
        readyTimeout = null
        IslandBus.payReady.value = null
    }

    private val success = Regex(
        "покупк|оплат|списан|списал|paid|payment|purchase|charged|spent|" +
            "pembayaran|transaksi|pago|compra|pagamento|zahlung|achat|paiement|ödeme|satın",
        RegexOption.IGNORE_CASE,
    )
    /**
     * Не оплата: пополнения, переводы, возвраты, неудачные платежи, а также напоминания и счета
     * («К оплате», «Не забудьте оплатить», «будет списано», «Payment due»).
     */
    private val skip = Regex(
        "пополн|зачисл|поступил|кэшбэк|кешбэк|cashback|возврат|refund|отклон|отказ|declined|не удал|ошибк|" +
            "не прошл|не выполн|недостаточно|не хватает|не оплач|к оплате|на оплату|оплатит|заплатит|" +
            "напомина|будет списан|спишем|спишется|предстоящ|due|remind|failed|unsuccessful|insufficient|request|received",
        RegexOption.IGNORE_CASE,
    )

    /**
     * «Перевод» — не оплата, если до него не было экрана оплаты телефоном. Но сразу после экрана оплаты так
     * иногда подписывают саму оплату (СБП, QR), поэтому тогда это слово не мешает.
     */
    private val transfer = Regex("перевод|transfer|transferred", RegexOption.IGNORE_CASE)

    /** Цифры суммы — чтобы одна оплата с экрана и из уведомления не показалась дважды. */
    private fun key(sum: String) = sum.filter { it.isDigit() }.trimEnd('0')

    /** Эта сумма уже показана как «Оплачено» за последние три минуты. */
    private fun alreadyShown(sum: String) = key(sum) == paidKey && SystemClock.elapsedRealtime() - paidAt < PAY_WINDOW_MS

    private fun showPaid(context: Context, sum: String, caption: String, pkg: String) {
        paidKey = key(sum)
        paidAt = SystemClock.elapsedRealtime()
        readyShownAt = 0L
        clearReady()
        IslandBus.post(
            Notice(
                title = tr("Оплачено", "Paid"),
                state = sum.ifBlank { null },
                icon = Ios.load(context, R.drawable.ios_checkmark_circle),
                tint = Tints.Green,
                caption = caption,
                style = NoticeStyle.PAY_DONE,
                appIcon = NotificationParser.icon(context, pkg),
                extraMs = 900,
            ),
        )
    }

    /** Приложение оплаты показало у себя галочку и сумму. */
    fun onScreenPaid(context: Context, pkg: String, sum: String) {
        val s = SettingsRepo.current
        PayDebug.add(tr("С экрана: оплачено $sum", "From the screen: paid $sum"), pkg)
        if (!s.enabled || !s.evPaySuccess || alreadyShown(sum)) return
        val app = appFor(pkg)
        showPaid(context, sum, NotificationParser.appLabel(context, app), app)
    }

    /**
     * Уведомление от банка, кошелька или СМС банка об оплате. Возвращает true, если оно показано
     * как «Оплачено» (или уже было показано с экрана) и обычной карточкой сообщения показывать его не нужно.
     */
    fun onNotification(context: Context, sbn: StatusBarNotification): Boolean {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evPaySuccess || sbn.isOngoing) return false
        val pkg = sbn.packageName
        val n = sbn.notification ?: return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ex = n.extras ?: return false
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val sms = pkg == defaultSms(context)
        if (sms) {
            if (!bankSender.containsMatchIn(title.trim())) return false
        } else if (!isPayApp(context, pkg) && !bankish.containsMatchIn(pkg)) {
            return false
        }
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val all = PayScreenParser.normalize("$title $text")
        // У настоящей оплаты всегда есть сумма; без неё это скорее реклама или новость банка
        val sum = PayScreenParser.amount(all)
        if (sum == null) {
            PayDebug.add(tr("Без суммы — не оплата", "No amount — not a payment"), pkg, all)
            return false
        }
        // Кошельки (Google Кошелёк, Samsung Wallet, Mir Pay) пишут только магазин и сумму с картой, без слова «оплата»;
        // сумма сразу после экрана оплаты телефоном — от того же приложения, платёжного или СМС банка — та самая оплата
        val recent = readyShownAt != 0L && SystemClock.elapsedRealtime() - readyShownAt < PAY_WINDOW_MS &&
            (pkg == readyShownPkg || sms || isPayApp(context, pkg))
        val paid = !skip.containsMatchIn(all) && (!transfer.containsMatchIn(all) || recent) &&
            (success.containsMatchIn(all) || pkg in wallets || recent)
        PayDebug.add(if (paid) tr("Оплачено $sum", "Paid $sum") else tr("Не похоже на оплату", "Doesn't look like a payment"), pkg, all)
        if (!paid) return false
        if (alreadyShown(sum)) return true
        showPaid(context, sum, if (sms) title else NotificationParser.appLabel(context, pkg), pkg)
        return true
    }

    private var smsPkg: String? = null
    private var smsCheckedAt = 0L

    /** Приложение СМС по умолчанию — раз в минуту, его меняют редко. */
    private fun defaultSms(context: Context): String? {
        val now = SystemClock.elapsedRealtime()
        if (smsCheckedAt == 0L || now - smsCheckedAt > 60_000) {
            smsCheckedAt = now
            smsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        }
        return smsPkg
    }
}

/**
 * «Диагностика оплаты»: что остров увидел — экран оплаты, уведомления банков и кошельков, СМС банков —
 * и как это понял. Последние 15 записей, только в памяти: после перезапуска пусто, никуда не отправляется.
 */
object PayDebug {
    val log = MutableStateFlow<List<String>>(emptyList())

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private var last = ""

    fun add(result: String, pkg: String, text: String = "") {
        // Экран оплаты разбирается много раз подряд — одно и то же записываем один раз
        val what = buildString {
            append(result).append('\n').append(pkg)
            if (text.isNotBlank()) append('\n').append(text.trim().take(200))
        }
        if (what == last) return
        last = what
        log.value = (listOf("${clock.format(Date())} · $what") + log.value).take(15)
    }
}
