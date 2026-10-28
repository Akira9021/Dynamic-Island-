package com.akira.island.core

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import com.akira.island.data.AppLanguage
import com.akira.island.data.SettingsRepo
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Язык приложения и острова. Русский и английский пишутся парой прямо в коде: tr("Зарядка", "Charging").
 * Остальные языки (индонезийский, португальский, казахский) — таблицы в assets/i18n/<код>.json: английская
 * строка → перевод. Чего в таблице нет, показывается по-английски. «Как в системе» — язык телефона, если он
 * есть в приложении, иначе английский.
 */
object Lang {
    /** Языки приложения: код → таблица переводов (у русского и английского таблиц нет) */
    val codes = listOf("ru", "en", "id", "pt", "kk")

    private var assets: android.content.res.AssetManager? = null
    private val tables = HashMap<String, Map<String, String>>()

    /** Таблицы переводов читаются из assets — зовёт приложение при запуске. */
    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    /** Код текущего языка: ru, en, id, pt или kk */
    val code: String
        get() = when (val l = SettingsRepo.current.language) {
            AppLanguage.SYSTEM -> systemCode()
            else -> l.code
        }

    /** Не русский: подписи, единицы и форматы — как в английском, если у языка нет своих */
    val en: Boolean get() = code != "ru"

    /** Для дат: «пятница, 24 октября», «Friday, October 24», «Jumat, 24 Oktober» */
    val locale: Locale
        get() = when (code) {
            "ru" -> Locale.forLanguageTag("ru")
            "id" -> Locale.forLanguageTag("id")
            "pt" -> Locale.forLanguageTag("pt-BR")
            "kk" -> Locale.forLanguageTag("kk")
            else -> Locale.ENGLISH
        }

    private fun systemCode(): String {
        val lang = runCatching { Resources.getSystem().configuration.locales[0].language }.getOrNull() ?: Locale.getDefault().language
        // Индонезийский у старых Android — «in»
        val c = if (lang == "in") "id" else lang
        return if (c in codes) c else "en"
    }

    /** Таблица перевода языка [c]: английская строка → перевод. Пустая, если файла нет. */
    fun table(c: String): Map<String, String> = synchronized(tables) {
        tables.getOrPut(c) {
            runCatching {
                val raw = assets?.open("i18n/$c.json")?.bufferedReader()?.use { it.readText() } ?: return@runCatching emptyMap()
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
            }.getOrDefault(emptyMap())
        }
    }

    /** Строка на текущем языке по паре «русская — английская». */
    fun pick(ru: String, en: String): String = when (val c = code) {
        "ru" -> ru
        "en" -> en
        else -> table(c)[en] ?: en
    }
}

/** Строка на текущем языке. */
fun tr(ru: String, en: String): String = Lang.pick(ru, en)

/**
 * Строка с подстановками на текущем языке: tr("Через {0} минут", "In {0} minutes", n). В переводах
 * {0}, {1}… можно переставлять — значения встанут куда нужно.
 */
fun trf(ru: String, en: String, vararg args: Any?): String {
    var s = Lang.pick(ru, en)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

/**
 * Сменили язык (в настройках или в системе) — всё внутри рисуется заново уже на новом языке.
 * Состояние снаружи (открытая вкладка и экран) остаётся.
 */
@Composable
fun LangScope(content: @Composable () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    // Язык системы меняется вместе с конфигурацией — чтение подписывает на неё
    LocalConfiguration.current
    key(s.language, Lang.code) { content() }
}
