package com.akira.island.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.os.Build
import android.content.Context
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.akira.island.core.CallInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveKind
import com.akira.island.core.ProgressInfo
import com.akira.island.data.SettingsRepo

/**
 * Доступ к уведомлениям даёт две вещи: медиасессии всех плееров
 * и уведомления с прогрессом или секундомером (загрузки, Часы, звонки).
 */
class NotifListener : NotificationListenerService() {
    private var media: MediaMonitor? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val reconnect = Runnable {
        requestReconnect(this)
    }

    override fun onListenerConnected() {
        SettingsRepo.init(this)
        handler.removeCallbacks(reconnect)
        reconnectAttempts = 0
        connected = this
        media?.stop()
        media = MediaMonitor(applicationContext).also { it.start() }
        Controls.cancelNotification = { key -> runCatching { cancelNotification(key) } }
        // Уже висящие уведомления разбираем без показа сообщений — они не новые
        val existing = runCatching { activeNotifications }.getOrNull()
        runCatching {
            if (existing != null) HotspotMonitor.rescan(this, existing.toList())
            else HotspotMonitor.notificationsUnavailable()
        }
        existing?.forEach { postSafely(it, initial = true) }
    }

    override fun onListenerDisconnected() {
        if (connected === this) HotspotMonitor.notificationsUnavailable()
        if (connected === this) connected = null
        media?.stop()
        media = null
        Controls.cancelNotification = {}
        IslandBus.progress.value = emptyMap()
        IslandBus.chrono.value = emptyMap()
        IslandBus.calls.value = emptyMap()
        IslandBus.live.value = emptyMap()
        IslandBus.ringingAlarms.value = emptySet()
        // The platform may unbind the listener after pressure or a package update. Rebind without
        // revoking access or asking the user to reinstall; cap retries to avoid a restart loop.
        if (reconnectAttempts < 3) {
            reconnectAttempts++
            handler.postDelayed(reconnect, reconnectAttempts * 2_000L)
        }
    }

    override fun onDestroy() {
        if (connected === this) HotspotMonitor.notificationsUnavailable()
        if (connected === this) connected = null
        handler.removeCallbacksAndMessages(null)
        media?.stop()
        media = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var connected: NotifListener? = null
        private var lastReconnectAt = -30_000L

        val isConnected: Boolean get() = connected != null

        /** Called by keep-alive/on-resume only while notification access is already granted. */
        @Synchronized
        fun requestReconnect(context: Context): Boolean {
            if (isConnected) return false
            val now = SystemClock.elapsedRealtime()
            if (now - lastReconnectAt < 30_000L) return false
            lastReconnectAt = now
            return runCatching {
                requestRebind(ComponentName(context, NotifListener::class.java))
                true
            }.getOrDefault(false)
        }

        /** Never offer cancellation for foreground services, calls, or other non-clearable entries. */
        fun canDismiss(key: String): Boolean = runCatching {
            connected?.getActiveNotifications(arrayOf(key))?.any { it.key == key && it.isClearable } == true
        }.getOrDefault(false)

        private val actions = NotificationActions(
            isClearable = ::canDismiss,
            cancel = { key ->
                val service = connected ?: error("Notification listener is disconnected")
                service.cancelNotification(key)
                IslandBus.removeMessage(key)
                Inbox.remove(key)
            },
        )

        /** Android remains the authority: cancellation may be refused by system policy. */
        fun dismiss(key: String): Boolean = actions.dismiss(key)

        fun dismissAll(keys: List<String>): Int = actions.dismissAll(keys)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        postSafely(sbn)
    }

    private fun postSafely(sbn: StatusBarNotification, initial: Boolean = false) {
        runCatching { HotspotMonitor.onPosted(this, sbn) }
        // Third-party extras/icons can be malformed. One bad notification must not take down
        // the notification listener and the accessibility overlay sharing this process.
        runCatching { media?.onNotificationPosted(sbn) }
        runCatching { NotificationParser.onPosted(this, sbn, initial) }.onFailure {
            NotifDebug.add(sbn.packageName, com.akira.island.core.tr("Не удалось прочитать уведомление", "Could not read notification"))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        HotspotMonitor.onRemoved(sbn.key)
        runCatching { media?.onNotificationRemoved(sbn.key) }
        runCatching { NotificationParser.onRemoved(sbn.key) }
    }
}

object NotificationParser {
    private val icons = HashMap<String, ImageBitmap?>()

