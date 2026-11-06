package com.akira.island.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.akira.island.R
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.service.IslandTile
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

/**
 * «Банк не пускает»: многие банки (K PLUS, GCash и другие) не открываются, пока включена служба спец. возможностей
 * не из Play Маркета, — так они защищаются от мошенников. Остров тут ни при чём, и обойти проверку изнутри него
 * нельзя: только выключить остров на время. Здесь — как сделать это одним нажатием и включить обратно.
 */
@Composable
fun BanksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val running by IslandBus.serviceRunning.collectAsState()

    Page(tr("Банк не пускает", "Bank app won't open"), onBack,
        subtitle = tr("Банк пишет, что обнаружено стороннее приложение или спец. возможности?",
            "Your bank says it detected a third-party app or accessibility service?")) {
        InfoText(
            tr(
                "Некоторые банки ограничивают работу, пока включена служба спец. возможностей. " +
                    "Остров не обходит эту проверку — его нужно выключить на время. Проще всего плиткой в быстрых настройках: " +
                    "нажмите, чтобы выключить остров и открыть банк; нажмите ещё раз, чтобы вернуться к включению острова.",
                "Some banks restrict access while an accessibility service is enabled. " +
                    "The island cannot bypass this check — turn it off temporarily. The easiest way is the quick settings tile: " +
                    "tap it to turn the island off and open your bank; tap it again to return to enabling the island.",
            ),
        )
        Group {
            val tileAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (tileAsk) {
                GroupItem(
                    Pos.First, Icons.Rounded.AddBox, tr("Добавить плитку «Остров»", "Add the Island tile"),
                    tr("В шторку быстрых настроек — одно нажатие", "To quick settings — one tap"),
                    color = Tile.Blue, onClick = { addTile(context) },
                ) { Arrow() }
            }
            GroupItem(
                if (tileAsk) Pos.Middle else Pos.First, Icons.Rounded.PowerSettingsNew, tr("Выключить остров сейчас", "Turn the island off now"),
                if (running) tr("Включить обратно — в спец. возможностях или плиткой", "Turn it back on in Accessibility or with the tile")
                else tr("Остров уже выключен", "The island is already off"),
                color = Tile.Orange, onClick = { if (running) Controls.turnOffIsland() },
            )
            GroupItem(
                Pos.Last, Icons.Rounded.Lock, tr("Включить остров", "Turn the island on"),
                tr("Откроются спец. возможности — «Динамический остров» → включить", "Opens Accessibility — Dynamic Island → turn on"),
                color = Tile.Green, onClick = { Perms.openAccessibility(context) },
            ) { Arrow() }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            SectionHeader(tr("Плитка в шторке", "The quick settings tile"))
            InfoText(
                tr("Откройте шторку целиком, нажмите карандаш и перетащите плитку «Остров» к остальным.",
                    "Open quick settings fully, tap the pencil and drag the Island tile to the others."),
            )
        }
    }
}

/** Android 13+: система сама спрашивает, добавить ли плитку острова в шторку. */
private fun addTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val bar = context.getSystemService(StatusBarManager::class.java) ?: return
    runCatching {
        bar.requestAddTileService(
            ComponentName(context, IslandTile::class.java), context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tab_island), context.mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                Toast.makeText(context, tr("Плитка уже в шторке", "The tile is already in quick settings"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Пункт «Банк не пускает» — на главной, рядом с «Чтобы остров не закрывался». */
@Composable
internal fun BanksItem(pos: Pos, open: (Screen) -> Unit) {
    GroupItem(pos, Icons.Rounded.AccountBalance, tr("Банк не пускает", "Bank app won't open"),
        tr("Плитка, чтобы выключать остров на время", "A tile to turn the island off for a while"),
        onClick = { open(Screen.BANKS) }, color = Tile.Indigo) { Arrow() }
}
