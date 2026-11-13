package com.akira.island.island

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
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
    onContact: (ContactShortcut) -> Unit, onCenter: (() -> Unit)? = null) {
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
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val orbit = useShortcutOrbit(maxWidth.value, LocalDensity.current.fontScale, visible.size)
            val gridColumns=(maxWidth.value / 44f).toInt().coerceIn(1,4)
            @Composable fun entry(item: ShortcutEntry) {
                key(item.id) {
                    val app = item.pkg?.let { rememberAppShortcut(it) }
                    val label = item.contact?.name ?: app!!.label
                    Box(Modifier.size(44.dp).clip(CircleShape).testTag(item.id)
                        .semantics { contentDescription = label }
                        .clickable(role = Role.Button, onClickLabel = tr("Открыть", "Open")) {
                            item.pkg?.let(onApp) ?: item.contact?.let(onContact)
                        }, contentAlignment = Alignment.Center) {
                        ShortcutAvatar(app?.icon, item.contact?.name, Modifier.size(if(orbit) 36.dp else 44.dp))
                    }
                }
            }
            @Composable fun center() {
                    Box(Modifier.size(68.dp).clip(CircleShape).glassSurface(CircleShape, Color(0xFF78858F), .25f)
                        .then(if (onCenter != null) Modifier.semantics { contentDescription = tr("Открыть сводку", "Open summary") }
                            .clickable(role=Role.Button, onClick=onCenter) else Modifier), contentAlignment=Alignment.Center) {
                        if (onCenter != null) Icon(Icons.Rounded.Schedule, null, tint=Color.White.copy(alpha=.7f), modifier=Modifier.size(20.dp))
                    }
            }
            if (orbit) {
                val diameter = if (visible.size <= 6) 176.dp else 216.dp
                Layout(content = {
                    center()
                    visible.forEach { entry(it) }
                }, modifier = Modifier.align(Alignment.CenterEnd).size(diameter).testTag("shortcut-orbit")) { measurables, constraints ->
                    val size = diameter.roundToPx()
                    val tile = 44.dp.roundToPx()
                    val middle = measurables.first().measure(Constraints.fixed(68.dp.roundToPx(), 68.dp.roundToPx()))
                    val children = measurables.drop(1).map { it.measure(Constraints.fixed(tile, tile)) }
                    layout(size, size) {
                        val cx = size / 2f
                        val cy = size / 2f
                        val centerAt=shortcutCloudCenter(children.size,size.toFloat())
                        middle.place((centerAt.first - middle.width / 2).roundToInt(), (centerAt.second - middle.height / 2).roundToInt())
                        val centers = shortcutOrbitCenters(children.size, size.toFloat(), tile.toFloat())
                        children.forEachIndexed { index, child ->
                            val (x, y) = centers[index]
                            child.place((cx - size / 2f + x - tile / 2f).roundToInt(), (y - tile / 2f).roundToInt())
                        }
                    }
                }
            } else Column(Modifier.testTag("shortcut-grid"), horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCenter != null) center()
                visible.chunked(gridColumns).forEach { row -> Row { row.forEach { entry(it) } } }
            }
        }
        if (pages > 1) Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { com.akira.island.core.IslandBus.lastTouch = android.os.SystemClock.elapsedRealtime(); page = current - 1 }, enabled = current > 0,
                modifier = Modifier.size(48.dp).testTag("shortcuts-previous")) {
                Icon(Icons.Rounded.ChevronLeft, tr("Предыдущие ярлыки", "Previous shortcuts"), tint = Color.White.copy(alpha = if (current > 0) 1f else 0.3f))
            }
            Text(trf("{0} из {1}", "{0} of {1}", current + 1, pages), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            IconButton(onClick = { com.akira.island.core.IslandBus.lastTouch = android.os.SystemClock.elapsedRealtime(); page = current + 1 }, enabled = current < pages - 1,
                modifier = Modifier.size(48.dp).testTag("shortcuts-next")) {
                Icon(Icons.Rounded.ChevronRight, tr("Следующие ярлыки", "Next shortcuts"), tint = Color.White.copy(alpha = if (current < pages - 1) 1f else 0.3f))
            }
        }
    }
}
