package com.akira.island.service

import android.content.Context
import com.akira.island.core.AppMessage
import com.akira.island.core.IslandBus
import com.akira.island.core.InboxItem
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Почему не показано»: последние уведомления, которые остров разбирал как сообщения, и что он решил.
 * Только приложение и причина — без имён и текстов; только в памяти, после перезапуска пусто.
 */
object NotifDebug {
    val log = MutableStateFlow<List<String>>(emptyList())

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun add(pkg: String, result: String) {
        val label = NotifApps.label(pkg)
        log.value = (listOf("${clock.format(Date())} · $label\n$result") + log.value).take(30)
    }
}

/**
 * Приложения, от которых приходили уведомления, — для списка «Приложения» в настройках уведомлений.
 * Список приложений телефона без особого разрешения Android не даёт, поэтому запоминаем тех, кто писал.
 */
object NotifApps {
    private const val PREFS = "island_apps"
    private const val KEY = "apps"
    private const val LIMIT = 80

    /** Пакет → название, новые — в конце */
    val apps = MutableStateFlow<Map<String, String>>(emptyMap())
    private var loaded = false
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching { json.decodeFromString(serializer, raw) }.onSuccess { apps.value = it }
    }

    fun label(pkg: String): String = apps.value[pkg] ?: pkg

    fun seen(context: Context, pkg: String, label: String) {
        load(context)
        if (apps.value[pkg] == label) return
        val next = LinkedHashMap(apps.value).apply {
            remove(pkg)
            put(pkg, label)
            while (size > LIMIT) remove(keys.first())
        }
        apps.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(serializer, next)).apply()
    }
}

/**
 * «История уведомлений»: показанные сообщения ждут в острове — значки приложений в капсуле, список при раскрытии.
 * Уходят, когда их прочитали в самом приложении (уведомление убрано), открыли из острова, очистили
 * или прошло время из настроек. Хранятся только в памяти.
 */
object Inbox {
    private const val LIMIT = 20

    fun add(m: AppMessage, title: String) {
        val item = InboxItem(
            key = m.key, pkg = m.pkg, appName = m.appName, title = title, text = m.text, avatar = m.avatar,
            appIcon = m.appIcon, intent = m.intent, time = System.currentTimeMillis(), sensitive = m.sensitive,
        )
        IslandBus.inbox.value = (IslandBus.inbox.value.filter { it.key != m.key } + item).takeLast(LIMIT)
    }

    fun remove(key: String) {
        if (IslandBus.inbox.value.any { it.key == key }) IslandBus.inbox.value = IslandBus.inbox.value.filter { it.key != key }
    }

    fun clear() {
        IslandBus.inbox.value = emptyList()
    }

    /** Сколько ещё ждать до того, как истечёт самое старое; null — ждать нечего (или «пока не прочитаете»). */
    fun nextExpiry(now: Long): Long? {
        val minutes = SettingsRepo.current.historyMinutes
        if (minutes <= 0) return null
        val oldest = IslandBus.inbox.value.minOfOrNull { it.time } ?: return null
        return (oldest + minutes * 60_000L - now).coerceAtLeast(0)
    }

    /** Убрать истёкшие; при выключенной истории — всё. */
    fun prune(now: Long) {
        val minutes = SettingsRepo.current.historyMinutes
        val list = IslandBus.inbox.value
        if (list.isEmpty()) return
        val keep = when {
            minutes == 0 -> emptyList()
            minutes < 0 -> list
            else -> list.filter { now - it.time < minutes * 60_000L }
        }
        if (keep.size != list.size) IslandBus.inbox.value = keep
    }
}
