package com.akira.island.island

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.akira.island.data.ContactShortcut

/** Small attached popup: shortcuts do not reserve space in the expanded card. */
@Composable
internal fun PinnedShortcutPopup(apps: List<String>, contacts: List<ContactShortcut>, onApp: (String) -> Unit,
    onContact: (ContactShortcut) -> Unit, onDismiss: () -> Unit, onSummary: (() -> Unit)? = null) {
    if (apps.isEmpty() && contacts.isEmpty()) return
    if (LocalIslandPreview.current) {
        ShortcutOrbit(apps, contacts, onApp, onContact, onSummary)
        return
    }
    val inset = with(LocalDensity.current) { 8.dp.roundToPx() }
    val top = with(LocalDensity.current) { 16.dp.roundToPx() }
    val placement = remember(inset, top) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
            (windowSize.width-popupContentSize.width-inset).coerceAtLeast(0),
            top.coerceAtMost((windowSize.height-popupContentSize.height-inset).coerceAtLeast(0)))
    } }
    Popup(popupPositionProvider=placement,onDismissRequest=onDismiss,
        properties=PopupProperties(focusable=false,dismissOnClickOutside=true,clippingEnabled=true)) {
        Box(Modifier.width(if (apps.distinct().size + contacts.distinctBy { it.uri }.size <= 6) 176.dp else 216.dp)) { ShortcutOrbit(apps,contacts,onApp,onContact,onSummary) }
    }
}

@Composable
internal fun PinnedShortcutLayer(apps: List<String>, contacts: List<ContactShortcut>, onSummary: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    PinnedShortcutPopup(apps, contacts,
        onApp = { com.akira.island.core.Controls.openApp(context, it) },
        onContact = { contact ->
            val uri = android.net.Uri.parse(contact.uri)
            if (uri.scheme == "content" && uri.authority == "com.android.contacts") {
                com.akira.island.core.Controls.collapse()
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }, onDismiss = { com.akira.island.core.Controls.collapse() }, onSummary = onSummary)
}
