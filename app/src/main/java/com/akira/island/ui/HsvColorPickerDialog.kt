package com.akira.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akira.island.core.tr
import java.util.Locale

/** A local draft: opening, dragging and cancelling never write SettingsRepo. */
@Composable
internal fun HsvColorPickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var selection by remember(initial) { mutableStateOf(HsvColorSelection.fromArgb(initial)) }
    val color = Color(selection.toArgb())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Свой цвет", "Custom color")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("hsv-color-picker")
            .verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(44.dp).background(color, RoundedCornerShape(12.dp)))
            Text(String.format(Locale.ROOT, "#%06X", selection.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.labelLarge)
            Text(tr("Оттенок", "Hue"))
            Box(Modifier.background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), RoundedCornerShape(16.dp))) {
                Slider(selection.hue, { selection = selection.copy(hue = it) }, valueRange = 0f..360f,
                    modifier = Modifier.testTag("hsv-hue").semantics { contentDescription = tr("Оттенок", "Hue") }, colors = SliderDefaults.colors(activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent, thumbColor = Color.White))
            }
            Text(tr("Насыщенность", "Saturation"))
            Slider(selection.saturation, { selection = selection.copy(saturation = it) },
                modifier = Modifier.testTag("hsv-saturation").semantics { contentDescription = tr("Насыщенность", "Saturation") })
            Text(tr("Яркость", "Brightness"))
            Slider(selection.value, { selection = selection.copy(value = it) }, modifier = Modifier.testTag("hsv-value")
                .semantics { contentDescription = tr("Яркость", "Brightness") })
        }
    }, confirmButton = {
        TextButton(onClick = { onConfirm(selection.toArgb()) }, modifier = Modifier.testTag("hsv-apply")) {
            Text(tr("Применить", "Apply"))
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("hsv-cancel")) { Text(tr("Отмена", "Cancel")) }
    })
}
