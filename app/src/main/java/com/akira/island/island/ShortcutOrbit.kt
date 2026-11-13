package com.akira.island.island

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.ContactShortcut
import kotlin.math.roundToInt

private data class ShortcutEntry(val id: String, val pkg: String? = null, val contact: ContactShortcut? = null)

@Composable
internal fun ShortcutOrbit(apps: List<String>, contacts: List<ContactShortcut>, onApp: (String) -> Unit,
    onContact: (ContactShortcut) -> Unit) {
    val entries = remember(apps, contacts) {
        apps.distinct().map { ShortcutEntry("app:$it", pkg = it) } +
            contacts.distinctBy { it.uri }.map { ShortcutEntry("contact:${it.uri}", contact = it) }
    }
    if (entries.isEmpty()) return
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = shortcutPageCount(entries.size)
    val current = page.coerceIn(0, pages - 1)
    // Clamp the saved page too: removing items must not resurrect an unrelated later page.
    LaunchedEffect(pages) { page = page.coerceIn(0, pages - 1) }
    val visible = entries.drop(current * SHORTCUT_PAGE_SIZE).take(SHORTCUT_PAGE_SIZE)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val orbit = useShortcutOrbit(maxWidth.value, LocalDensity.current.fontScale)
            @Composable fun entry(item: ShortcutEntry) {
                key(item.id) {
                    val app = item.pkg?.let { rememberAppShortcut(it) }
                    val label = item.contact?.name ?: app!!.label
                    Column(Modifier.width(if (orbit) 80.dp else maxWidth / 2)
                        .heightIn(min = 80.dp).testTag(item.id)
                        .clickable(role = Role.Button, onClickLabel = tr("Открыть", "Open")) {
                            item.pkg?.let(onApp) ?: item.contact?.let(onContact)
                        }.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShortcutAvatar(app?.icon, item.contact?.name, Modifier.size(48.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, maxLines = if (orbit) 1 else 2,
                            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (orbit) {
                val diameter = maxWidth.coerceAtMost(300.dp)
                Layout(content = {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Apps, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(28.dp))
                    }
                    visible.forEach { entry(it) }
                }, modifier = Modifier.fillMaxWidth().height(diameter).testTag("shortcut-orbit")) { measurables, constraints ->
                    val size = diameter.roundToPx()
                    val tile = 80.dp.roundToPx()
                    val middle = measurables.first().measure(Constraints.fixed(56.dp.roundToPx(), 56.dp.roundToPx()))
                    val children = measurables.drop(1).map { it.measure(Constraints.fixed(tile, tile)) }
                    layout(constraints.maxWidth, size) {
                        val cx = constraints.maxWidth / 2f
                        val cy = size / 2f
                        middle.place((cx - middle.width / 2).roundToInt(), (cy - middle.height / 2).roundToInt())
                        val centers = shortcutOrbitCenters(children.size, size.toFloat(), tile.toFloat())
                        children.forEachIndexed { index, child ->
                            val (x, y) = centers[index]
                            child.place((cx - size / 2f + x - tile / 2f).roundToInt(), (y - tile / 2f).roundToInt())
                        }
                    }
                }
            } else Column(Modifier.testTag("shortcut-grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.chunked(2).forEach { row -> Row { row.forEach { entry(it) } } }
            }
        }
        if (pages > 1) Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page = current - 1 }, enabled = current > 0,
                modifier = Modifier.size(48.dp).testTag("shortcuts-previous")) {
                Icon(Icons.Rounded.ChevronLeft, tr("Предыдущие ярлыки", "Previous shortcuts"), tint = Color.White.copy(alpha = if (current > 0) 1f else 0.3f))
            }
            Text(trf("{0} из {1}", "{0} of {1}", current + 1, pages), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            IconButton(onClick = { page = current + 1 }, enabled = current < pages - 1,
                modifier = Modifier.size(48.dp).testTag("shortcuts-next")) {
                Icon(Icons.Rounded.ChevronRight, tr("Следующие ярлыки", "Next shortcuts"), tint = Color.White.copy(alpha = if (current < pages - 1) 1f else 0.3f))
            }
        }
    }
}
