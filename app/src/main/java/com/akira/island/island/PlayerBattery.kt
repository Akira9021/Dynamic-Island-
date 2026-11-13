package com.akira.island.island

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.BatteryInfo
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf

@Composable
internal fun PlayerBattery(battery: BatteryInfo, percentOnly: Boolean) {
    if (battery.level !in 0..100) return
    val description = trf("Заряд батареи: {0}%", "Battery: {0}%", battery.level) +
        if (battery.charging) ", " + tr("Заряжается", "Charging") else ""
    Row(Modifier.testTag("player-battery").semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!percentOnly) BatteryGlyph(battery.level, when {
            battery.charging -> Tints.Green
            battery.level <= 15 -> Tints.Red
            else -> Color.White
        }, Modifier.size(21.dp, 11.dp))
        Text("${battery.level}%", color = Color.White, fontSize = 12.sp, maxLines = 1, style = Digits)
    }
}
