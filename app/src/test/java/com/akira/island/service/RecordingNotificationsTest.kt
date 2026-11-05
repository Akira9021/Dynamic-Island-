package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class RecordingNotificationsTest {
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
