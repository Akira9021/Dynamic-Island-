package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akira.island.core.tr
import com.akira.island.data.*
import com.akira.island.data.TapAction
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile
import com.akira.island.ui.SectionHeader

private enum class Which { TAP, DOUBLE, LONG }

@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var swipeDialog by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Which?>(null) }

    Page(tr("Жесты", "Gestures"), onBack,
        subtitle = tr("Раскрытый остров сворачивается нажатием или касанием за его пределами",
            "The expanded island collapses with a tap or a touch outside it")) {
        SectionHeader(tr("Нажатия", "Taps"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.TouchApp, tr("Одиночное нажатие", "Single tap"), s.tapAction.title,
                onClick = { dialog = Which.TAP }, color = Tile.Blue)
            GroupItem(Pos.Middle, Icons.Rounded.TouchApp, tr("Двойное нажатие", "Double tap"), s.doubleTapAction.title,
                onClick = { dialog = Which.DOUBLE }, color = Tile.Indigo)
            GroupItem(Pos.Last, Icons.Rounded.PanTool, tr("Долгое нажатие", "Long press"), s.longPressAction.title,
                onClick = { dialog = Which.LONG }, color = Tile.Orange)
        }
        SectionHeader(tr("Свайпы", "Swipes"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.KeyboardArrowDown, tr("Потянуть вниз", "Pull down"),
                tr("Раскрыть капсулу, вверх — свернуть", "Expand the capsule, up — collapse"),
                s.swipeExpand, color = Tile.Teal) { v ->
                SettingsRepo.update { copy(swipeExpand = v) }
            }
            GroupItem(Pos.Last, Icons.Rounded.SwapHoriz, tr("Горизонтальный свайп", "Horizontal swipe"), swipeLabel(s.sideSwipe),
                color = Tile.Purple, onClick = { swipeDialog = true })
        }
    }

    if (swipeDialog) ChoiceDialog(tr("Горизонтальный свайп", "Horizontal swipe"), SideSwipe.entries, s.sideSwipe, ::swipeLabel,
        { mode -> SettingsRepo.update { withSideSwipe(mode) } }) { swipeDialog = false }
    when (dialog) {
        Which.TAP -> ChoiceDialog(tr("Одиночное нажатие", "Single tap"), TapAction.entries, s.tapAction, { it.title },
            { v -> SettingsRepo.update { copy(tapAction = v) } }) { dialog = null }
        Which.DOUBLE -> ChoiceDialog(tr("Двойное нажатие", "Double tap"), TapAction.entries, s.doubleTapAction, { it.title },
            { v -> SettingsRepo.update { copy(doubleTapAction = v) } }) { dialog = null }
        Which.LONG -> ChoiceDialog(tr("Долгое нажатие", "Long press"), TapAction.entries, s.longPressAction, { it.title },
            { v -> SettingsRepo.update { copy(longPressAction = v) } }) { dialog = null }
        null -> Unit
    }
}

private fun swipeLabel(mode: SideSwipe): String = when (mode) {
    SideSwipe.OFF -> tr("Выключен", "Off")
    SideSwipe.TRACKS -> tr("Переключать треки", "Change tracks")
    SideSwipe.ACTIVITIES -> tr("Листать карточки", "Switch cards")
}
