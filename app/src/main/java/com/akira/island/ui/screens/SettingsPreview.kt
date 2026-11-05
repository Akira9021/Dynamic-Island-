package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.Notice
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.AppearancePreview
import com.akira.island.island.IslandType
import com.akira.island.island.PreviewScene
import com.akira.island.ui.Ui

/** Pinned local examples do not post events or change any live playback/notification state. */
@Composable
fun SettingsPreview(
    card: Boolean = false,
    scene: PreviewScene = PreviewScene.MUSIC,
    onScene: ((PreviewScene) -> Unit)? = null,
    event: Notice? = null,
    onExpand: (() -> Unit)? = null,
    eventShown: Boolean = true,
    notificationStyle: Boolean = false,
    twoActivities: Boolean = false,
    respectVisibility: Boolean = false,
) {
    val s by SettingsRepo.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(s) { IslandType.apply(context, s) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF464361), Color(0xFF262B3A)))).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Живой пример", "Live preview"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (onExpand != null) TextButton(onClick = onExpand, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (card) tr("Свернуть", "Collapse") else tr("Раскрыть", "Expand"), color = Color.White, fontSize = 12.sp)
            } else Text(if (event != null) tr("Пример события", "Sample event") else if (twoActivities && scene != PreviewScene.IDLE && scene != PreviewScene.MESSAGE) tr("Музыка + таймер", "Music + timer") else if (card) tr("Раскрытая карточка", "Expanded card") else tr("Капсула", "Capsule"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(if (card) 155.dp else 85.dp), contentAlignment = Alignment.Center) {
            // Preview the real dimensions, then scale down only to fit the sample board.
            if (!eventShown || respectVisibility && scene == PreviewScene.IDLE && s.hideIdle) Text(tr("Пример скрыт", "Preview hidden"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            else Layout(content = { AppearancePreview(s, card, scene, event, notificationStyle, twoActivities) }) { measurables, constraints ->
                val max = 600.dp.roundToPx()
                val p = measurables.single().measure(Constraints(maxWidth = max, maxHeight = 600.dp.roundToPx()))
                val k = minOf(1f, constraints.maxWidth.toFloat() / p.width.coerceAtLeast(1), constraints.maxHeight.toFloat() / p.height.coerceAtLeast(1))
                val w = (p.width * k).toInt().coerceAtLeast(1)
                val h = (p.height * k).toInt().coerceAtLeast(1)
                layout(w, h) { p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) } }
            }
        }
        if (onScene != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(PreviewScene.IDLE, PreviewScene.MUSIC, PreviewScene.MESSAGE, PreviewScene.TIMER).forEach { candidate ->
                TextButton(onClick = { onScene(candidate) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text(when (candidate) {
                        PreviewScene.IDLE -> tr("Часы", "Clock")
                        PreviewScene.MUSIC -> tr("Музыка", "Music")
                        PreviewScene.MESSAGE -> tr("Сообщение", "Message")
                        PreviewScene.TIMER -> tr("Таймер", "Timer")
                    }, color = if (scene == candidate) Ui.Primary else Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
    }
}
