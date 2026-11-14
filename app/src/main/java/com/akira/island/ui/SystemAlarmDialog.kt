@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.akira.island.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akira.island.core.system.AlarmCreationRequest
import com.akira.island.core.system.AlarmWeekday
import com.akira.island.core.tr

/** Local draft; only Clock can confirm that a requested alarm was actually created. */
@Composable
internal fun SystemAlarmDialog(onDismiss: () -> Unit, onConfirm: (AlarmCreationRequest) -> Unit,
    error: String? = null, initialHour: Int = 7, initialMinute: Int = 0) {
    val time = rememberTimePickerState(initialHour, initialMinute, true)
    var dayMask by rememberSaveable { mutableIntStateOf(0) }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Новый будильник", "New alarm")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("system-alarm-draft")
            .verticalScroll(rememberScrollState())) {
            TimeInput(time)
            Text(if (dayMask == 0) tr("Один раз", "Once") else tr("Повторять по дням", "Repeat on selected days"))
            val short = listOf(tr("Пн", "Mo"), tr("Вт", "Tu"), tr("Ср", "We"), tr("Чт", "Th"), tr("Пт", "Fr"), tr("Сб", "Sa"), tr("Вс", "Su"))
            val full = listOf(tr("Понедельник", "Monday"), tr("Вторник", "Tuesday"), tr("Среда", "Wednesday"),
                tr("Четверг", "Thursday"), tr("Пятница", "Friday"), tr("Суббота", "Saturday"), tr("Воскресенье", "Sunday"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AlarmWeekday.entries.forEachIndexed { index, _ ->
                    FilterChip(dayMask and (1 shl index) != 0, { dayMask = dayMask xor (1 shl index) },
                        label = { Text(short[index]) }, modifier = Modifier.testTag("alarm-day-$index")
                            .semantics { contentDescription = full[index] })
                }
            }
            OutlinedTextField(label, { label = it.take(80) }, singleLine = true,
                label = { Text(tr("Подпись", "Label")) }, modifier = Modifier.fillMaxWidth().testTag("alarm-label"))
            Text(tr("Откроются системные «Часы», где можно подтвердить будильник.",
                "Clock will open so you can confirm the alarm."), style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("alarm-error")) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            onConfirm(AlarmCreationRequest(time.hour, time.minute,
                AlarmWeekday.entries.filterIndexed { index, _ -> dayMask and (1 shl index) != 0 }.toSet(), label))
        }, modifier = Modifier.testTag("alarm-open-clock")) { Text(tr("Продолжить", "Continue")) }
    }, dismissButton = {
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("alarm-cancel")) { Text(tr("Отмена", "Cancel")) }
    })
}
