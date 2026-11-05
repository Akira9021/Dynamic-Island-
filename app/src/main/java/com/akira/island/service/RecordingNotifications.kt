package com.akira.island.service

internal data class RecordingMatch(val recording: Boolean = false, val screen: Boolean = false, val stopOnTap: Boolean = false)

private val screenRecorders = setOf("com.miui.screenrecorder", "com.coloros.screenrecorder", "com.oplus.screenrecorder",
    "com.samsung.android.app.screenrecorder", "com.samsung.android.app.smartcapture", "com.huawei.screenrecorder",
    "com.hihonor.screenrecorder", "com.android.systemui")
private val voiceRecorders = setOf("com.android.soundrecorder", "com.miui.soundrecorder", "com.coloros.soundrecorder",
    "com.oplus.soundrecorder", "com.sec.android.app.voicenote", "com.google.android.apps.recorder", "com.huawei.soundrecorder",
    "com.hihonor.soundrecorder", "com.android.bbksoundrecorder", "com.asus.soundrecorder", "com.transsion.soundrecorder")
private val recordingWords = listOf("record", "запис", "grava", "grab", "rekam", "aufnahme", "enregistr", "registraz",
    "nagryw", "kayıt", "đang ghi", "录", "錄", "録", "녹음", "녹화", "жазба", "жазып")
private val screenWords = listOf("screen", "экран", "layar", "tela", "pantalla", "écran", "bildschirm", "屏幕", "画面", "화면")
private val stopWords = listOf("стоп", "останов", "заверш", "stop", "finish", "berhenti", "hentikan", "parar", "detener",
    "тоқтату", "аяқтау", "beenden", "arrêter", "停止", "結束", "終了", "중지")

internal fun isStopRecordingAction(title: String) = stopWords.any { it in title.lowercase() }

/** A recorder can deliberately use category=navigation. Its explicit recording evidence wins. */
internal fun classifyRecording(pkg: String, title: String, text: String, ongoing: Boolean, hasStopAction: Boolean): RecordingMatch {
    if (!ongoing) return RecordingMatch()
    val combined = "$title $text".lowercase()
    val recordWords = recordingWords.any { it in combined }
    val tapStop = recordWords && isStopRecordingAction(combined) &&
        listOf("tap", "touch", "нажм", "коснит", "tocar", "ketuk", "appuyez").any { it in combined }
    val known = pkg in screenRecorders || pkg in voiceRecorders
    val recording = tapStop || hasStopAction && (known || recordWords) || known &&
        listOf("recording", "идёт запись", "идет запись", "запись экрана", "perekaman", "enregistrement en cours").any { it in combined }
    return RecordingMatch(recording, recording && (pkg in screenRecorders || screenWords.any { it in combined }), tapStop)
}
