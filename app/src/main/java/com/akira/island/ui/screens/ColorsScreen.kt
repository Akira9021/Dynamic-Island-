@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.*
import com.akira.island.island.PreviewScene
import com.akira.island.ui.*

private val fillColors = listOf(0xFF000000, 0xFF111114, 0xFF1C1C22, 0xFF26233A, 0xFF1A2238, 0xFF102A2A,
    0xFF2B1730, 0xFF3A1A1A, 0xFF2E2A1A, 0xFF3D3D45, 0xFFF2F2F7, 0xFFC6C3F7)
private val lineColors = listOf(0xFF3A3A40, 0xFF8E8E93, 0xFFFFFFFF, 0xFFC6C3F7, 0xFF0A84FF, 0xFF64D2FF,
    0xFF34C759, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF2D92, 0xFFBF5AF2)

@Composable
fun ColorsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var card by rememberSaveable { mutableStateOf(false) }
    var scene by rememberSaveable { mutableStateOf(PreviewScene.MUSIC) }
    Page(tr("Цвет и рамка", "Color and border"), onBack, preview = {
        SettingsPreview(card, scene, onScene = { scene = it })
    }) {
        ChoiceStrip(listOf(false, true), card, { if (it) tr("Карточка", "Card") else tr("Капсула", "Capsule") }) { card = it }
        SettingsHint(if (card) tr("Карточка появляется при раскрытии острова и в уведомлениях.", "The card appears when the island expands and in notifications.")
            else tr("Капсула — небольшой остров вокруг камеры.", "The capsule is the small island around the camera."))

        SectionHeader(tr("Заливка", "Fill"))
        if (card) ChoiceStrip(listOf(false, true), s.expandedColorEnabled,
            { if (it) tr("Свой цвет", "Custom color") else tr("Как у капсулы", "Same as capsule") }) {
            SettingsRepo.update { copy(expandedColorEnabled = it) }
        }
        if (!card || s.expandedColorEnabled) Swatches(fillColors, if (card) s.expandedColor else s.color) { color ->
            SettingsRepo.update { if (card) copy(expandedColor = color) else copy(color = color) }
        } else SettingsHint(tr("Используется цвет капсулы. Чтобы изменить только карточку, выберите «Свой цвет».", "Uses the capsule color. Choose Custom color to change only the card."))

        SectionHeader(tr("Рамка", "Border"))
        ChoiceStrip(if (card) BorderMode.entries else listOf(BorderMode.NONE, BorderMode.CUSTOM), s.borderMode(card), {
            when (it) {
                BorderMode.INHERIT -> tr("Как у капсулы", "Same as capsule")
                BorderMode.NONE -> tr("Без рамки", "No border")
                BorderMode.CUSTOM -> tr("Своя рамка", "Custom border")
            }
        }) { mode -> SettingsRepo.update { withBorderMode(card, mode) } }
        if (s.borderMode(card) == BorderMode.INHERIT) SettingsHint(tr("Цвет, прозрачность и толщина рамки повторяют капсулу. Если у капсулы рамка выключена, у карточки её тоже нет.",
            "Color, opacity and widths follow the capsule. If the capsule has no border, neither does the card."))
        if (s.borderMode(card) == BorderMode.CUSTOM) {
            if (!card) {
                ChoiceStrip(listOf(false, true), s.outlineFromArt, { if (it) tr("Цвет активности", "Activity color") else tr("Свой цвет", "Custom color") }) {
                    SettingsRepo.update { copy(outlineFromArt = it) }
                }
            }
            if (card || !s.outlineFromArt) Swatches(lineColors, if (card) s.expandedOutlineColor else s.outlineColor) { color ->
                SettingsRepo.update { if (card) copy(expandedOutlineColor = color) else copy(outlineColor = color) }
            }
            else SettingsHint(tr("В примере показан цвет выбранной активности; при музыке он берётся из обложки.", "The preview uses the selected activity color; music uses its artwork color."))
            ChoiceStrip(BorderWidths.entries, s.borderWidths(card), { if (it == BorderWidths.UNIFORM) tr("Одинаковая толщина", "Uniform width") else tr("По сторонам", "Per edge") }) {
                mode -> SettingsRepo.update { withBorderWidths(card, mode) }
            }
            if (s.borderWidths(card) == BorderWidths.UNIFORM) {
                val value = if (card) s.expandedOutlineWidth else s.outlineWidth
                GroupSlider(Pos.Single, Icons.Rounded.LineWeight, tr("Толщина рамки", "Border width"), value.coerceIn(0.5f, 6f), 0.5f..6f, "%.1f dp".format(value), color = Tile.Blue) {
                    value -> SettingsRepo.update { if (card) copy(expandedOutlineWidth = half(value)) else copy(outlineWidth = half(value)) }
                }
            } else {
                val edges = if (card) listOf(s.expandedOutlineTopWidth, s.expandedOutlineRightWidth, s.expandedOutlineBottomWidth, s.expandedOutlineLeftWidth)
                    else listOf(s.outlineTopWidth, s.outlineRightWidth, s.outlineBottomWidth, s.outlineLeftWidth)
                val labels = listOf(tr("Сверху", "Top"), tr("Справа", "Right"), tr("Снизу", "Bottom"), tr("Слева", "Left"))
                val icons = listOf(Icons.Rounded.BorderTop, Icons.Rounded.BorderRight, Icons.Rounded.BorderBottom, Icons.Rounded.BorderLeft)
                Group {
                    edges.forEachIndexed { index, width ->
                        val value = width.takeIf { it >= 0f } ?: if (card) s.expandedOutlineWidth else s.outlineWidth
                        GroupSlider(posOf(index, 4), icons[index], labels[index], value, 0f..6f, "%.1f dp".format(value), color = Tile.Blue) { v ->
                            SettingsRepo.update { edge(card, index, half(v)) }
                        }
                    }
                }
                SettingsHint(tr("Ноль скрывает выбранную сторону. Общий ползунок толщины в этом режиме не используется.", "Zero hides that edge. Uniform width is not used in this mode."))
            }
            GroupSlider(Pos.Single, Icons.Rounded.Opacity, tr("Непрозрачность рамки", "Border opacity"),
                (if (card) s.expandedOutlineOpacity else s.outlineOpacity).toFloat(), 0f..100f,
                "${if (card) s.expandedOutlineOpacity else s.outlineOpacity}%", color = Tile.Gray) { v ->
                SettingsRepo.update { if (card) copy(expandedOutlineOpacity = v.toInt()) else copy(outlineOpacity = v.toInt()) }
            }
        }

        SectionHeader(tr("Материал и эффекты", "Material and effects"))
        SettingsHint(tr("Общие для капсулы и карточек. Пример сверху показывает выбранный вид.", "Shared by the capsule and cards. The preview above shows the selected appearance."))
        ChoiceStrip(listOf(false, true), s.liquidGlass, { if (it) tr("Стекло", "Glass") else tr("Обычная заливка", "Plain fill") }) {
            SettingsRepo.update { copy(liquidGlass = it) }
        }
        GroupSlider(Pos.Single, Icons.Rounded.Opacity, if (s.liquidGlass) tr("Плотность стекла", "Glass density") else tr("Непрозрачность заливки", "Fill opacity"),
            (if (s.liquidGlass) s.glassOpacity else s.islandOpacity).toFloat(), if (s.liquidGlass) 40f..100f else 20f..100f,
            "${if (s.liquidGlass) s.glassOpacity else s.islandOpacity}%", color = Tile.Teal) { v ->
            SettingsRepo.update { if (liquidGlass) copy(glassOpacity = v.toInt()) else copy(islandOpacity = v.toInt()) }
        }
        if (s.liquidGlass) GroupToggle(
            Pos.Single,
            Icons.Rounded.AutoAwesome,
            tr("Живое преломление", "Live refraction"),
            tr(
                "Шейдер преломляет настоящий фон под островом. Нужны спец. возможности; снимок окна — Android 14+.",
                "The shader bends the real backdrop. Requires accessibility; window capture needs Android 14+.",
            ),
            s.glassShader,
            color = Tile.Purple,
        ) { v -> SettingsRepo.update { copy(glassShader = v) } }
        GroupSlider(Pos.Single, Icons.Rounded.Gradient, tr("Затемнение снизу", "Bottom shading"), s.bottomGradient.toFloat(), 0f..80f, "${s.bottomGradient}%", color = Tile.Indigo) {
            v -> SettingsRepo.update { copy(bottomGradient = v.toInt()) }
        }
        SettingsHint(tr("Затемнение видно на светлой заливке. На чёрном цвете разницы не будет.", "Bottom shading is visible on light fills. It has no visible effect on black."))
        GroupToggle(Pos.Single, Icons.Rounded.Tonality, tr("Тень", "Shadow"), tr("Заметна на светлом фоне; режим «Уменьшить эффекты» отключает её.", "Visible on light backgrounds; Reduce effects disables it."), s.shadow, color = Tile.Gray) {
            v -> SettingsRepo.update { copy(shadow = v) }
        }

        SectionHeader(tr("Основные кнопки", "Main controls"))
        ChoiceStrip(listOf(false, true), s.actionColorEnabled, { if (it) tr("Свой цвет", "Custom color") else tr("Стандартные цвета", "Default colors") }) {
            enabled -> card = true; scene = PreviewScene.TIMER; SettingsRepo.update { copy(actionColorEnabled = enabled) }
        }
        if (s.actionColorEnabled) Swatches(lineColors.drop(2), s.actionColor) { color ->
            card = true; scene = PreviewScene.TIMER; SettingsRepo.update { copy(actionColor = color) }
        }
        SettingsHint(tr("Показаны кнопки таймера. Цвета ответа на сообщения настраиваются в «Уведомлениях»; красный и зелёный у звонков сохраняются.",
            "The preview shows timer controls. Reply colors are set in Notifications; red and green call controls keep their meaning."))
    }
}

