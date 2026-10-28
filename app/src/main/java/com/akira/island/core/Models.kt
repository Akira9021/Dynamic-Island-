package com.akira.island.core

import android.app.PendingIntent
import android.app.RemoteInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.atomic.AtomicLong

data class MediaInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val artist: String,
    val album: String?,
    val art: ImageBitmap?,
    val accent: Color,
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() момента, к которому относится positionMs */
    val positionTime: Long,
    val speed: Float,
    val playing: Boolean,
    val canSeek: Boolean,
    /** «Нравится» и «Не нравится» из самого плеера (Яндекс Музыка, YouTube Music и др.), если он их даёт */
    val like: PlayerAction? = null,
    val dislike: PlayerAction? = null,
) {
    fun positionAt(now: Long): Long {
        val p = if (playing) positionMs + ((now - positionTime) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Своя кнопка плеера: что отправить ему и включена ли она уже (трек уже нравится). */
data class PlayerAction(val action: String, val on: Boolean)

data class LyricLine(val timeMs: Long, val text: String)

enum class LyricsStatus { LOADING, MISSING, FOUND }

/** [synced] = false — у трека нашёлся только обычный текст, строки разложены по его длине. */
data class Lyrics(val key: String, val lines: List<LyricLine>, val synced: Boolean = true) {
    /** Индекс строки, которая поётся в момент [pos], или -1 до первой строки. */
    fun indexAt(pos: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= pos) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

data class ProgressInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val icon: ImageBitmap?,
    val progress: Int,
    val max: Int,
    val indeterminate: Boolean,
    val postTime: Long,
) {
    val fraction: Float get() = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f
}

data class ChronoInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val icon: ImageBitmap?,
    /** Notification.when — настенное время */
    val base: Long,
    val countDown: Boolean,
    val postTime: Long,
    /** Кнопка «Стоп» из уведомления (запись экрана, диктофон) */
    val stop: PendingIntent? = null,
    /** На паузе: застывшее время (таймер или секундомер «Часов») */
    val pausedMs: Long? = null,
    /** Кнопки из уведомления «Часов»: остров нажимает их, не открывая приложение */
    val pause: PendingIntent? = null,
    val resume: PendingIntent? = null,
    val reset: PendingIntent? = null,
    /** Идёт запись (диктофон, запись экрана) — узнали по приложению и кнопке «Стоп» */
    val recording: Boolean = false,
) {
    fun elapsed(nowWall: Long): Long = pausedMs ?: if (countDown) (base - nowWall).coerceAtLeast(0) else nowWall - base

    /**
     * Показывать уже нечего: обратный отсчёт дошёл до нуля больше 3 секунд назад, а приложение так и не убрало
     * уведомление (иначе в острове навсегда «00:00»), или отсчёт «с начала» начинается в будущем.
     */
    fun finished(nowWall: Long): Boolean = pausedMs == null &&
        (countDown && nowWall > base + DONE_MS || !countDown && base > nowWall + 60_000)

    companion object {
        /** Сколько закончившийся обратный отсчёт ещё виден в острове */
        const val DONE_MS = 3_000L
    }

    /** Запись экрана или звука — показывается красным. */
    val isRecording: Boolean
        get() = recording || title.lowercase().let { t -> "record" in t || "запис" in t || "grava" in t || "rekam" in t }
}

/** Звонок из уведомления звонилки: входящий или идущий разговор. */
data class CallInfo(
    val key: String,
    val pkg: String,
    val name: String,
    val avatar: ImageBitmap?,
    val incoming: Boolean,
    /** Notification.when — начало разговора, настенное время */
    val base: Long,
    val postTime: Long,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
    val hangUp: PendingIntent?,
    val open: PendingIntent?,
)

enum class LiveKind { NAV, RIDE, DELIVERY }

/** Куда поворачивать по подсказке навигатора. */
enum class Turn { STRAIGHT, LEFT, RIGHT, UTURN }

/** «Живая активность» из постоянного уведомления: навигатор, такси, доставка. */
data class LiveInfo(
    val key: String,
    val pkg: String,
    val kind: LiveKind,
    val title: String,
    val text: String,
    val sub: String,
    val icon: ImageBitmap?,
    /** Крупная картинка из уведомления: стрелка манёвра, машина, курьер */
    val art: ImageBitmap?,
    val postTime: Long,
    val open: PendingIntent?,
    /** Расстояние до манёвра или цели («300 м», «3,7 км»), если нашлось в уведомлении */
    val distance: String? = null,
    /** Прибытие и время в пути («15:52 · 9 мин») */
    val eta: String? = null,
    /** Куда поворачивать — по тексту или описанию значка в уведомлении навигатора */
    val turn: Turn? = null,
    /** До какого времени (System.currentTimeMillis) показывать; 0 — пока живо само уведомление. Нужно пробной активности. */
    val expiresAt: Long = 0,
)

data class TorchState(
    val on: Boolean = false,
    val cameraId: String? = null,
    val level: Int = 1,
    val maxLevel: Int = 1,
)

data class TimerState(
    val totalMs: Long,
    /** SystemClock.elapsedRealtime() окончания */
    val endAt: Long,
    val pausedLeft: Long? = null,
    val ringing: Boolean = false,
) {
    fun left(now: Long): Long = pausedLeft ?: (endAt - now).coerceAtLeast(0)
    val paused: Boolean get() = pausedLeft != null && !ringing
}

data class StopwatchState(val startedAt: Long?, val accumulated: Long) {
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { now - it } ?: 0)
    val running: Boolean get() = startedAt != null
}

data class BatteryInfo(val level: Int = -1, val charging: Boolean = false)

/** Как событие выглядит в острове. */
enum class NoticeStyle {
    /** Широкая плашка почти во всю ширину: зарядка, наушники, Wi-Fi */
    WIDE,
    /** Пилюля высотой со свёрнутый остров, вздрагивает при появлении: режим звука, замок */
    PILL,
    /** Уведомление приложения: аватар, имя и текст */
    MESSAGE,
    /** Оплата прошла: зелёная галочка и сумма */
    PAY_DONE,
    /** Навигатор, такси или доставка только что запустились: широкая плашка, потом капсула */
    LIVE,
    /** Поставили будильник: время подъёма и сколько осталось спать */
    ALARM,
    /** Пасхалка: окно диалога, как в игре, — строка печатается по букве */
    EGG,
    /** Ответ ушёл: «Отправлено» с галочкой и кому */
    SENT,
    /** Новая версия: значок приложения, что нового и кнопки «Позже» и «Обновить»; после установки — «Обновлено» */
    UPDATE,
}

/** На экране — оплата телефоном: в каком приложении и его значок. Держится, пока не заплатили или не ушли. */
data class PayReady(val pkg: String, val icon: ImageBitmap?)

/**
 * Ближайший будильник (AlarmManager.getNextAlarmClock): когда прозвенит, какое приложение его поставило
 * и что открыть нажатием — обычно экран будильников «Часов».
 */
data class AlarmInfo(val triggerAt: Long, val pkg: String?, val show: PendingIntent?)

/**
 * Кнопка из уведомления: «Ответить», «Пометить прочитанным» и т. п.
 * [inputs] не пуст у кнопки с полем ответа — тогда остров показывает строку ввода.
 */
class MsgAction(
    val title: String,
    val intent: PendingIntent,
    val inputs: List<RemoteInput> = emptyList(),
) {
    val isReply: Boolean get() = inputs.isNotEmpty()
}

/** Идёт ввод ответа на уведомление [noticeId] через кнопку [action]. */
class ReplyState(val noticeId: Long, val action: MsgAction)

/** Уведомление другого приложения, показанное в острове. */
data class AppMessage(
    val key: String,
    val pkg: String,
    val appName: String,
    val text: String,
    val avatar: ImageBitmap?,
    val appIcon: ImageBitmap?,
    val intent: PendingIntent?,
    /** Кнопки из уведомления, не больше двух: ответить и ещё одна */
    val actions: List<MsgAction> = emptyList(),
    /** Цвет, подобранный по аватару или значку приложения, — для режима «Из аватара» */
    val accent: Color? = null,
    /** Все непрочитанные сообщения переписки, новое — последним: их показывает счётчик со стрелкой */
    val history: List<String> = emptyList(),
    /** На заблокированном экране вместо имени и текста — только название приложения, как в шторке */
    val sensitive: Boolean = false,
)

/** Уведомление в «Истории уведомлений»: ждёт в острове, пока его не прочитают, не очистят или не выйдет время. */
data class InboxItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val avatar: ImageBitmap?,
    val appIcon: ImageBitmap?,
    val intent: PendingIntent?,
    /** Когда пришло, System.currentTimeMillis */
    val time: Long,
    val sensitive: Boolean,
)

