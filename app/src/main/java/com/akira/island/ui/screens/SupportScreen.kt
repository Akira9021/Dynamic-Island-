package com.akira.island.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Tile

/** Entry point kept separate from the existing About implementation. */
@Composable
fun SupportAuthorItem(onClick: () -> Unit) {
    GroupItem(
        Pos.Single, Icons.Rounded.Favorite,
        tr("Поддержка автора", "Support the developer"),
        tr("Telegram и Boosty", "Telegram and Boosty"),
        color = Tile.Purple, onClick = onClick,
    ) { Arrow() }
    InfoText(tr(
        "Спасибо @uswer1106 за помощь в развитии Dynamic Island!",
        "Thank you @uswer1106 for helping improve Dynamic Island!",
    ))
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val openError = tr("Не удалось открыть ссылку", "Couldn't open the link")
    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, openError, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, openError, Toast.LENGTH_SHORT).show()
        }
    }

    Page(tr("Поддержка автора", "Support the developer"), onBack) {
        InfoText(tr(
            "Спасибо, что помогаете развивать Dynamic Island. Присоединяйтесь к общению в Telegram или поддержите автора на Boosty.",
            "Thank you for helping Dynamic Island grow. Join the conversation on Telegram or support the developer on Boosty.",
        ))
        Group {
            GroupItem(
                Pos.First, ImageVector.vectorResource(R.drawable.ic_support_telegram), "Telegram",
                tr("Общение и новости проекта", "Community and project news"),
                color = Color(0xFF229ED9),
                onClick = { openLink("https://t.me/+EvAUtTx5Rt84ZWI6") },
            ) { Arrow() }
            GroupItem(
                Pos.Last, ImageVector.vectorResource(R.drawable.ic_support_boosty), "Boosty",
                tr("Поддержать развитие острова", "Support the island's development"),
                color = Color(0xFFF26F35),
                onClick = { openLink("https://boosty.to/dynamicisland") },
            ) { Arrow() }
        }
    }
}
