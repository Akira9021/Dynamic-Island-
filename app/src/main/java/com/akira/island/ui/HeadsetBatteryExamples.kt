package com.akira.island.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.service.HeadsetBatteryTracker

@Composable
fun HeadsetBatteryExamples(onExample: (Notice) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(tr("Наушники · пример", "Headphones · preview"),Modifier.weight(1f),color=Ui.Sub,fontSize=13.sp)
        fun show(level:Int?) = onExample(Notice(tr("Наушники", "Headphones"),icon=Icons.Rounded.Headphones,
            tint=Tints.Green,ring=HeadsetBatteryTracker.ring(level),caption=tr("Подключено", "Connected")))
        TextButton(onClick={show(75)}) { Text("75%") }
        TextButton(onClick={show(null)}) { Text(tr("Нет данных", "Unknown")) }
    }
}