/**
 * Короткое событие в стиле iOS: слева значок и подпись, справа состояние.
 * [battery] рисует справа батарейку, [ring] — кольцо (например, заряд наушников),
 * [caption] — мелкая серая строка над подписью («Подключено»).
 */
data class Notice(
    val title: String,
    val state: String? = null,
    val icon: ImageVector,
    val tint: Color,
    val battery: Int? = null,
    val ring: Float? = null,
    val caption: String? = null,
    val style: NoticeStyle = NoticeStyle.WIDE,
    val message: AppMessage? = null,
    /**
     * Насколько дольше «Показа событий» (Анимации → Показ событий) держать плашку: сообщению с кнопками
     * нужно успеть нажать, оплате — приложить телефон. Всё считается от этой настройки.
     */
    val extraMs: Long = 0,
    /** Значок приложения, например банка при оплате */
    val appIcon: ImageBitmap? = null,
    /** Данные «живой активности» для плашки NoticeStyle.LIVE */
    val live: LiveInfo? = null,
    /** Какое приложение открыть нажатием на плашку (например, само это — «Доступна новая версия») */
    val openPkg: String? = null,
    /** Будильник для плашки NoticeStyle.ALARM */
    val alarm: AlarmInfo? = null,
    /** Новая версия для плашки NoticeStyle.UPDATE; null — уже обновились */
    val update: com.akira.island.update.Release? = null,
    val id: Long = ids.incrementAndGet(),
) {
    private companion object {
        val ids = AtomicLong()
    }
}

object Tints {
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF453A)
    val Blue = Color(0xFF0A84FF)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Purple = Color(0xFFBF5AF2)
    val Teal = Color(0xFF64D2FF)
    val Gray = Color(0xFF8E8E93)
    val Lavender = Color(0xFFC6C3F7)
}

fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
