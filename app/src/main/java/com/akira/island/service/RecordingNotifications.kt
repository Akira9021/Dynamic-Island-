package com.akira.island.service

import com.akira.island.core.RecordingKind
import java.util.Locale

internal data class RecordingMatch(
    val kind: RecordingKind? = null,
    val stopOnTap: Boolean = false,
    val paused: Boolean = false,
    val playback: Boolean = false,
) {
    val recording get() = kind != null
    val screen get() = kind == RecordingKind.SCREEN
}

private val screenRecorders = setOf("com.miui.screenrecorder", "com.coloros.screenrecorder", "com.oplus.screenrecorder",
    "com.samsung.android.app.screenrecorder", "com.samsung.android.app.smartcapture", "com.huawei.screenrecorder",
    "com.hihonor.screenrecorder", "com.android.systemui")
private val voiceRecorders = setOf("com.android.soundrecorder", "com.miui.soundrecorder", "com.coloros.soundrecorder",
    "com.oplus.soundrecorder", "com.sec.android.app.voicenote", "com.google.android.apps.recorder", "com.huawei.soundrecorder",
    "com.hihonor.soundrecorder", "com.android.bbksoundrecorder", "com.asus.soundrecorder", "com.transsion.soundrecorder",
    "com.samsung.android.app.voicenote", "com.vivo.soundrecorder", "com.realme.soundrecorder", "com.nothing.soundrecorder")
private val cameras = setOf("com.android.camera", "com.android.camera2", "com.sec.android.app.camera", "com.google.android.GoogleCamera",
    "com.google.android.GoogleCamera.Cameight", "com.oplus.camera", "com.oppo.camera", "com.huawei.camera", "com.hihonor.camera",
    "com.motorola.camera2", "com.motorola.camera3", "org.codeaurora.snapcam", "net.sourceforge.opencamera")
private val recordingWords = listOf("record", "запис", "grava", "grab", "rekam", "aufnahme", "enregistr", "registraz",
    "nagryw", "kayıt", "đang ghi", "录", "錄", "録", "녹음", "녹화", "жазба", "жазып")
private val activeWords = listOf("recording", "идет запись", "запись экрана", "запись звука", "запись видео", "аудиозапись",
    "perekaman", "sedang merekam", "enregistrement en cours", "grabando", "gravando", "正在录", "正在錄", "녹음 중", "녹화 중")
private val screenWords = listOf("screen", "экран", "layar", "tela", "pantalla", "écran", "bildschirm", "屏幕", "画面", "화면")
private val videoWords = listOf("video", "видео", "відео", "vídeo", "видеозапись", "動画", "视频", "影片", "동영상")
private val voiceWords = listOf("voice", "audio", "sound", "диктофон", "звука", "голоса", "аудио", "suara", "voix", "vocal", "เสียง", "녹음")
private val stopWords = listOf("стоп", "останов", "заверш", "stop", "finish", "berhenti", "hentikan", "parar", "detener",
    "тоқтату", "аяқтау", "beenden", "arrêter", "停止", "結束", "終了", "중지")
private val pauseWords = listOf("pause", "пауза", "приостанов", "pausar", "jeda", "暂停", "暫停", "一時停止", "일시")
private val resumeWords = listOf("resume", "продолж", "возобнов", "retomar", "continuar", "lanjut", "reprendre", "继续", "繼續", "再開")
private val saveWords = listOf("save", "сохран", "готово", "done", "salvar", "simpan", "guardar", "enregistrer", "保存", "저장")
private val playbackWords = listOf("playing", "playback", "listening", "воспроизв", "прослуш", "reproduciendo", "reproduzindo", "pemutaran")
private val finishedWords = listOf("recording saved", "saved recording", "recording complete", "recording finished", "recording stopped",
    "saving recording", "processing recording", "запись сохраняется", "сохранение записи", "запись сохран", "запись заверш", "запись останов", "записано", "rekaman disimpan", "enregistrement terminé")
private val transferWords = listOf("download", "upload", "загруз", "скачив", "unduh", "mengunggah", "télécharg")

