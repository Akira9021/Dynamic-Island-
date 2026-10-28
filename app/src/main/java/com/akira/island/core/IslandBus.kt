package com.akira.island.core

import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Общее состояние острова. Службы пишут сюда, капсула и приложение читают.
 * Всё живёт в одном процессе, поэтому хватает обычного синглтона.
 */
object IslandBus {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val serviceRunning = MutableStateFlow(false)
    val expanded = MutableStateFlow(false)
    /** id активности, которую пользователь выбрал (например, кликом по пузырю) */
    val focus = MutableStateFlow<String?>(null)

    val media = MutableStateFlow<MediaInfo?>(null)
    val lyrics = MutableStateFlow<Lyrics?>(null)
    /** Как идёт поиск текста для трека с ключом first */
    val lyricsStatus = MutableStateFlow<Pair<String, LyricsStatus>?>(null)
    val progress = MutableStateFlow<Map<String, ProgressInfo>>(emptyMap())
    val chrono = MutableStateFlow<Map<String, ChronoInfo>>(emptyMap())
    val calls = MutableStateFlow<Map<String, CallInfo>>(emptyMap())
    /** Входящий звонок смахнули — в острове его нет, пока он не закончится или не станет разговором */
    val callHidden = MutableStateFlow(false)
    /** Что может сделать только сама служба спец. возможностей; без службы команда теряется */
    val commands = MutableSharedFlow<ServiceCommand>(extraBufferCapacity = 4)
    val live = MutableStateFlow<Map<String, LiveInfo>>(emptyMap())
    /** Экран оплаты телефоном на виду — «Оплата · К терминалу», пока не заплатили или не ушли (PayMonitor) */
    val payReady = MutableStateFlow<PayReady?>(null)
    /** Ближайший будильник любого приложения «Часы» */
    val nextAlarm = MutableStateFlow<AlarmInfo?>(null)
    /** Звонящие будильники и таймеры других приложений («Часы»): ключи их уведомлений */
    val ringingAlarms = MutableStateFlow<Set<String>>(emptySet())
    val torch = MutableStateFlow(TorchState())
    val timer = MutableStateFlow<TimerState?>(null)
    val stopwatch = MutableStateFlow<StopwatchState?>(null)
    val notice = MutableStateFlow<Notice?>(null)
    /** «История уведомлений»: показанные сообщения, которые ещё ждут в острове */
    val inbox = MutableStateFlow<List<InboxItem>>(emptyList())
    /** Идёт ввод ответа на уведомление: окну острова нужен фокус, чтобы показать клавиатуру */
    val reply = MutableStateFlow<ReplyState?>(null)
    /** Наклон телефона от -1 до 1: по нему сдвигаются блики Liquid Glass */
    val glassTilt = MutableStateFlow(0f)
    /** До какого момента (elapsedRealtime) показывать пример активности — пока двигают ползунок ширины */
    val widthPreviewUntil = MutableStateFlow(0L)
    /** Человек читает или отвечает — уведомление не убирается по таймеру */
    val noticeHold = MutableStateFlow(false)
    val battery = MutableStateFlow(BatteryInfo())
    /** Режим звука (AudioManager.RINGER_MODE_*) и «Не беспокоить» — для кнопок раскрытого острова */
    val ringer = MutableStateFlow(2)
    val dnd = MutableStateFlow(false)
    val foregroundPackage = MutableStateFlow<String?>(null)
    val landscape = MutableStateFlow(false)
    /** Поворот экрана для острова «У камеры»: −90 — камера слева, 90 — справа, 0 — портрет */
    val displayRotation = MutableStateFlow(0)
    /** На сколько градусов повёрнут остров в своём окне сейчас (0 — как обычно, сверху) */
    val contentRotation = MutableStateFlow(0)
    /** Вне часов «По расписанию» остров не показывается */
    val offSchedule = MutableStateFlow(false)
    /** Экран включён. Пока он погашен, датчик наклона и чтение экрана навигатора не работают — бережём батарею */
    val screenOn = MutableStateFlow(true)
    /** Включено энергосбережение Android — с «Экономией заряда» остров без живых эффектов */
    val powerSave = MutableStateFlow(false)
    /** Телефон заблокирован PIN-кодом, графическим ключом или паролем: тексты сообщений и кнопки скрыты */
    val locked = MutableStateFlow(false)
    /** На экране — экран блокировки (с PIN-кодом или без): в капсуле замок, если он включён в «Событиях» */
    val keyguard = MutableStateFlow(false)
    /** Открыт экран «Размер и положение»: остров красный, чтобы подогнать его под камеру */
    val calibrating = MutableStateFlow(false)
    /** Играет та самая песня — остров празднует (пасхалка, Egg.kt) */
    val egg = MutableStateFlow(false)
    /** Последнее касание раскрытого острова — чтобы не сворачивать его из-под пальца */
    @Volatile
    var lastTouch = 0L

    private val queue = Channel<Notice>(capacity = 6, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            for (n in queue) {
                val s = SettingsRepo.current
                // «Задержка появления»; подтверждения того, что вы только что сделали (отправлено, оплачено), — сразу
                if (s.noticeDelayMs > 0 && n.style != NoticeStyle.SENT && n.style != NoticeStyle.PAY_DONE) delay(s.noticeDelayMs.toLong())
                notice.value = n
                val message = n.style == NoticeStyle.MESSAGE
                val duration = if (message && s.messageSeconds > 0) s.messageSeconds * 1000L else s.noticeDurationMs + n.extraMs
                // Уведомление уходит по времени или раньше, если его смахнули кликом
                withTimeoutOrNull(duration) { notice.first { it?.id != n.id } }
                // «Пока не смахнёте»: плашка ждёт, пока её не уберут; пришло следующее событие — уступает ему
                if (message && s.messageSeconds < 0 && notice.value?.id == n.id && waitingIsEmpty()) {
                    val seen = posted.value
                    combine(notice, posted) { cur, p -> cur?.id != n.id || p != seen }.first { it }
                }
                // Пока отвечают или листают переписку, карточка остаётся; потом ещё чуть-чуть
                if (notice.value?.id == n.id && noticeHold.value) {
                    while (notice.value?.id == n.id && noticeHold.value) delay(200)
                    if (notice.value?.id == n.id) withTimeoutOrNull(1500) { notice.first { it?.id != n.id } }
                }
                if (notice.value?.id == n.id) notice.value = null
                // Короткая пауза между плашками, чтобы они не слипались, — но без заметного ожидания
                delay(110)
            }
        }
    }

    /** Сколько событий отправлено в очередь — по нему плашка «пока не смахнёте» узнаёт, что пришло следующее */
    private val posted = MutableStateFlow(0)

    fun post(n: Notice) {
        queue.trySend(n)
        posted.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun waitingIsEmpty(): Boolean = queue.isEmpty
}

/** Команды службе: скриншот без острова, блокировка экрана, выключение острова (для банков). */
enum class ServiceCommand { SCREENSHOT, LOCK, TURN_OFF }
