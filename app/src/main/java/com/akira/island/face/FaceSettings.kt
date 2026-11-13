package com.akira.island.face

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.*
import com.akira.island.ui.screens.SettingsHint
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

/** Permission requests are explicit user actions, never a side-effect of opening settings. */
@Composable
fun FaceSettings(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val context=LocalContext.current
    val runtime by FaceUnlockState.availability.collectAsState()
    val service by IslandBus.serviceRunning.collectAsState()
    val revision by FaceUnlockState.revision.collectAsState()
    val settings by SettingsRepo.state.collectAsState()
    val powerSave by IslandBus.powerSave.collectAsState()
    var requestFailed by remember { mutableStateOf(false) }
    var example by remember { mutableIntStateOf(0) }
    var demo by remember { mutableStateOf(FacePhase.HIDDEN) }
    DisposableEffect(Unit) {
        val received=Shizuku.OnBinderReceivedListener { FaceUnlockState.refresh() }
        val dead=Shizuku.OnBinderDeadListener { FaceUnlockState.refresh() }
        val result=Shizuku.OnRequestPermissionResultListener { _, _ -> FaceUnlockState.refresh() }
        Shizuku.addBinderReceivedListenerSticky(received); Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(result)
        onDispose { Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(result) }
    }
    val access=remember(revision,runtime,service,enabled) { if(enabled) FaceUnlockState.access() else FaceAvailability.OFF }
    val status=when {
        !enabled -> tr("Выключено", "Off")
        !service -> tr("Включите остров на Главной", "Turn on the island on Home")
        access == FaceAvailability.SHIZUKU_STOPPED -> tr("Установите и запустите Shizuku", "Install and start Shizuku")
        access == FaceAvailability.PERMISSION_NEEDED -> tr("Нужно разрешение Shizuku", "Shizuku permission required")
        access == FaceAvailability.BACKEND_UNSUPPORTED -> tr("Нужен Shizuku 13+ в режиме ADB, без root", "Requires Shizuku 13+ with the ADB backend")
        runtime == FaceAvailability.PAUSED -> tr("Приостановлено: экран выключен", "Paused while the screen is off")
        runtime == FaceAvailability.EVENTS_SEEN -> tr("События лица обнаружены. Проверьте вход лицом, PIN и отпечатком", "Face events detected. Test face, PIN and fingerprint unlock")
        runtime == FaceAvailability.SOURCE_UNAVAILABLE -> tr("Источник событий недоступен на этой прошивке или отключён", "Event source unavailable on this firmware or disconnected")
        else -> tr("Ожидаем системные события лица; совместимость ещё не подтверждена", "Waiting for face events; compatibility is not yet confirmed")
    }
    SectionHeader(tr("Распознавание лица", "Face recognition"))
    GroupToggle(Pos.Single,Icons.Rounded.Face,tr("Face ID · только лицо", "Face ID · face only"),
        tr("Анимация при подтверждённой разблокировке лицом. Требуется Shizuku", "Animation for verified face unlock. Requires Shizuku"), enabled,color=Tile.Indigo) { onEnabled(it) }
    SettingsHint(status)
    if (enabled) {
        SettingsHint(tr("Shizuku подключается отдельно и обычно требует запуска после перезагрузки. При неизвестном способе входа эффект не показывается. Общая плашка «Разблокировка устройства» при этом режиме не используется.",
            "Set up Shizuku separately; it usually needs restarting after a reboot. Unknown unlock methods show no effect. The general Device unlock banner is suppressed in this mode."))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(onClick={
                requestFailed=false
                if (access == FaceAvailability.PERMISSION_NEEDED) {
                    runCatching { Shizuku.requestPermission(7461) }.onFailure { requestFailed=true }
                } else FaceUnlockState.refresh()
            }) { Text(if(access == FaceAvailability.PERMISSION_NEEDED) tr("Дать доступ", "Allow access") else tr("Проверить", "Check")) }
            TextButton(onClick={ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/guide/setup/"))) } }) {
                Text(tr("Как подключить", "Setup guide"))
            }
        }
        if(requestFailed) SettingsHint(tr("Не удалось запросить доступ. Проверьте, что Shizuku запущен.", "Could not request access. Check that Shizuku is running."))
    }
    TextButton(onClick={example++},modifier=Modifier.testTag("face-preview")) { Text(tr("Пример анимации", "Preview animation")) }
    LaunchedEffect(example) {
        if(example==0) return@LaunchedEffect
        demo=FacePhase.SCANNING; delay(1_400); demo=FacePhase.SUCCESS; delay(1_200); demo=FacePhase.HIDDEN
    }
    fun closeExample() { example=0; demo=FacePhase.HIDDEN }
    if(demo!=FacePhase.HIDDEN) AlertDialog(
        onDismissRequest=::closeExample,
        title={ Text(tr("Пример анимации", "Preview animation")) },
        text={ Row(Modifier.fillMaxWidth().testTag("face-demo"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FaceUnlockCard(demo,settings.forPowerSave(powerSave).reduceEffects)
            Text(tr("Демонстрация, не реальная проверка лица", "Preview, not an actual face scan"),color=Ui.Sub,modifier=Modifier.weight(1f))
        } },
        confirmButton={ TextButton(onClick=::closeExample) { Text(tr("Закрыть", "Close")) } },
    )
}
