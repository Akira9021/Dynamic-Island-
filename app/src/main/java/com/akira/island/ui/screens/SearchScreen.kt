package com.akira.island.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.akira.island.core.Lang
import com.akira.island.core.tr
import com.akira.island.data.matchesSettingsSearch
import com.akira.island.ui.*

@Composable
fun SettingsSearchAction(open: (Screen) -> Unit) {
    IconButton(onClick = { open(Screen.SEARCH) }) {
        Icon(Icons.Rounded.Search, tr("Поиск по настройкам", "Search settings"), tint = Ui.Primary)
    }
}

private data class SearchItem(val title: String, val section: String, val screen: Screen, val words: String)

@Composable
fun SearchScreen(query: String, onQuery: (String) -> Unit, onBack: () -> Unit, open: (Screen) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val items = remember(Lang.code) { searchCatalog() }
    val found = remember(query, items) { items.filter { matchesSettingsSearch(query, "${it.title} ${it.section} ${it.words}") } }
    Page(tr("Поиск по настройкам", "Search settings"), onBack, preview = {
        OutlinedTextField(query, { onQuery(it.take(80)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(tr("Название или функция", "Name or feature")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Rounded.Close, tr("Очистить поиск", "Clear search"))
                }
            })
    }) {
        SettingsHint(tr("Нажмите на результат, чтобы открыть нужный раздел.", "Tap a result to open its settings section."))
        if (found.isEmpty()) Text(tr("Ничего не найдено", "No results"), color = Ui.Sub, modifier = Modifier.padding(12.dp))
        else Group {
            found.forEachIndexed { index, item ->
                GroupItem(posOf(index, found.size), Icons.Rounded.Search, item.title, item.section,
                    color = Tile.Indigo, onClick = { keyboard?.hide(); open(item.screen) }) { Arrow() }
            }
        }
    }
}

private fun searchCatalog(): List<SearchItem> {
    val appearance = tr("Оформление", "Appearance")
    val features = tr("Функции", "Features")
    return listOf(
        SearchItem(tr("Положение и размер", "Position and size"), appearance, Screen.SIZE, "камера вырез notch camera ширина высота offset position width height"),
        SearchItem(tr("Размер и форма", "Size and shape"), appearance, Screen.LOOK, "радиус скругление углы карточка текст icons radius rounded corners card scale"),
        SearchItem(tr("Цвет и рамка", "Color and border"), appearance, Screen.COLORS, "заливка контур стороны прозрачность тень градиент fill outline opacity shadow gradient"),
        SearchItem("Liquid Glass", appearance, Screen.COLORS, "стекло плотность блики прозрачность glass reflections density material"),
        SearchItem(tr("Шрифт острова", "Island font"), appearance, Screen.FONT, "системный жирный начертание текст font bold typeface text"),
        SearchItem(tr("Раскрытие острова", "Island expansion"), appearance, Screen.ANIM, "анимация пружина упругость скорость animation spring motion speed elasticity"),
        SearchItem(tr("Эффекты", "Effects"), appearance, Screen.EFFECTS, "эквалайзер кадры батарея свечение blur fps frame rate battery glow equalizer"),
        SearchItem(tr("Динамические карточки", "Dynamic cards"), features, Screen.CARDS, "музыка плеер звонок таймер диктофон music player call timer recorder navigation навигация"),
        SearchItem(tr("Уведомления", "Notifications"), features, Screen.NOTIFICATIONS, "сообщения ответ длительность повторы message reply duration repeat WhatsApp Telegram"),
        SearchItem(tr("Тихие уведомления", "Silent notifications"), features, Screen.NOTIFICATIONS, "Snapchat Instagram messages уведомления нет не приходят minimized silent"),
        SearchItem(tr("Приложения", "Apps"), features, Screen.NOTIF_APPS, "уведомления всегда никогда исключения applications notifications always never"),
        SearchItem(tr("Почему не показано", "Why it didn't show"), features, Screen.NOTIF_DIAG, "диагностика журнал сообщения пропуски notification diagnosis missing"),
        SearchItem(tr("События", "Events"), features, Screen.EVENTS, "батарея зарядка wifi bluetooth наушники кейс VPN USB отладка hotspot headphones case battery charging debugging"),
        SearchItem(tr("Показ событий", "Event display"), features, Screen.EVENTS, "длительность задержка duration delay timeout"),
        SearchItem(tr("Поведение", "Behavior"), features, Screen.BEHAVIOR, "скрывать AOD блокировка расписание источник игры альбомная landscape games lockscreen schedule visibility source"),
        SearchItem(tr("Когда нет активностей", "When no activities are active"), features, Screen.BEHAVIOR, "пустая капсула часы заряд idle empty capsule clock battery"),
        SearchItem(tr("Вторая активность рядом", "Second activity alongside"), features, Screen.BEHAVIOR, "сторона пузырь слева справа bubble side left right"),
        SearchItem(tr("Скрывать при записи экрана", "Hide while recording the screen"), features, Screen.BEHAVIOR, "запись экрана screen recording capture hide"),
        SearchItem(tr("Удалить уведомление", "Dismiss notification"), features, Screen.GESTURES, "удаление жест долгое нажатие dismiss delete notification long press"),
        SearchItem(tr("Жесты", "Gestures"), features, Screen.GESTURES, "нажатие свайп двойное вибрация gesture swipe tap double haptics"),
        SearchItem(tr("Текст песен", "Lyrics"), features, Screen.LYRICS, "Spotify синхронизация задержка lyrics synchronization offset"),
        SearchItem(tr("Системные плашки", "System banners"), features, Screen.BANNERS, "дубли уведомлений heads up duplicate notifications"),
        SearchItem(tr("Дополнительная панель", "Extra controls"), features, Screen.EXTRAS, "ярлыки контакты погода шаги часы громкость яркость weather steps clock shortcuts contacts volume brightness"),
        SearchItem(tr("Разрешения", "Permissions"), features, Screen.PERMISSIONS, "доступ специальные возможности запрещено accessibility restricted settings permission"),
        SearchItem(tr("Не выгружать остров", "Keep the island running"), features, Screen.KEEPALIVE, "фон перезапуск остановка батарея background restart crash battery"),
        SearchItem(tr("Банки", "Banks"), features, Screen.BANKS, "банк не открывается bank blocked accessibility"),
        SearchItem(tr("Диагностика оплаты", "Payment diagnostics"), features, Screen.PAYDIAG, "NFC оплата перевод Т-Банк Belkart payment transfer"),
    )
}
