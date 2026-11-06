package com.akira.island.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.LauncherVisibility
import com.akira.island.service.IslandTile
import com.akira.island.ui.Page
import com.akira.island.ui.PillAction
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Ui

/** Launcher visibility is deliberately separate from the island's enabled/accessibility state. */
@Composable
fun LauncherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by LauncherVisibility.state.collectAsState()
    rememberOnResume { LauncherVisibility.refresh(context) }
    var requestingTile by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Page(tr("Значок приложения", "App icon"), onBack) {
        InfoText(tr(
            "Можно убрать значок с рабочего стола и из списка приложений. Сам остров продолжит работать, а настройки и обновления сохранятся.",
            "You can hide the icon from the home screen and app drawer. The island keeps working, and your settings and updates are preserved.",
        ))
        SectionHeader(tr("Сначала проверьте вход в настройки", "First test how to reopen settings"))
        InfoText(tr(
            "Добавьте плитку «Остров» в быстрые настройки Android. Затем зажмите её: должен открыться этот экран. Перед каждым новым скрытием нужно проверить вход через плитку.",
            "Add the Island tile to Android Quick Settings. Then long-press it: this screen should open. Test the tile again before hiding the icon each time.",
        ))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!requestingTile) {
                PillAction(tr("Добавить или проверить плитку", "Add or check tile")) {
                    requestingTile = true
                    error = false
                    val manager = context.getSystemService(StatusBarManager::class.java)
                    val requested = runCatching {
                        checkNotNull(manager).requestAddTileService(
                            ComponentName(context, IslandTile::class.java), context.getString(R.string.tile_label),
                            Icon.createWithResource(context, R.drawable.ic_tab_island), context.mainExecutor,
                        ) { result ->
                            requestingTile = false
                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                            ) {
                                LauncherVisibility.onTileAdded(context)
                            } else error = true
                        }
                    }.isSuccess
                    if (!requested) {
                        requestingTile = false
                        error = true
                    }
                }
            } else {
                Text(tr("Ожидание ответа Android…", "Waiting for Android…"), color = Ui.Sub)
            }
        } else {
            InfoText(tr(
                "Откройте шторку полностью, нажмите «Изменить» и перенесите плитку «Остров» к активным плиткам. Если она уже была добавлена до обновления, удалите и добавьте её снова.",
                "Fully expand Quick Settings, tap Edit, and drag the Island tile into the active tiles. If it was already added before updating, remove it and add it again.",
            ))
        }
        Text(
            when {
                state.recoveryTested -> tr("Вход через плитку проверен", "Tile recovery tested")
                state.tileAdded -> tr("Плитка добавлена. Теперь зажмите её в шторке", "Tile added. Now long-press it in Quick Settings")
                else -> tr("Плитка ещё не подтверждена Android", "Android has not confirmed the tile yet")
            },
            color = Ui.Sub,
        )
        if (error) {
            Text(tr(
                "Не удалось выполнить действие. Значок остаётся доступен; попробуйте снова.",
                "The action could not be completed. The icon remains available; try again.",
            ), color = Ui.Sub)
        }

        TextButton(
            enabled = state.hidden || state.canHide,
            onClick = { error = !LauncherVisibility.setHidden(context, !state.hidden) },
        ) {
            Text(if (state.hidden) tr("Вернуть значок", "Show icon") else tr("Скрыть значок", "Hide icon"))
        }
        InfoText(tr(
            "Чтобы вернуть значок, зажмите плитку «Остров» и нажмите «Вернуть значок». Удаление плитки тоже вернёт значок. Ещё один вход: настройки Android → Спец. возможности → Динамический остров → настройки службы (значок шестерёнки).",
            "To restore the icon, long-press the Island tile and tap Show icon. Removing the tile also restores the icon. You can also use Android Settings → Accessibility → Dynamic Island → service settings (the gear icon).",
        ))
        InfoText(tr(
            "Некоторые рабочие столы обновляют значки с задержкой. Старый закреплённый ярлык после обновления может остаться — удалите его вручную. Android также может оставить системный значок «О приложении». Названия пунктов и видимость шестерёнки зависят от прошивки; если плитка не открывает этот экран, скрытие недоступно.",
            "Some launchers refresh icons with a delay. An old pinned shortcut may remain after updating; remove it manually. Android may also show a system App info entry. Menu names and the settings gear depend on your phone. Hiding stays unavailable if the tile does not open this screen.",
        ))
    }
}
