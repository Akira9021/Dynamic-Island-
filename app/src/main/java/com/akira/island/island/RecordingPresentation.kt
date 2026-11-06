package com.akira.island.island

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.akira.island.core.RecordingKind

/** Audio alone gets an audio waveform; screen and camera capture have their own recognizable symbols. */
internal val RecordingKind.icon: ImageVector get() = when (this) {
    RecordingKind.VOICE -> Icons.Rounded.Mic
    RecordingKind.SCREEN -> Icons.Rounded.ScreenShare
    RecordingKind.VIDEO -> Icons.Rounded.Videocam
    RecordingKind.GENERIC -> Icons.Rounded.FiberManualRecord
}
