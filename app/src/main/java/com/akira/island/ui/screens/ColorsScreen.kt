@file:OptIn(ExperimentalLayoutApi::class)

package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

private val capsuleColors = listOf(
    0xFF000000, 0xFF111114, 0xFF1C1C22, 0xFF26233A, 0xFF1A2238, 0xFF102A2A,
    0xFF2B1730, 0xFF3A1A1A, 0xFF2E2A1A, 0xFF3D3D45, 0xFFF2F2F7, 0xFFC6C3F7,
)

private val outlineColors = listOf(
    0xFF3A3A40, 0xFF8E8E93, 0xFFFFFFFF, 0xFFC6C3F7, 0xFF0A84FF, 0xFF64D2FF,
    0xFF34C759, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF2D92, 0xFFBF5AF2,
)

@Composable
fun ColorsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Цвет капсулы", "Capsule color"), onBack) {
        IslandPreview()

        SectionHeader(tr("Цвет", "Color"))
        Swatches(capsuleColors, s.color) { c -> SettingsRepo.update { copy(color = c) } }

        SectionHeader(tr("Контур", "Outline"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.BorderStyle, tr("Контур", "Outline"),
                tr("Тонкая обводка вокруг капсулы", "A thin line around the capsule"),
                s.outline, color = Tile.Gray) { v ->
                SettingsRepo.update { copy(outline = v) }
            }
            GroupToggle(Pos.Middle, Icons.Rounded.Image, tr("Цвет из обложки", "Color from artwork"),
                tr("Пока играет музыка, контур окрашивается в цвет обложки", "While music plays, the outline takes the artwork's color"),
                s.outlineFromArt, color = Tile.Pink) { v -> SettingsRepo.update { copy(outlineFromArt = v, outline = outline || v) } }
            GroupSlider(Pos.Last, Icons.Rounded.LineWeight, tr("Толщина", "Thickness"), s.outlineWidth, 0.5f..4f,
                "%.1f dp".format(s.outlineWidth), color = Tile.Gray) { v ->
                SettingsRepo.update { copy(outlineWidth = (v * 2).toInt() / 2f) }
            }
        }
        Swatches(outlineColors, s.outlineColor) { c -> SettingsRepo.update { copy(outlineColor = c, outline = true) } }
        GroupSlider(Pos.Single, Icons.Rounded.Layers, tr("Непрозрачность контура", "Outline opacity"),
            s.outlineOpacity.toFloat(), 0f..100f, "${s.outlineOpacity}%", color = Tile.Gray,
        ) { v -> SettingsRepo.update { copy(outlineOpacity = v.toInt()) } }

        EdgeWidthControls(s, expanded = false)

        SectionHeader(tr("Раскрытые карточки", "Expanded cards"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Layers, tr("Отдельный цвет карточки", "Separate card color"),
                tr("Выключено — цвет как у капсулы", "Off uses the capsule color"),
                s.expandedColorEnabled, color = Tile.Purple) { v -> SettingsRepo.update { copy(expandedColorEnabled = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.BorderStyle, tr("Отдельный контур карточки", "Separate card outline"),
                tr("Свой цвет и толщина; выключено — контур как у капсулы", "Choose its color and width; off uses the capsule outline"),
                s.expandedOutlineWidth >= 0, color = Tile.Blue) { v -> SettingsRepo.update {
                    if (v) copy(expandedOutlineWidth = 1f)
                    else copy(expandedOutlineWidth = -1f, expandedOutlineTopWidth = -1f, expandedOutlineRightWidth = -1f,
                        expandedOutlineBottomWidth = -1f, expandedOutlineLeftWidth = -1f)
                } }
        }
        if (s.expandedColorEnabled) {
            Swatches(capsuleColors, s.expandedColor) { c -> SettingsRepo.update { copy(expandedColor = c) } }
        }
        if (s.expandedOutlineWidth >= 0) {
            Group {
                GroupSlider(Pos.First, Icons.Rounded.LineWeight, tr("Толщина контура карточки", "Card outline width"),
                    s.expandedOutlineWidth, 0f..6f, "%.1f dp".format(s.expandedOutlineWidth), color = Tile.Blue,
                ) { v -> SettingsRepo.update { copy(expandedOutlineWidth = (v * 2).toInt() / 2f) } }
                GroupSlider(Pos.Last, Icons.Rounded.Layers, tr("Непрозрачность контура карточки", "Card outline opacity"),
                    s.expandedOutlineOpacity.toFloat(), 0f..100f, "${s.expandedOutlineOpacity}%", color = Tile.Gray,
                ) { v -> SettingsRepo.update { copy(expandedOutlineOpacity = v.toInt()) } }
            }
            Swatches(outlineColors, s.expandedOutlineColor) { c -> SettingsRepo.update { copy(expandedOutlineColor = c) } }
        }
        EdgeWidthControls(s, expanded = true)

        SectionHeader(tr("Градиент", "Gradient"))
        GroupSlider(Pos.Single, Icons.Rounded.Layers, tr("Затемнение снизу", "Bottom shading"),
            s.bottomGradient.toFloat(), 0f..80f, "${s.bottomGradient}%", color = Tile.Indigo,
        ) { v -> SettingsRepo.update { copy(bottomGradient = v.toInt()) } }


        SectionHeader(tr("Основные кнопки", "Main controls"))
        GroupToggle(Pos.Single, Icons.Rounded.Layers, tr("Свой цвет кнопок", "Custom control color"),
            tr("Таймер, секундомер и основные действия; цвета звонков и предупреждений сохраняются", "Timer, stopwatch and main actions; call and warning colors stay unchanged"),
            s.actionColorEnabled, color = Tile.Purple) { v -> SettingsRepo.update { copy(actionColorEnabled = v) } }
        if (s.actionColorEnabled) {
            Swatches(outlineColors.drop(2), s.actionColor) { c -> SettingsRepo.update { copy(actionColor = c) } }
        }

        SectionHeader(tr("Тень", "Shadow"))
        GroupToggle(Pos.Single, Icons.Rounded.Layers, tr("Тень", "Shadow"),
            tr("Мягкая тень под капсулой — заметна на светлых экранах", "A soft shadow under the capsule — visible on light screens"),
            s.shadow, color = Tile.Indigo) { v ->
            SettingsRepo.update { copy(shadow = v) }
        }

        Text(
            tr("Цвет кнопок «Ответить» и «Пометить прочитанным» — в «Функции» → «Уведомления».",
                "The color of the Reply and Mark as read buttons is in Features → Notifications."),
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** Кружки цветов в карточке: выбранный — с галочкой. */
@Composable
fun Swatches(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Card)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { c ->
            val color = Color(c)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (c == selected) Ui.Primary else Ui.Divider, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) {
                    Icon(Icons.Rounded.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White)
                }
            }
        }
    }
}


