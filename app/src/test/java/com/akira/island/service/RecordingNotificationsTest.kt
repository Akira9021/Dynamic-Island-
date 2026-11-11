package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test
import com.akira.island.core.RecordingKind

class RecordingNotificationsTest {
    @Test fun russianPauseIsNeverAStopAction() {
        assertTrue(isPauseRecordingAction("Приостановить запись"))
        assertFalse(isStopRecordingAction("Приостановить запись"))
        assertFalse(isFinishRecordingAction("com.google.android.apps.recorder", "Приостановить запись"))
        assertTrue(isStopRecordingAction("Остановить запись"))
    }
    @Test fun pixelRecorderMediaTokenDoesNotHideConfirmedLiveRecording() {
        assertEquals(RecordingKind.VOICE, classifyRecording("com.google.android.apps.recorder", "Recording", "00:12",
            false, false, hasElapsedTime = true, hasPauseAction = true, foregroundService = true, mediaNotification = true).kind)
        assertEquals(RecordingKind.VOICE, classifyRecording("com.google.android.apps.recorder", "Запись", "",
            false, false, hasPauseAction = true, mediaNotification = true).kind)
    }
    @Test fun savedRecorderPlaybackAndMusicAreNeverLiveCapture() {
        assertTrue(classifyRecording("com.google.android.apps.recorder", "Interview", "00:12",
            true, true, hasElapsedTime = true, hasPauseAction = true, mediaNotification = true).playback)
        assertFalse(classifyRecording("com.google.android.apps.recorder", "Recording 12", "00:12",
            true, true, hasPauseAction = true, foregroundService = true, mediaNotification = true).recording)
        assertFalse(classifyRecording("com.spotify.music", "Recording of the day", "00:12",
            true, true, hasPauseAction = true, mediaNotification = true).recording)
        assertFalse(classifyRecording("com.google.android.apps.recorder", "Playing recording", "00:12",
            true, true, hasPauseAction = true, foregroundService = true, mediaNotification = true).recording)
    }
    @Test fun liveRecorderSpecificActionsWorkWithoutPersistentNotificationFlags() {
        assertEquals(RecordingKind.VOICE, classifyRecording("com.google.android.apps.recorder", "Recorder", "",
            false, false, hasPauseAction = true, hasRecordingAction = true, mediaNotification = true).kind)
        assertFalse(classifyRecording("com.google.android.apps.recorder", "Recorder", "",
            false, false, hasPauseAction = true).recording)
    }
    @Test fun recorderSaveIsAnExplicitFinishActionOnlyForTrustedRecorders() {
        assertTrue(isFinishRecordingAction("com.google.android.apps.recorder", "Сохранить"))
        assertTrue(isFinishRecordingAction("com.google.android.apps.recorder", "Save recording"))
        assertFalse(isFinishRecordingAction("example.files", "Save recording"))
        assertFalse(isFinishRecordingAction("com.google.android.apps.recorder", "Delete"))
        assertTrue(isSpecificRecordingAction("Pause recording"))
        assertFalse(isSpecificRecordingAction("Pause"))
    }
    @Test fun oemRecorderWithJustTimeAndPauseIsRecognized() {
        assertEquals(RecordingKind.VOICE, classifyRecording("com.sec.android.app.voicenote", "00:12", "", true, false,
            hasElapsedTime = true, hasPauseAction = true).kind)
        assertEquals(RecordingKind.VOICE, classifyRecording("com.miui.soundrecorder", "Диктофон", "00:12", false, false,
            hasElapsedTime = true, foregroundService = true).kind)
    }
    @Test fun cameraAndScreenRecordingsHaveDifferentKinds() {
        assertEquals(RecordingKind.VIDEO, classifyRecording("com.sec.android.app.camera", "Recording video", "00:12", true, true).kind)
        assertEquals(RecordingKind.SCREEN, classifyRecording("com.miui.screenrecorder", "Recording video", "00:12", true, true).kind)
        assertEquals(RecordingKind.VIDEO, classifyRecording("example.camera", "Запись видео", "", true, true).kind)
    }
    @Test fun recorderPauseIsNotTreatedAsFinished() {
        val result = classifyRecording("com.google.android.apps.recorder", "Recording paused", "00:12", true, true, hasResumeAction = true)
        assertEquals(RecordingKind.VOICE, result.kind); assertTrue(result.paused)
    }
    @Test fun finishedRecordingsAndSavingWorkAreNotLiveRecording() {
        assertFalse(classifyRecording("com.miui.soundrecorder", "Recording saved", "00:12", true, true, hasElapsedTime = true).recording)
        assertFalse(classifyRecording("com.miui.screenrecorder", "Saving recording", "00:12", true, true, hasElapsedTime = true).recording)
        assertFalse(classifyRecording("com.android.camera", "Processing video", "", true, true, hasProgress = true).recording)
    }
    @Test fun systemUiTimersAndFileTransfersAreNotScreenRecordings() {
        assertFalse(classifyRecording("com.android.systemui", "Timer", "00:12", true, true, hasElapsedTime = true).recording)
        assertFalse(classifyRecording("com.miui.soundrecorder", "Uploading recording", "00:12", true, true, hasProgress = true).recording)
        assertFalse(classifyRecording("example.download", "Downloading video recording", "", true, true, hasProgress = true).recording)
    }
    @Test fun unknownRecorderUsesANeutralSymbolRatherThanGuessingAudio() {
        assertEquals(RecordingKind.GENERIC, classifyRecording("example.recorder", "Recording", "", true, true).kind)
        assertEquals(RecordingKind.VOICE, classifyRecording("example.recorder", "Audio recording", "00:12", true, false,
            hasElapsedTime = true).kind)
    }
    @Test fun tapToStopSystemRecorderNeedsNoActionButtons() {
        val match = classifyRecording("com.android.systemui", "Tap here to stop recording.", "00:01", true, false)
        assertTrue(match.recording); assertTrue(match.screen); assertTrue(match.stopOnTap)
    }
    @Test fun russianScreenRecordingIsDetected() {
        val match = classifyRecording("com.miui.screenrecorder", "Запись экрана", "Нажмите, чтобы остановить запись", true, false)
        assertTrue(match.screen); assertTrue(match.stopOnTap)
    }
    @Test fun aVoiceRecorderDoesNotHideTheScreen() {
        val match = classifyRecording("com.google.android.apps.recorder", "Recording", "00:12", true, true)
        assertTrue(match.recording); assertFalse(match.screen); assertFalse(match.stopOnTap)
    }
    @Test fun dismissedOrOrdinaryNotificationsCannotTriggerRecordingMode() {
        assertFalse(classifyRecording("com.android.systemui", "Recording saved", "Tap here to stop recording", false, false).recording)
        assertFalse(classifyRecording("com.example.player", "Record of the day", "", true, false).recording)
        assertFalse(classifyRecording("com.google.android.apps.maps", "Turn right", "Arrive 00:01", true, true).recording)
    }
    @Test fun knownRecorderWithLocalizedStopActionWorksWithoutEnglishText() {
        assertTrue(classifyRecording("com.oplus.screenrecorder", "正在录制", "", true, true).screen)
        assertTrue(isStopRecordingAction("Arrêter"))
    }
}
