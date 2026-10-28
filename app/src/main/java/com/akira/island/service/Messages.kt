package com.akira.island.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import androidx.core.graphics.drawable.toBitmap
import com.akira.island.core.AppMessage
import com.akira.island.core.IslandBus
import com.akira.island.core.MsgAction
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.R
import com.akira.island.data.AccentMode
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios

/**
 * Уведомления приложений в острове, как баннеры на iPhone: сообщения из Telegram,
 * WhatsApp, почта и т. п. Показываются только те, что система и так показала бы всплывающими.
 */
object Messages {
    /** Последний показанный текст для каждого уведомления — чтобы не повторять одно и то же */
    private val shown = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 100
    }

    /** Куда только что ответили из острова: обновление этого уведомления с нашим же ответом не показываем */
    private val repliedAt = HashMap<String, Long>()

    private val skipCategories = setOf(
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_STATUS,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_STOPWATCH,
        Notification.CATEGORY_LOCATION_SHARING,
    )

    fun maybeShow(service: NotificationListenerService, sbn: StatusBarNotification, appName: String) {
        val s = SettingsRepo.current
        if (!s.cardMessages || !s.enabled) return
        val n = sbn.notification ?: return
        if (sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (n.category in skipCategories) return
        // Пока открыто само приложение, оно и так показывает сообщение — если не попросили показывать и тогда
        if (!s.msgInApp && IslandBus.foregroundPackage.value == sbn.packageName) return

        // Тихие, свёрнутые и заглушённые «Не беспокоить» не показываем — как и система
        val ranking = NotificationListenerService.Ranking()
        val ranked = runCatching { service.currentRanking?.getRanking(sbn.key, ranking) == true }.getOrDefault(false)
        if (ranked) {
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
            if (!ranking.matchesInterruptionFilter()) return
        }

        // Экран блокировки: как в шторке — уведомления там скрыты, «секретные» не показываются,
        // у остальных без разрешения показывать содержимое видно только название приложения
        val locked = IslandBus.locked.value
        if (locked && !Lockscreen.showsNotifications(service)) return
        val visibility = if (ranked && ranking.lockscreenVisibilityOverride != NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE) {
            ranking.lockscreenVisibilityOverride
        } else {
            n.visibility
        }
        val secret = visibility == Notification.VISIBILITY_SECRET
        if (locked && secret) return
        val sensitive = Lockscreen.isSecure(service) &&
            (secret || visibility == Notification.VISIBILITY_PRIVATE && !Lockscreen.showsPrivate(service))

        val ex = n.extras ?: return
        // Мессенджер дописал в уведомление ваш же ответ — это не новое сообщение
        if (ownLastMessage(ex)) return
        val replied = repliedAt[sbn.key]
        if (replied != null && SystemClock.elapsedRealtime() - replied < 5_000) return
        val title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
        val text = lastMessage(ex)
            ?: (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim()
            ?: ""
        if (title.isEmpty() && text.isEmpty()) return

        val signature = "$title\n$text"
        // Тот же текст — не повторяем. Новое сообщение в той же переписке показываем, даже если мессенджер
        // обновляет уведомление с флагом «не звенеть повторно» (WhatsApp ставит его на каждое следующее)
        if (shown[sbn.key] == signature) return
        shown[sbn.key] = signature

        val avatar = runCatching {
            n.getLargeIcon()?.loadDrawable(service)?.toBitmap(128, 128)?.asImageBitmap()
        }.getOrNull()
        val actions = if (s.msgActions) buttons(n) else emptyList()
        val message = AppMessage(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appName,
            text = text,
            avatar = avatar,
            appIcon = NotificationParser.icon(service, sbn.packageName),
            intent = n.contentIntent,
            actions = actions,
            // Разбор палитры нужен только для цвета кнопок «Из аватара»
            accent = if (s.accentMode == AccentMode.ADAPTIVE) accentOf(avatar ?: NotificationParser.icon(service, sbn.packageName)) else null,
            history = history(ex),
            sensitive = sensitive,
        )
        IslandBus.post(
            Notice(
                title = title.ifEmpty { appName },
                icon = Ios.load(service, R.drawable.ios_chatbubble_ellipses),
                tint = Tints.Blue,
                style = NoticeStyle.MESSAGE,
                message = message,
                // Текст читать дольше, чем короткое событие, а с кнопками — ещё дольше: нужно успеть нажать
                extraMs = if (actions.isEmpty()) 2_000 else 4_500,
            ),
        )
        // Для «Системных плашек»: какой мессенджер и всплывает ли у него ещё своя плашка поверх острова
        SystemBanners.onMessage(sbn.packageName, n.channelId, popsUp = !ranked || ranking.importance >= NotificationManager.IMPORTANCE_HIGH)
    }

    /**
     * Кнопки из уведомления, как в шторке: сначала «Ответить» (с полем ввода), потом ещё одна —
     * «Пометить прочитанным» и т. п. Больше двух в острове не помещается.
     */
    private fun buttons(n: Notification): List<MsgAction> {
        val all = n.actions?.mapNotNull { a ->
            val intent = a.actionIntent ?: return@mapNotNull null
            val title = a.title?.toString()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val inputs = a.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
            MsgAction(title, intent, inputs)
        }.orEmpty()
        val reply = all.firstOrNull { it.isReply }
        val read = all.firstOrNull { a ->
            !a.isReply && listOf("прочит", "read").any { it in a.title.lowercase() }
        }
        val other = all.firstOrNull { !it.isReply }
        return listOfNotNull(reply, read ?: other)
    }

    /** Все сообщения переписки для списка под карточкой: у групп с именем отправителя. */
    private fun history(ex: Bundle): List<String> {
        @Suppress("DEPRECATION")
        val arr: Array<Parcelable> = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        val group = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        return arr.mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            val text = b.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val sender = b.getCharSequence("sender")?.toString()
            if (group && !sender.isNullOrBlank()) "$sender: $text" else text
        }.takeLast(6)
    }

    /** Яркий цвет аватара или значка приложения — читается на чёрном и подходит для кнопок. */
    private fun accentOf(image: ImageBitmap?): Color? {
        image ?: return null
        return runCatching {
            val palette = Palette.from(image.asAndroidBitmap()).maximumColorCount(12).generate()
            val rgb = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDominantColor(0)))
            if (rgb == 0) return null
            val c = Color(rgb)
            // Тёмный цвет на чёрной капсуле не виден — подсвечиваем
            if (c.luminance() < 0.4f) lerp(c, Color.White, 0.45f) else c
        }.getOrNull()
    }

    /** У переписок (MessagingStyle) берём последнее сообщение с именем отправителя в группах. */
    private fun lastMessage(ex: Bundle): String? {
        @Suppress("DEPRECATION")
        val arr: Array<Parcelable>? = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = arr?.lastOrNull() as? Bundle ?: return null
        val text = last.getCharSequence("text")?.toString()?.trim() ?: return null
        val group = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val sender = last.getCharSequence("sender")?.toString()
        return if (group && !sender.isNullOrBlank()) "$sender: $text" else text
    }

    /**
     * Последнее сообщение переписки написали вы: в MessagingStyle у своих сообщений нет отправителя
     * или отправитель — сам пользователь (EXTRA_MESSAGING_PERSON).
     */
    private fun ownLastMessage(ex: Bundle): Boolean {
        @Suppress("DEPRECATION")
        val arr: Array<Parcelable> = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return false
        val last = arr.lastOrNull() as? Bundle ?: return false
        if (!last.containsKey("sender") && !last.containsKey("sender_person") && !last.containsKey("person")) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && sentByMe(last, ex)
    }

    /** Отправитель сообщения [message] — тот же человек, что EXTRA_MESSAGING_PERSON уведомления. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sentByMe(message: Bundle, ex: Bundle): Boolean {
        @Suppress("DEPRECATION")
        val sender = runCatching { message.getParcelable<Person>("sender_person") }.getOrNull()
        @Suppress("DEPRECATION")
        val me = runCatching { ex.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON) }.getOrNull() ?: return false
        if (me.key != null && sender?.key == me.key) return true
        val name = (sender?.name ?: message.getCharSequence("sender"))?.toString()
        return !name.isNullOrBlank() && name == me.name?.toString()
    }

    /** Ответ ушёл из острова: ближайшее обновление этого уведомления — наш же ответ, его не показываем. */
    fun onReplied(key: String) {
        repliedAt[key] = SystemClock.elapsedRealtime()
    }

    fun forget(key: String) {
        shown.remove(key)
        repliedAt.remove(key)
    }
}

/** Настройки экрана блокировки, как их понимает шторка. */
private object Lockscreen {
    /** Есть ли PIN-код, графический ключ или пароль: без них система содержимое не прячет. */
    fun isSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** «Уведомления на заблокированном экране» не выключены. */
    fun showsNotifications(context: Context): Boolean = secureInt(context, "lock_screen_show_notifications", 1) != 0

    /** Разрешено показывать содержимое уведомлений; если настройку не прочитать — считаем, что нет. */
    fun showsPrivate(context: Context): Boolean = secureInt(context, "lock_screen_allow_private_notifications", 0) != 0

    /** Настройку ни разу не меняли — у системы она включена; прочитать нельзя — [unreadable]. */
    private fun secureInt(context: Context, name: String, unreadable: Int): Int = try {
        Settings.Secure.getInt(context.contentResolver, name)
    } catch (_: Settings.SettingNotFoundException) {
        1
    } catch (_: Exception) {
        unreadable
    }
}