/** Independent edges are optional; switching them off restores the inherited uniform outline. */
@Composable
private fun EdgeWidthControls(s: com.akira.island.data.IslandSettings, expanded: Boolean) {
    val values = if (expanded) listOf(s.expandedOutlineTopWidth, s.expandedOutlineRightWidth, s.expandedOutlineBottomWidth, s.expandedOutlineLeftWidth)
        else listOf(s.outlineTopWidth, s.outlineRightWidth, s.outlineBottomWidth, s.outlineLeftWidth)
    val enabled = values.any { it >= 0f }
    val inherited = if (expanded && s.expandedOutlineWidth >= 0f) s.expandedOutlineWidth else s.outlineWidth
    Group {
        GroupToggle(if (enabled) Pos.First else Pos.Single, Icons.Rounded.BorderStyle,
            if (expanded) tr("Стороны контура карточки", "Card outline edges") else tr("Стороны контура капсулы", "Capsule outline edges"),
            tr("Отдельная толщина сверху, справа, снизу и слева", "Separate widths at the top, right, bottom and left"),
            enabled, color = Tile.Purple,
        ) { on ->
            val width = if (on) inherited.coerceIn(0f, 6f) else -1f
            SettingsRepo.update {
                if (expanded) copy(expandedOutlineTopWidth = width, expandedOutlineRightWidth = width,
                    expandedOutlineBottomWidth = width, expandedOutlineLeftWidth = width,
                    expandedOutlineWidth = if (on) inherited else expandedOutlineWidth)
                else copy(outlineTopWidth = width, outlineRightWidth = width,
                    outlineBottomWidth = width, outlineLeftWidth = width, outline = outline || on)
            }
        }
        if (enabled) {
            val labels = listOf(tr("Сверху", "Top"), tr("Справа", "Right"), tr("Снизу", "Bottom"), tr("Слева", "Left"))
            labels.forEachIndexed { index, label ->
                val value = values[index].takeIf { it >= 0f } ?: inherited
                GroupSlider(if (index == 3) Pos.Last else Pos.Middle, Icons.Rounded.LineWeight, label,
                    value, 0f..6f, "%.1f dp".format(value), color = Tile.Gray,
                ) { v ->
                    val width = (v * 2).toInt() / 2f
                    SettingsRepo.update {
                        if (expanded) when (index) {
                            0 -> copy(expandedOutlineTopWidth = width)
                            1 -> copy(expandedOutlineRightWidth = width)
                            2 -> copy(expandedOutlineBottomWidth = width)
                            else -> copy(expandedOutlineLeftWidth = width)
                        } else when (index) {
                            0 -> copy(outlineTopWidth = width)
                            1 -> copy(outlineRightWidth = width)
                            2 -> copy(outlineBottomWidth = width)
                            else -> copy(outlineLeftWidth = width)
                        }
                    }
                }
            }
        }
    }
}