@Composable
fun <T> ChoiceStrip(values: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).selectableGroup()) {
        values.forEachIndexed { index, value ->
            val checked = selected == value
            Row(Modifier.fillMaxWidth().background(if (checked) Ui.Primary.copy(alpha = 0.08f) else Color.Transparent)
                .selectable(selected = checked, role = Role.RadioButton, onClick = { onSelect(value) })
                .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(checked, onClick = null, modifier = Modifier.size(24.dp),
                    colors = RadioButtonDefaults.colors(selectedColor = Ui.Primary, unselectedColor = Ui.Sub))
                Spacer(Modifier.width(12.dp))
                Text(title(value), color = Ui.Text, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
            if (index != values.lastIndex) HorizontalDivider(Modifier.padding(start = 52.dp, end = 16.dp), color = Ui.Divider)
        }
    }
}

@Composable
fun SettingsHint(text: String) { Text(text, color = Ui.Sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 6.dp)) }

private fun half(value: Float) = kotlin.math.round(value * 2) / 2f
private fun IslandSettings.edge(card: Boolean, index: Int, value: Float): IslandSettings = if (card) when (index) {
    0 -> copy(expandedOutlineTopWidth = value); 1 -> copy(expandedOutlineRightWidth = value)
    2 -> copy(expandedOutlineBottomWidth = value); else -> copy(expandedOutlineLeftWidth = value)
} else when (index) {
    0 -> copy(outlineTopWidth = value); 1 -> copy(outlineRightWidth = value)
    2 -> copy(outlineBottomWidth = value); else -> copy(outlineLeftWidth = value)
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
