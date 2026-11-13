package com.akira.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.trf
import com.akira.island.island.ShortcutAvatar

/** Selection rows are always middle rows of ExtrasScreen's existing group. */
@Composable
internal fun SelectedShortcutRow(label: String, icon: ImageBitmap?, contactName: String?, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Ui.Card)
        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ShortcutAvatar(icon, contactName, Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), color = Ui.Text, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, trf("Убрать {0}", "Remove {0}", label), tint = Ui.Sub)
        }
    }
}
