package com.akira.island.ui.screens

import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akira.island.core.tr
import com.akira.island.data.ContactShortcut
import com.akira.island.data.ExtrasRepo
import com.akira.island.service.WeatherCity
import com.akira.island.service.WeatherRepo
import com.akira.island.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtrasScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by ExtrasRepo.state.collectAsState()
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ResolveInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<WeatherCity>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ExtrasRepo.update { copy(steps = granted) }
    }
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null && uri.scheme == "content" && uri.authority == "com.android.contacts") {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf("display_name"), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: tr("Контакт", "Contact")
            ExtrasRepo.update { copy(contacts = (contacts.filter { it.uri != uri.toString() } + ContactShortcut(name, uri.toString())).takeLast(6)) }
        }
    }
    Page(tr("Дополнительная панель", "Extra controls"), onBack) {
        GroupToggle(Pos.Single, Icons.Rounded.Tune, tr("Элементы управления", "Controls"),
            tr("Громкость, яркость и журнал звонков в раскрытом пустом острове", "Volume, brightness and call history in the expanded idle island"), s.controls, color = Tile.Indigo) {
            ExtrasRepo.update { copy(controls = it) }
        }
        GroupToggle(Pos.Single, Icons.Rounded.Schedule, tr("Аналоговые часы", "Analog clock"),
            tr("Циферблат в раскрытом пустом острове", "Clock face in the expanded idle island"), s.analogClock, color = Tile.Purple) { ExtrasRepo.update { copy(analogClock = it) } }
        GroupToggle(Pos.Single, Icons.Rounded.DirectionsWalk, tr("Шагомер", "Step counter"),
            tr("Шаги за день с момента включения; нужен датчик шагов", "Daily steps since enabling; requires a step sensor"), s.steps, color = Tile.Green) { enabled ->
            val sensor = context.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
            if (enabled && sensor == null) error = tr("На устройстве нет датчика шагов", "This device has no step sensor")
            else if (enabled && android.os.Build.VERSION.SDK_INT >= 29) stepPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            else ExtrasRepo.update { copy(steps = enabled) }
        }
        SectionHeader(tr("Быстрый доступ", "Shortcuts"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Apps, tr("Добавить приложение", "Add app"), tr("До шести приложений", "Up to six apps"), color = Tile.Purple, onClick = {
                scope.launch {
                    apps = withContext(Dispatchers.IO) {
                        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(context.packageManager).toString() }
                    }
                }
            })
            s.apps.forEach { pkg ->
                val label = remember(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                GroupItem(Pos.Middle, Icons.Rounded.Close, label, tr("Нажмите, чтобы убрать", "Tap to remove"), onClick = { ExtrasRepo.update { copy(apps = this.apps - pkg) } })
            }
            GroupItem(Pos.Middle, Icons.Rounded.PersonAdd, tr("Добавить контакт", "Add contact"), tr("Открывает карточку контакта; до шести", "Opens the contact card; up to six"), color = Tile.Green, onClick = { runCatching { contact.launch(null) }.onFailure { error = tr("Не удалось открыть контакты", "Could not open contacts") } })
            s.contacts.forEach { c ->
                GroupItem(Pos.Middle, Icons.Rounded.Close, c.name, tr("Нажмите, чтобы убрать", "Tap to remove"), onClick = { ExtrasRepo.update { copy(contacts = contacts - c) } })
            }
            GroupItem(Pos.Last, Icons.Rounded.Info, tr("Ваши ярлыки", "Your shortcuts"),
                tr("Приложения и контакты доступны после раскрытия пустого острова. Контакты скрыты на экране блокировки.", "Apps and contacts appear in the expanded idle island. Contacts are hidden on the lock screen."))
        }
        SectionHeader(tr("Погода", "Weather"))
        GroupToggle(Pos.Single, Icons.Rounded.Cloud, tr("Карточка погоды", "Weather card"),
            s.weatherCity.ifBlank { tr("Сначала выберите город", "Choose a city first") }, s.weatherEnabled, color = Tile.Teal) {
            ExtrasRepo.update { copy(weatherEnabled = it && weatherCity.isNotBlank()) }
        }
        OutlinedTextField(query, { query = it.take(100) }, label = { Text(tr("Город", "City")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        TextButton(enabled = !busy && query.trim().length >= 2, onClick = {
            scope.launch {
                busy = true; error = null
                try {
                    cities = WeatherRepo.cities(query)
                    if (cities.isEmpty()) error = tr("Город не найден", "City not found")
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = tr("Не удалось найти город. Проверьте подключение.", "City search failed. Check your connection.") }
                finally { busy = false }
            }
        }) { Text(if (busy) tr("Поиск…", "Searching…") else tr("Найти город", "Find city")) }
        error?.let { Text(it, color = Ui.Sub) }
        cities.forEach { city ->
            TextButton(onClick = {
                ExtrasRepo.update { copy(weatherCity = city.name, latitude = city.latitude, longitude = city.longitude, weatherEnabled = true) }
                cities = emptyList()
            }) { Text(city.name) }
        }
        Text(tr("Прогноз Open-Meteo (CC BY 4.0). Выбранный город и координаты отправляются Open-Meteo. Обновление раз в 15 минут при включённом экране; геолокация не запрашивается.",
            "Forecast by Open-Meteo (CC BY 4.0). Your selected city and coordinates are sent to Open-Meteo. Refreshes every 15 minutes while the screen is on; no location permission is requested."), color = Ui.Sub, modifier = Modifier.padding(vertical = 12.dp))
    }
    apps?.let { available ->
        AlertDialog(onDismissRequest = { apps = null }, title = { Text(tr("Выберите приложение", "Choose app")) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                available.forEach { app ->
                    Text(app.loadLabel(context.packageManager).toString(), modifier = Modifier.fillMaxWidth().clickable {
                        ExtrasRepo.update { copy(apps = (this.apps + app.activityInfo.packageName).distinct().takeLast(6)) }
                        apps = null
                    }.padding(12.dp))
                }
            }
        }, confirmButton = { TextButton(onClick = { apps = null }) { Text(tr("Закрыть", "Close")) } })
    }
}
