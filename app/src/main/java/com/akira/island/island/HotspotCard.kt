package com.akira.island.island

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.service.HotspotStatus

@Composable
internal fun HotspotDetails(status: HotspotStatus) {
    val context = LocalContext.current
    val locked by IslandBus.locked.collectAsState()
    Column(Modifier.semantics { contentDescription = "hotspot-card" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WifiTethering, null, tint = Tints.Blue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("Точка доступа", "Hotspot"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold.island)
        }
        Text(status.clients?.let { trf("Подключено устройств: {0}", "Connected devices: {0}", it) }
            ?: tr("Число подключённых устройств недоступно", "Connected device count is unavailable"),
            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        if (!locked) PillButton(Icons.Rounded.Settings, tr("Настройки точки доступа", "Hotspot settings")) {
            if (openHotspotSettings(context)) Controls.collapse()
            else Toast.makeText(context, tr("Не удалось открыть настройки сети", "Couldn't open network settings"), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openHotspotSettings(context: Context): Boolean {
    // AOSP's optional settings route; fall back to the public wireless settings action on OEMs.
    for (action in listOf("android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val system = context.packageManager.resolveActivity(intent, PackageManager.MATCH_SYSTEM_ONLY) ?: continue
        intent.setClassName(system.activityInfo.packageName, system.activityInfo.name)
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return false
}
