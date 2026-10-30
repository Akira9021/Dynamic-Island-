@file:OptIn(ExperimentalLayoutApi::class)

package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.IslandLook
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.ThemePalette
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.isOneUi
import com.akira.island.ui.rememberThemePalette

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
    val oneUi = s.look.isOneUi()
    val palette = rememberThemePalette(s.look)
    var styleDialog by remember { mutableStateOf(false) }

    Page(tr("Цвет капсулы", "Capsule color"), onBack) {
        IslandPreview()

        SectionHeader(tr("Стиль острова", "Island look"))
        Group {
            GroupItem(
                Pos.Single, Icons.Rounded.Smartphone, tr("Вид острова", "Island look"),
                "${s.look.title} — ${s.look.hint}", color = Tile.Indigo,
                onClick = { styleDialog = true },
            )
        }
        if (styleDialog) {
            ChoiceDialog(
                tr("Вид острова", "Island look"), IslandLook.entries, s.look,
                { "${it.title} — ${it.hint}" },
                { v -> SettingsRepo.update { copy(look = v) } },
            ) { styleDialog = false }
        }

        if (oneUi) {
            SectionHeader(tr("Цвет темы", "Theme color"))
            ThemeCard(palette)
            Text(
                if (palette.fromTheme) {
                    tr("Акцент взят из нынешней темы Samsung: обводка, свечение и кнопки в карточках окрашены им.",
                        "The accent comes from the current Samsung theme: outline, glow and card buttons use it.")
                } else {
                    tr("Цвет темы прочитать не удалось — остров в сером стиле. Смените обои или цветовую палитру и вернитесь.",
                        "Couldn't read the theme color — the island is gray. Change the wallpaper or color palette and come back.")
                },
                color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            SectionHeader(tr("Цвет", "Color"))
            Swatches(capsuleColors, s.color) { c -> SettingsRepo.update { copy(color = c) } }
        }

        SectionHeader(tr("Контур", "Outline"))
        Group {
            GroupToggle(
                Pos.First, Icons.Rounded.BorderStyle, tr("Контур", "Outline"),
                if (oneUi) tr("Тонкая обводка цвета темы вокруг острова", "A thin theme-colored line around the island")
                else tr("Тонкая обводка вокруг капсулы", "A thin line around the capsule"),
                s.outline, color = Tile.Gray,
            ) { v ->
                SettingsRepo.update { copy(outline = v) }
            }
            if (!oneUi) {
                GroupToggle(Pos.Middle, Icons.Rounded.Image, tr("Цвет из обложки", "Color from artwork"),
                    tr("Пока играет музыка, контур окрашивается в цвет обложки", "While music plays, the outline takes the artwork's color"),
                    s.outlineFromArt, color = Tile.Pink) { v -> SettingsRepo.update { copy(outlineFromArt = v, outline = outline || v) } }
            }
            GroupSlider(Pos.Last, Icons.Rounded.LineWeight, tr("Толщина", "Thickness"), s.outlineWidth, 0.5f..4f,
                "%.1f dp".format(s.outlineWidth), color = Tile.Gray) { v ->
                SettingsRepo.update { copy(outlineWidth = (v * 2).toInt() / 2f) }
            }
        }
        if (!oneUi) {
            Swatches(outlineColors, s.outlineColor) { c -> SettingsRepo.update { copy(outlineColor = c, outline = true) } }
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

/** Карточка с нынешним цветом темы: видно и акцент, и подложку One UI. */
@Composable
private fun ThemeCard(palette: ThemePalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Card)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(palette.surfaceTop, palette.surfaceBottom)))
                .border(2.dp, palette.accent, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(palette.accent))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (palette.fromTheme) tr("Цвет темы Samsung", "Samsung theme color") else tr("Серый стиль", "Gray style"),
                color = Ui.Text, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (palette.fromTheme) tr("Акцент ${palette.hex} · подложка One UI", "Accent ${palette.hex} · One UI surface")
                else tr("Цвет темы недоступен — остров серый", "Theme color unavailable — the island is gray"),
                color = Ui.Sub, fontSize = 14.sp, lineHeight = 18.sp,
            )
        }
        Icon(Icons.Rounded.Palette, null, tint = palette.accent)
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
