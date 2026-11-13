package com.akira.island.island

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.*
import com.akira.island.service.NetworkRates
import java.util.Locale

internal fun transferRateText(bytes: Double): String {
    if (!bytes.isFinite() || bytes < 0) return "—"
    return if (bytes >= 1_000_000) String.format(Locale.ROOT, "%.1f", bytes / 1_000_000) + " " + tr("МБ/с", "MB/s")
        else String.format(Locale.ROOT, "%.1f", bytes / 1000) + " " + tr("КБ/с", "KB/s")
}

@Composable
internal fun TransferRateDetails(details: TransferDetails?, showNetwork: Boolean) {
    val general = if (showNetwork && details?.bytesPerSecond == null) NetworkRates.state.collectAsState().value else null
    TransferRateLines(details, general, waitingForNetwork = showNetwork && details?.bytesPerSecond == null)
}

@Composable
internal fun TransferRateLines(details: TransferDetails?, general: NetworkRate?, waitingForNetwork: Boolean = false) {
    if (details == null && general == null && !waitingForNetwork) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        details?.bytesPerSecond?.let {
            val arrow = when (details.direction) { TransferDirection.DOWN -> "↓ "; TransferDirection.UP -> "↑ "; null -> "" }
            Text(arrow + transferRateText(it), color = Color.White, fontSize = 13.sp, modifier = Modifier.testTag("operation-rate"))
            Text(tr("Из уведомления приложения", "From the app's notification"), color = Color.Gray, fontSize = 11.sp)
        }
        details?.etaSeconds?.let { seconds ->
            val remaining = when {
                seconds >= 3600 -> trf("{0} ч {1} мин", "{0} h {1} min", seconds / 3600, seconds % 3600 / 60)
                seconds >= 60 -> trf("{0} мин {1} с", "{0} min {1} s", seconds / 60, seconds % 60)
                else -> trf("{0} с", "{0} s", seconds)
            }
            Text(trf("Осталось: {0}", "Remaining: {0}", remaining), color = Color.Gray, fontSize = 12.sp,
                modifier = Modifier.testTag("operation-eta"))
        }
        if (general != null || waitingForNetwork) {
            Text(tr("Примерная общая скорость сети", "Approximate total network speed"), color = Color.Gray, fontSize = 11.sp)
            // Wrap directions independently on narrow cards and with large text.
            Text("↓ " + (general?.incoming?.let(::transferRateText) ?: "—"), color = Color.White, fontSize = 13.sp, modifier = Modifier.testTag("network-incoming"))
            Text("↑ " + (general?.outgoing?.let(::transferRateText) ?: "—"), color = Color.White, fontSize = 13.sp, modifier = Modifier.testTag("network-outgoing"))
        }
    }
}

/** Local example, with no collector, service start or writes to real activity state. */
@Composable
internal fun NetworkRateExample() {
    Column(Modifier.fillMaxWidth().background(Color(0xFF111113), RoundedCornerShape(20.dp)).padding(16.dp)
        .testTag("network-rate-example")) {
        Text(tr("Пример — не текущая скорость", "Example — not the current speed"), color = Color.Gray, fontSize = 12.sp)
        TransferRateLines(null, NetworkRate(1_200_000.0, 80_000.0))
    }
}