private fun String.normalized() = lowercase(Locale.ROOT).replace('ё', 'е')
internal fun isStopRecordingAction(title: String) = title.normalized().let { t ->
    // «Приостановить запись» contains «останов», but its action is Pause, never Stop.
    !pauseWords.any { it in t } && !resumeWords.any { it in t } && stopWords.any { it in t }
}
internal fun isPauseRecordingAction(title: String) = title.normalized().let { t -> pauseWords.any { it in t } }
internal fun isResumeRecordingAction(title: String) = title.normalized().let { t -> resumeWords.any { it in t } }
/** Save/Done ends a recording in Recorder, but must not become a destructive Stop action in arbitrary apps. */
internal fun isFinishRecordingAction(pkg: String, title: String) = isStopRecordingAction(title) ||
    pkg in voiceRecorders && title.normalized().let { t -> saveWords.any { it in t } }

internal fun isSpecificRecordingAction(title: String): Boolean = title.normalized().let { t ->
    recordingWords.any { it in t } && (isStopRecordingAction(t) || isPauseRecordingAction(t) || isResumeRecordingAction(t))
}

/** Notification evidence only: a microphone/camera being occupied cannot identify what its app records. */
internal fun classifyRecording(
    pkg: String, title: String, text: String, ongoing: Boolean, hasStopAction: Boolean,
    hasChronometer: Boolean = false, hasElapsedTime: Boolean = false, hasPauseAction: Boolean = false,
    hasResumeAction: Boolean = false, foregroundService: Boolean = false, hasProgress: Boolean = false,
    mediaNotification: Boolean = false, hasRecordingAction: Boolean = false,
): RecordingMatch {
    val combined = "$title $text".normalized()
    if (finishedWords.any { it in combined }) return RecordingMatch()
    val recordWords = recordingWords.any { it in combined }
    val explicitActive = activeWords.any { it in combined } || title.normalized().trim() in
        setOf("запись", "аудиозапись", "enregistrement", "rekaman", "録音", "녹음")
    val tapStop = recordWords && isStopRecordingAction(combined) &&
        listOf("tap", "touch", "нажм", "коснит", "tocar", "ketuk", "appuyez").any { it in combined }
    val knownScreen = pkg in screenRecorders && (pkg != "com.android.systemui" || recordWords)
    val knownVoice = pkg in voiceRecorders
    val knownCamera = pkg in cameras
    val known = knownScreen || knownVoice || knownCamera
    // Recorder can use MediaStyle for its recording service as well as for playing saved files.
    // A media token is not a recording veto, and generic playback Pause/Stop is not recording evidence.
    val playback = (knownVoice || mediaNotification) && playbackWords.any { it in combined } && !hasRecordingAction
    if (playback) return RecordingMatch(playback = true)
    val liveTitle = title.normalized().trim().trimEnd('.', '…').trim() in setOf(
        "recording", "recording paused", "запись", "запись приостановлена", "запись звука", "аудиозапись",
        "screen recording", "запись экрана", "video recording", "recording video", "запись видео", "enregistrement", "rekaman")
    val liveStatus = activeWords.any { it in text.normalized() }
    if (mediaNotification && !tapStop && !hasRecordingAction && !(known && (liveTitle || liveStatus))) {
        return RecordingMatch(playback = true)
    }
    val controls = hasStopAction || hasPauseAction || hasResumeAction
    if (!ongoing && !foregroundService && !(known && controls && (explicitActive || hasRecordingAction))) return RecordingMatch()
    if (!tapStop && (transferWords.any { it in combined } ||
            hasProgress && (!known || !explicitActive && !hasChronometer && !hasPauseAction && !hasResumeAction))) return RecordingMatch()
    val activeControls = controls
    val recording = tapStop || known && (explicitActive || hasChronometer || hasElapsedTime || activeControls) ||
        recordWords && activeControls || explicitActive && (hasChronometer || hasElapsedTime)
    if (!recording) return RecordingMatch()
    val kind = when {
        knownScreen -> RecordingKind.SCREEN
        knownVoice -> RecordingKind.VOICE
        knownCamera -> RecordingKind.VIDEO
        screenWords.any { it in combined } -> RecordingKind.SCREEN
        videoWords.any { it in combined } -> RecordingKind.VIDEO
        voiceWords.any { it in combined } -> RecordingKind.VOICE
        else -> RecordingKind.GENERIC
    }
    return RecordingMatch(kind, tapStop, paused = hasResumeAction && !hasPauseAction ||
        listOf("paused", "пауза", "приостановлена", "приостановлено", "en pause").any { it in combined })
}