    fun onPosted(context: Context, sbn: StatusBarNotification, initial: Boolean = false) {
        if (sbn.packageName == context.packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val isMedia = n.category == Notification.CATEGORY_TRANSPORT || ex.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: appLabel(context, sbn.packageName)

        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val value = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val hasProgress = !isMedia && ((max > 0 && value < max) || (indeterminate && ongoing))
        IslandBus.progress.value = IslandBus.progress.value.toMutableMap().apply {
            if (hasProgress) {
                val detailText = listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT)
                    .mapNotNull { key -> ex.getCharSequence(key)?.takeIf { it.length <= 1024 }?.toString() }
                    .distinct().joinToString("\n")
                put(sbn.key, ProgressInfo(sbn.key, sbn.packageName, title, icon(context, sbn.packageName), value, max, indeterminate,
                    sbn.postTime, TransferDetailsParser.parse(detailText)))
            } else {
                remove(sbn.key)
            }
        }

        val call = when {
            isMedia -> null
            n.category == Notification.CATEGORY_CALL -> parseCall(context, sbn, title)
            // Discord, Zoom, Teams и др. звонят без «звонковой» категории — узнаём их по кнопкам
            VoipApps.isVoip(sbn.packageName) -> parseVoip(context, sbn, title)
            else -> null
        }
        // Какой канал присылает всплывающую плашку звонка или будильника — для экрана «Системные плашки»
        SystemBanners.onPosted(sbn, incomingCall = call?.incoming == true)
        // Звонящий будильник или таймер «Часов» — у него полноэкранный сигнал; остров включит экран сам,
        // даже если всплывающие плашки «Часов» выключены
        val ringing = n.category == Notification.CATEGORY_ALARM && n.fullScreenIntent != null
        if (ringing != (sbn.key in IslandBus.ringingAlarms.value)) {
            IslandBus.ringingAlarms.value = if (ringing) IslandBus.ringingAlarms.value + sbn.key else IslandBus.ringingAlarms.value - sbn.key
        }
        IslandBus.calls.value = IslandBus.calls.value.toMutableMap().apply {
            if (call != null) put(sbn.key, call) else remove(sbn.key)
        }

        val ticking = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0
        // Кнопки «Часов» (пауза, продолжить, сброс) — их нажимают кнопки острова
        val clock = ClockApps.isClock(context, sbn.packageName)
        val buttons = if (clock) ClockWords.buttons(n.actions) else emptyMap()
        // Таймер или секундомер «Часов» на паузе: счёт стоит, время только в тексте, а в кнопках — «Продолжить»
        val pausedMs = if (clock && !ticking && call == null && !isMedia && ClockAction.START in buttons) {
            listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
                .firstNotNullOfOrNull { ClockScreenParser.find(ex.getCharSequence(it)) }
        } else {
            null
        }
        // Идущий таймер или секундомер «Часов», у которого время только в тексте (каждую секунду новое), а не
        // системным отсчётом: так у части оболочек (Xiaomi, vivo и др.) — остров ведёт отсчёт сам от этого времени
        val texts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
        val runningMs = if (clock && !ticking && pausedMs == null && call == null && !isMedia && ClockAction.PAUSE in buttons) {
            texts.firstNotNullOfOrNull { ClockScreenParser.find(ex.getCharSequence(it)) }
        } else {
            null
        }
        // Многие диктофоны дают только время/паузу. Android 14+ также допускает FGS-плашку без ongoing.
        // The recording name may contain a date/time; prefer the live status over its title.
        val recordingTexts = listOf(Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT)
            .mapNotNull { ex.getCharSequence(it)?.toString() } +
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map(CharSequence::toString)
        val recordingElapsed = recordingTexts.firstNotNullOfOrNull { ClockScreenParser.find(it) }
            ?: ClockScreenParser.parse(title.trim())
        val recordingActions = n.actions.orEmpty().filter { it.actionIntent != null }
        val recordingPause = recordingActions.firstOrNull { isPauseRecordingAction(it.title?.toString().orEmpty()) }?.actionIntent
        val recordingResume = recordingActions.firstOrNull { isResumeRecordingAction(it.title?.toString().orEmpty()) }?.actionIntent
        val recordingStop = recordingActions.firstOrNull { isFinishRecordingAction(sbn.packageName, it.title?.toString().orEmpty()) }?.actionIntent
        val recording = classifyRecording(sbn.packageName, title,
            recordingTexts.joinToString(" "), ongoing, recordingStop != null,
            hasChronometer = ticking, hasElapsedTime = recordingElapsed != null,
            hasPauseAction = recordingPause != null, hasResumeAction = recordingResume != null,
            foregroundService = n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0, hasProgress = hasProgress,
            mediaNotification = isMedia,
            hasRecordingAction = recordingActions.any { isSpecificRecordingAction(it.title?.toString().orEmpty()) })
        val recorder = call == null && recording.recording
        // Неактивный (на паузе) таймер, который остров уже убрал, снова не показываем
        val chrono = call == null && !recording.playback && (recorder || !isMedia && (ticking || pausedMs != null || runningMs != null)) &&
            !ClockControl.wasDismissed(sbn.key, pausedMs)
        if (clock && chrono) ClockControl.reader?.onNotificationChrono(sbn.packageName)
        if (pausedMs != null && chrono) ClockControl.dropPausedLater(sbn.key)
        IslandBus.chrono.value = IslandBus.chrono.value.toMutableMap().apply {
            if (chrono) {
                val down = when {
                    recorder -> false
                    pausedMs != null || runningMs != null -> looksLikeTimer(n, title)
                    else -> ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
                }
                val stop = if (recorder) recordingStop ?: n.contentIntent.takeIf { recording.stopOnTap } else n.actions?.firstOrNull { a ->
                    val t = a.title?.toString()?.lowercase().orEmpty()
                    isStopRecordingAction(t)
                }?.actionIntent
                val now = System.currentTimeMillis()
                // Отсчёт: системный — от Notification.when; из текста — от прочитанного времени; у записи без
                // отсчёта — от времени в тексте или с момента, как появилось уведомление
                val base = when {
                    runningMs != null -> if (down) now + runningMs else now - runningMs
                    recorder -> n.`when`.takeIf { ticking && it in 1..now }
                        ?: recordingElapsed?.let { now - it }
                        ?: IslandBus.chrono.value[sbn.key]?.takeIf { it.recordingKind == recording.kind }?.let { previous ->
                            if (!recording.paused && previous.pausedMs != null) now - previous.pausedMs else previous.base
                        }
                        ?: n.`when`.takeIf { it in 1..now } ?: sbn.postTime
                    else -> n.`when`
                }
                put(sbn.key, ChronoInfo(sbn.key, sbn.packageName, title, icon(context, sbn.packageName), base, down, sbn.postTime, stop,
                    pausedMs = if (recorder && recording.paused) recordingElapsed
                        ?: IslandBus.chrono.value[sbn.key]?.elapsed(now) ?: (now - base).coerceAtLeast(0) else pausedMs,
                    pause = if (recorder) recordingPause else buttons[ClockAction.PAUSE],
                    resume = if (recorder) recordingResume else buttons[ClockAction.START],
                    reset = buttons[ClockAction.RESET], recordingKind = recording.kind.takeIf { recorder }, open = n.contentIntent))
            } else {
                remove(sbn.key)
            }
        }

        val live = if (call == null && !isMedia && !recorder) LiveParser.parse(context, sbn, title) else null
        val liveWasKnown = sbn.key in IslandBus.live.value
        IslandBus.live.value = IslandBus.live.value.toMutableMap().apply {
            if (live != null) put(sbn.key, live) else remove(sbn.key)
        }
        // Новая «живая активность» сначала показывается широкой плашкой, потом сворачивается в капсулу
        if (live != null && !liveWasKnown && !initial && SettingsRepo.current.cardLive && SettingsRepo.current.liveBanner) {
            LiveParser.postBannerLater(context, sbn.key)
        }
        if (recorder) IslandBus.progress.value = IslandBus.progress.value - sbn.key
        // Навигатор и такси показываются своей карточкой, а не полоской прогресса или таймером
        if (live != null) {
            IslandBus.progress.value = IslandBus.progress.value - sbn.key
            IslandBus.chrono.value = IslandBus.chrono.value - sbn.key
        }

        val service = context as? NotificationListenerService
        if (!initial && service != null && !isMedia && call == null && !chrono && !hasProgress && live == null) {
            // Уведомление об оплате из банка — «Оплачено» с галочкой, а не карточка сообщения
            if (PayMonitor.onNotification(context, sbn)) return
            Messages.maybeShow(service, sbn, appLabel(context, sbn.packageName))
        }
    }

