package com.akira.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr

@Composable
fun IslandSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        thumbContent = {
            Icon(
                if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ui.OnPrimary,
            checkedTrackColor = Ui.Primary,
            checkedIconColor = Ui.Primary,
            checkedBorderColor = Ui.Primary,
            uncheckedThumbColor = Ui.Outline,
            uncheckedTrackColor = Ui.Bg,
            uncheckedBorderColor = Ui.Outline,
            uncheckedIconColor = Ui.Bg,
        ),
    )
}

/** Страница: крупный заголовок, кнопка «назад» и действие справа. */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ui.Card).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Назад", "Back"), tint = Ui.Text)
                }
                Spacer(Modifier.width(18.dp))
            } else {
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                color = Ui.Text,
                fontSize = if (onBack != null) 26.sp else 28.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Spacer(Modifier.width(10.dp))
                action()
            }
        }
        if (subtitle != null) {
            Text(
                subtitle,
                color = Ui.Sub.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Spacer(Modifier.height(130.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
fun PillAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Ui.IconBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(text, color = Ui.Text.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionHeader(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ui.Primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Отдельная карточка с иконкой в подложке и переключателем (экран карточек и событий). */
@Composable
fun ToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    color: Color? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Card)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(color ?: Ui.IconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (color != null) Color.White else Ui.Text, modifier = Modifier.size(if (color != null) 28.dp else 24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
            if (subtitle != null) Text(subtitle, color = Ui.Sub, fontSize = 14.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        VerticalDivider(Modifier.height(36.dp), thickness = 2.dp, color = Ui.Divider)
        Spacer(Modifier.width(14.dp))
        IslandSwitch(checked, onChange, enabled)
    }
}

enum class Pos { Single, First, Middle, Last }

fun posOf(index: Int, count: Int): Pos = when {
    count == 1 -> Pos.Single
    index == 0 -> Pos.First
    index == count - 1 -> Pos.Last
    else -> Pos.Middle
}

private fun Pos.shape(): Shape = when (this) {
    Pos.Single -> RoundedCornerShape(28.dp)
    Pos.First -> RoundedCornerShape(28.dp, 28.dp, 6.dp, 6.dp)
    Pos.Middle -> RoundedCornerShape(6.dp)
    Pos.Last -> RoundedCornerShape(6.dp, 6.dp, 28.dp, 28.dp)
}

/** Склеенные строки настроек: одна карточка со скруглением сверху и снизу. */
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

/** Значок пункта: белый на цветном скруглённом квадрате, как в «Настройках» iPhone; без цвета — просто значок. */
@Composable
fun ItemIcon(icon: ImageVector, color: Color?, size: Dp = 36.dp) {
    if (color == null) {
        Icon(icon, null, tint = Ui.Text, modifier = Modifier.size(26.dp))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupItem(
    pos: Pos,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    color: Color? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(pos.shape())
            .background(Ui.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(icon, color)
        Spacer(Modifier.width(if (color != null) 16.dp else 22.dp))
        Column(Modifier.weight(1f)) {
            // Метка переносится на следующую строку целиком, а название не разрывается
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp,
                    modifier = Modifier.align(Alignment.CenterVertically))
                if (badge != null) Badge(badge, Modifier.align(Alignment.CenterVertically))
            }
            if (subtitle != null) Text(subtitle, color = Ui.Sub, fontSize = 15.sp, lineHeight = 19.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Строка списка со своим значком слева (например, значком приложения) вместо векторного. */
@Composable
fun GroupRow(
    pos: Pos,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(pos.shape())
            .background(Ui.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
            if (subtitle != null) Text(subtitle, color = Ui.Sub, fontSize = 15.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
fun GroupToggle(
    pos: Pos,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    color: Color? = null,
    badge: String? = null,
    onChange: (Boolean) -> Unit,
) {
    GroupItem(pos, icon, title, subtitle, color = color, badge = badge, onClick = { onChange(!checked) }) { IslandSwitch(checked, onChange) }
}

/** Маленькая метка рядом с названием, например «Экспериментальный». */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Tile.Orange) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Круглая кнопка со значком в шапке страницы — в пару к кнопке «Назад», не отнимает место у заголовка. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Ui.Card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Ui.Text)
    }
}

@Composable
fun GroupSlider(
    pos: Pos,
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    steps: Int = 0,
    color: Color? = null,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(pos.shape())
            .background(Ui.Card)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(icon, color)
            Spacer(Modifier.width(if (color != null) 16.dp else 22.dp))
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(label, color = Ui.Sub, fontSize = 15.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Ui.Primary,
                activeTrackColor = Ui.Primary,
                inactiveTrackColor = Ui.IconBg,
            ),
        )
    }
}

/** Строка с ползунком и кнопками ±1 по краям — для точной подгонки размеров. */
@Composable
fun GroupStepSlider(
    pos: Pos,
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    unit: String = "dp",
    action: Pair<String, () -> Unit>? = null,
    color: Color? = null,
    onChange: (Int) -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    fun tick() = view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(pos.shape())
            .background(Ui.Card)
            .padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(icon, color)
            Spacer(Modifier.width(if (color != null) 16.dp else 22.dp))
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (action != null) {
                Text(
                    action.first, color = Ui.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).clickable { tick(); action.second() }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Box(Modifier.clip(CircleShape).background(Ui.IconBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("$value $unit", color = Ui.Text, fontSize = 14.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Rounded.Remove) {
                tick()
                onChange((value - 1).coerceAtLeast(range.first))
            }
            Slider(
                value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
                onValueChange = { v ->
                    val next = Math.round(v)
                    if (next != value) onChange(next)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Ui.Primary,
                    activeTrackColor = Ui.Primary,
                    inactiveTrackColor = Ui.IconBg,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            StepButton(Icons.Rounded.Add) {
                tick()
                onChange((value + 1).coerceAtMost(range.last))
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Ui.IconBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Ui.Text, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.Card,
        titleContentColor = Ui.Text,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onPick(o)
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = o == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Ui.Primary, unselectedColor = Ui.Outline),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label(o), color = Ui.Text, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Закрыть", "Close"), color = Ui.Primary) } },
    )
}
