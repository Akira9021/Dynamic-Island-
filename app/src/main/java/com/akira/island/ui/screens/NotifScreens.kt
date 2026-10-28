package com.akira.island.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.AppRule
import com.akira.island.data.SettingsRepo
import com.akira.island.service.NotifApps
import com.akira.island.service.NotificationParser
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupRow
import com.akira.island.ui.ItemIcon
import com.akira.island.ui.Page
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.posOf

/**
 * «Приложения» в уведомлениях: для каждого приложения, от которого приходили уведомления, — как в системе,
 * всегда (и тихие) или никогда. Список приложений телефона Android без особого разрешения не даёт,
 * поэтому здесь те, кто уже писал.
 */
@Composable
fun NotifAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NotifApps.load(context) }
    val apps by NotifApps.apps.collectAsState()
    val s by SettingsRepo.state.collectAsState()
    var pick by remember { mutableStateOf<Pair<String, String>?>(null) }

    Page(tr("Приложения", "Apps"), onBack) {
        Text(
            tr("Здесь появляются приложения, от которых приходили уведомления. «Всегда» — показывать и тихие, «Никогда» — не показывать в острове.",
                "Apps that have sent notifications show up here. Always — also show silent ones, Never — don't show them in the island."),
            color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
        )
        val list = apps.entries.sortedBy { it.value.lowercase() }
        if (list.isEmpty()) {
            Text(tr("Пока пусто: уведомлений приложений ещё не приходило.", "Empty for now: no app notifications have arrived yet."),
                color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Group {
            list.forEachIndexed { i, (pkg, label) ->
                GroupRow(posOf(i, list.size), label, ruleLabel(s.msgAppRules[pkg]), onClick = { pick = pkg to label }) {
                    val icon = remember(pkg) { NotificationParser.icon(context, pkg) }
                    if (icon != null) {
                        Image(icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        ItemIcon(Icons.Rounded.Apps, Tile.Gray)
                    }
                }
            }
        }
    }

    pick?.let { (pkg, label) ->
        ChoiceDialog(label, listOf<AppRule?>(null, AppRule.ALWAYS, AppRule.NEVER), s.msgAppRules[pkg], ::ruleLabel, { v ->
            SettingsRepo.update { copy(msgAppRules = if (v == null) msgAppRules - pkg else msgAppRules + (pkg to v)) }
        }) { pick = null }
    }
}

private fun ruleLabel(rule: AppRule?) = when (rule) {
    null -> tr("Как в системе — то, что всплывает", "Like the system — what pops up")
    AppRule.ALWAYS -> tr("Всегда, и тихие", "Always, silent ones too")
    AppRule.NEVER -> tr("Никогда", "Never")
}