    private fun parseCall(context: Context, sbn: StatusBarNotification, title: String): CallInfo? {
        val n = sbn.notification
        val ex = n.extras
        val ticking = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0
        // CallStyle (Android 12+) кладёт кнопки в extras, у остальных звонилок ищем по подписи на языке телефона;
        // «Присоединиться» и «Отключиться» — у звонков в Discord и похожих приложениях
        var answer = pending(ex, "android.answerIntent")
        var decline = pending(ex, "android.declineIntent")
        var hangUp = pending(ex, "android.hangUpIntent")
        n.actions?.forEach { a ->
            val t = a.title?.toString()?.lowercase().orEmpty()
            when {
                answer == null && (AnswerWords.any { it in t } || JoinWords.any { it in t }) -> answer = a.actionIntent
                decline == null && DeclineWords.any { it in t } -> decline = a.actionIntent
                hangUp == null && (HangUpWords.any { it in t } || LeaveWords.any { it in t }) -> hangUp = a.actionIntent
            }
        }
        // Идёт разговор: системный отсчёт или постоянное уведомление с кнопкой «Завершить» и без «Ответить»
        val ongoing = ticking || (n.flags and Notification.FLAG_ONGOING_EVENT != 0 && hangUp != null && answer == null)
        val incoming = !ongoing && answer != null
        if (!incoming && !ongoing) return null

        val (name, personIcon) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val person = runCatching { ex.getParcelable<Person>(Notification.EXTRA_CALL_PERSON) }.getOrNull()
            (person?.name?.toString()?.takeIf { it.isNotBlank() } ?: title) to person?.icon
        } else {
            title to null
        }
        val avatar = runCatching {
            val icon = personIcon ?: n.getLargeIcon()
            icon?.loadDrawable(context)?.toBitmap(128, 128)?.asImageBitmap()
        }.getOrNull()
        return CallInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            name = name,
            avatar = avatar,
            incoming = incoming,
            // Без системного отсчёта время разговора идёт с момента, как появилось уведомление
            base = if (ticking) n.`when` else n.`when`.takeIf { it in 1..System.currentTimeMillis() } ?: sbn.postTime,
            postTime = sbn.postTime,
            answer = answer,
            decline = decline ?: hangUp,
            hangUp = hangUp ?: decline,
            open = n.contentIntent,
        )
    }

    /**
     * Звонок в Discord, Zoom, Teams и др.: у них нет категории звонка, поэтому узнаём по кнопкам. Входящий —
     * есть «Принять» («Присоединиться») и «Отклонить»; идёт разговор — постоянное уведомление с «Отключиться»
     * («Выйти», «Завершить»).
     */
    private fun parseVoip(context: Context, sbn: StatusBarNotification, title: String): CallInfo? {
        val n = sbn.notification
        var answer: PendingIntent? = null
        var decline: PendingIntent? = null
        var hangUp: PendingIntent? = null
        n.actions?.forEach { a ->
            val t = a.title?.toString()?.lowercase().orEmpty()
            when {
                answer == null && (AnswerWords.any { it in t } || JoinWords.any { it in t }) -> answer = a.actionIntent
                decline == null && DeclineWords.any { it in t } -> decline = a.actionIntent
                hangUp == null && (HangUpWords.any { it in t } || LeaveWords.any { it in t }) -> hangUp = a.actionIntent
            }
        }
        val ongoingFlag = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val incoming = answer != null && (decline != null || hangUp != null)
        val talking = !incoming && hangUp != null && ongoingFlag
        if (!incoming && !talking) return null
        val avatar = runCatching { n.getLargeIcon()?.loadDrawable(context)?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull()
        val now = System.currentTimeMillis()
        return CallInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            name = title.ifBlank { appLabel(context, sbn.packageName) },
            avatar = avatar,
            incoming = incoming,
            base = n.`when`.takeIf { it in 1..now } ?: sbn.postTime,
            postTime = sbn.postTime,
            answer = answer,
            decline = decline ?: hangUp,
            hangUp = hangUp ?: decline,
            open = n.contentIntent,
        )
    }

    /** Таймер на паузе или секундомер — по заголовку и каналу; по умолчанию таймер. */
    private fun looksLikeTimer(n: Notification, title: String): Boolean {
        val t = (title + " " + n.channelId.orEmpty()).lowercase()
        return when {
            "секундомер" in t || "stopwatch" in t -> false
            else -> true
        }
    }

    @Suppress("DEPRECATION")
    private fun pending(ex: android.os.Bundle, key: String): PendingIntent? =
        runCatching { ex.getParcelable<PendingIntent>(key) }.getOrNull()

    fun onRemoved(key: String) {
        Messages.forget(key)
        // Прочитали в самом приложении — из «Истории уведомлений» уходит, а плашка, которую держат дольше, — тоже
        Inbox.remove(key)
        Messages.onRemoved(key)
        LiveParser.forget(key)
        ClockControl.forget(key)
        LiveDebug.forget(key)
        if (key in IslandBus.calls.value) IslandBus.calls.value = IslandBus.calls.value - key
        if (key in IslandBus.progress.value) IslandBus.progress.value = IslandBus.progress.value - key
        if (key in IslandBus.chrono.value) IslandBus.chrono.value = IslandBus.chrono.value - key
        if (key in IslandBus.live.value) IslandBus.live.value = IslandBus.live.value - key
        if (key in IslandBus.ringingAlarms.value) IslandBus.ringingAlarms.value = IslandBus.ringingAlarms.value - key
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Приложения звонков без «звонковой» категории уведомлений: звонок узнаётся по кнопкам. */
object VoipApps {
    private val known = setOf(
        "com.discord",
        "us.zoom.videomeetings",
        "com.microsoft.teams",
        "com.skype.raider",
        "com.google.android.apps.meetings",
        "com.google.android.apps.tachyon",
        "jp.naver.line.android",
        "com.viber.voip",
    )

    fun isVoip(pkg: String) = pkg in known
}

/** Приложения, чьи постоянные уведомления остров показывает как «живые активности». */
object LiveApps {
    private val nav = setOf(
        "com.google.android.apps.maps",
        "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi",
        "ru.dublgis.dgismobile",
        "com.waze",
        "net.osmand",
        "net.osmand.plus",
        "com.here.app.maps",
        "cz.seznam.mapy",
        "app.organicmaps",
        "com.mapswithme.maps.pro",
    )
    private val ride = setOf(
        "ru.yandex.taxi",
        "com.yandex.go",
        "com.ubercab",
        "ee.mtakso.client",
        "ru.citymobil",
        "sinet.startup.indriver",
        "com.lyft.android",
    )
    private val delivery = setOf(
        "ru.yandex.eda",
        "ru.foodfox.client",
        "ru.samokat.app",
        "ru.instamart",
        "ru.sbermarket",
        "com.deliveryclub",
        "com.ubercab.eats",
        "ru.ozon.app.android",
        "com.wildberries.ru",
        "ru.beru.android",
    )

    fun kind(pkg: String, category: String?): LiveKind? = when {
        pkg in nav || category == Notification.CATEGORY_NAVIGATION -> LiveKind.NAV
        pkg in ride -> LiveKind.RIDE
        pkg in delivery -> LiveKind.DELIVERY
        else -> null
    }
}

/**
 * Подписи кнопок звонка на разных языках: остров смотрят во Вьетнаме, Индонезии, Таиланде, Бразилии,
 * Латинской Америке и не только — без них ответить и сбросить из острова там не получалось.
 */
private val AnswerWords = listOf(
    "ответ", "принять", "жауап", "қабылдау", "answer", "accept", "trả lời", "nghe", "jawab", "terima", "angkat", "contestar", "responder",
    "atender", "aceptar", "aceitar", "รับสาย", "cevapla", "yanıtla", "kabul", "annehmen", "répondre", "rispondi", "accetta",
    "odbierz", "відповісти", "прийняти", "sagutin", "接听", "接聽", "応答", "응답", "받기",
)
private val DeclineWords = listOf(
    "отклон", "сброс", "қабылдамау", "бас тарту", "decline", "reject", "dismiss", "từ chối", "tolak", "rechazar", "recusar", "rejeitar", "ปฏิเสธ",
    "reddet", "ablehnen", "refuser", "rifiuta", "odrzuć", "відхилити", "tanggihan", "拒接", "拒絕", "拒否", "거절",
)
private val HangUpWords = listOf(
    "заверш", "положить", "hang", "end call", "kết thúc", "gác máy", "akhiri", "colgar", "finalizar", "desligar",
    "encerrar", "วางสาย", "kapat", "auflegen", "beenden", "raccrocher", "riaggancia", "zakończ", "завершити", "ibaba",
    "挂断", "掛斷", "終了", "종료", "аяқтау",
)

/** «Присоединиться» к звонку (Discord и др.) — как «Принять» */
private val JoinWords = listOf("join", "присоедин", "gabung", "entrar", "participar", "қосылу", "beitreten", "rejoindre", "unirse")

/** «Отключиться», «Выйти» из звонка (Discord, Zoom и др.) — как «Завершить» */
private val LeaveWords = listOf(
    "disconnect", "leave", "отключ", "выйти", "покинуть", "putus", "keluar", "tinggalkan", "desconectar", "sair", "ажырату", "шығу",
    "trennen", "verlassen", "quitter", "salir",
)
