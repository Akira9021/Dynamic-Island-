package com.akira.island.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.akira.island.core.system.AlarmCreationDispatch
import com.akira.island.core.system.openAlarmCreation
import com.akira.island.core.tr

/** Dedicated input window; never focus or repurpose the running island/notification windows. */
internal class SystemAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IslandTheme {
                var failure by remember { mutableStateOf<String?>(null) }
                SystemAlarmDialog(onDismiss = { finish() }, error = failure, onConfirm = { request ->
                    when (openAlarmCreation(this, request, tr("Выберите «Часы»", "Choose Clock"))) {
                        AlarmCreationDispatch.OPENED_CLOCK -> finish()
                        AlarmCreationDispatch.UNAVAILABLE -> failure = tr("Приложение «Часы» не найдено.", "No Clock app is available.")
                        AlarmCreationDispatch.FAILED -> failure = tr("Не удалось открыть «Часы». Попробуйте ещё раз.", "Could not open Clock. Please try again.")
                    }
                })
            }
        }
    }
}
