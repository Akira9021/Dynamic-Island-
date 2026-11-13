package com.akira.island.island

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ShortcutVisual(val label: String, val icon: ImageBitmap? = null)

/** Resolve once per visible package, away from composition/drawing; missing apps keep a safe fallback. */
@Composable
internal fun rememberAppShortcut(pkg: String): ShortcutVisual {
    val context = LocalContext.current
    var visual by remember(context, pkg) { mutableStateOf(ShortcutVisual(pkg)) }
    LaunchedEffect(context, pkg) {
        visual = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                ShortcutVisual(pm.getApplicationLabel(info).toString(),
                    runCatching { pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap() }.getOrNull())
            }.getOrDefault(ShortcutVisual(pkg))
        }
    }
    return visual
}

@Composable
internal fun ShortcutAvatar(icon: ImageBitmap?, contactName: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(if (contactName != null) Color(0xFF315D58) else Color(0xFF303039)),
        contentAlignment = Alignment.Center) {
        when {
            icon != null -> Image(icon, null, Modifier.fillMaxSize())
            contactName != null -> Text(shortcutInitials(contactName), color = Color.White, fontSize = 14.sp,
                maxLines = 1, customizable = false)
            else -> Icon(Icons.Rounded.Apps, null, tint = Color.White)
        }
    }
}
