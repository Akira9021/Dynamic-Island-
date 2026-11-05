package com.akira.island.island

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import com.akira.island.core.*
import com.akira.island.data.IslandSettings
import com.akira.island.data.MessageStyle
import androidx.compose.runtime.rememberUpdatedState

internal val LocalIslandPreview = staticCompositionLocalOf { false }
enum class PreviewScene { IDLE, MUSIC, MESSAGE, TIMER }

/** The actual island renderer and animations, with isolated sample data and disabled phone actions. */
@Composable
fun AppearancePreview(s: IslandSettings, card: Boolean, scene: PreviewScene, event: Notice? = null, notificationStyle: Boolean = false, twoActivities: Boolean = false,
    light: GlassTilt = GlassTilt.Zero, onLight: ((GlassTilt) -> Unit)? = null) {
    val sampleMedia = remember { MediaInfo("preview", "", "Night Drive", "Alex · Demo", null, null, Tints.Lavender,
        180_000, 72_000, SystemClock.elapsedRealtime(), 1f, true, true) }
    val media = sampleMedia.copy(playing = !s.reduceEffects)
    val timer = remember { TimerState(300_000, SystemClock.elapsedRealtime() + 192_000, pausedLeft = 192_000) }
    val message = remember { AppMessage("preview", "", "Messages", tr("Привет! Во сколько встречаемся?", "Hi! What time are we meeting?"), null, null, null) }
    val icon = Ios.chatbubble
    val notice = remember(message, icon) { Notice(tr("Анна", "Anna"), icon = icon, tint = Tints.Blue, style = NoticeStyle.MESSAGE, message = message) }
    val act = when {
        event != null -> Act.NoticeA(event)
        scene == PreviewScene.MESSAGE -> Act.NoticeA(notice)
        scene == PreviewScene.TIMER -> Act.TimerA(timer)
        scene == PreviewScene.IDLE -> Act.Idle
        else -> Act.MediaA(media)
    }
    val settings = if (scene == PreviewScene.MESSAGE && !notificationStyle)
        s.copy(messageStyle = if (card) MessageStyle.CARD else MessageStyle.PILL) else s
    val previewLabel = tr("Живой пример — действия телефона отключены", "Live preview — phone actions are disabled")
    val lightCallback by rememberUpdatedState(onLight)
    Box(Modifier.clearAndSetSemantics {
        contentDescription = previewLabel
        testTag = "appearance-preview"
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val finger = event.changes.firstOrNull { it.pressed }
                    lightCallback?.invoke(if (finger == null) GlassTilt.Zero else GlassTilt(
                        (finger.position.x / size.width.coerceAtLeast(1) * 2f - 1f).coerceIn(-1f, 1f),
                        (finger.position.y / size.height.coerceAtLeast(1) * 2f - 1f).coerceIn(-1f, 1f),
                    ))
                    event.changes.forEach { it.consume() }
                }
            } finally { lightCallback?.invoke(GlassTilt.Zero) }
        }
    }) {
        CompositionLocalProvider(LocalIslandPreview provides true, LocalGlassTilt provides light) {
            IslandTheme {
                val pair = twoActivities && !card && (scene == PreviewScene.MUSIC || scene == PreviewScene.TIMER)
                val slot = animateDpAsState(if (pair && settings.bubble) activeHeight(settings) + 6.dp else 0.dp, islandSpec(settings), label = "previewBubble")
                if (pair) {
                    IslandRow(slot, settings, if (settings.preferMusic) Act.MediaA(media) else Act.TimerA(timer), false, null,
                        BatteryInfo(72), settings.bubble, if (settings.preferMusic) Act.TimerA(timer) else Act.MediaA(media), false, {}, {})
                } else IslandBody(act, card && event == null && scene != PreviewScene.MESSAGE, settings, null, BatteryInfo(72, false), false, {})
            }
        }
    }
}
