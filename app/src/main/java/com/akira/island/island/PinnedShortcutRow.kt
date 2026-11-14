package com.akira.island.island

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.data.ContactShortcut
import com.akira.island.data.ExtrasRepo

@Composable
internal fun PinnedShortcutRow() {
    val extras by ExtrasRepo.state.collectAsState()
    val locked by IslandBus.locked.collectAsState()
    if (locked) return
    val context = LocalContext.current
    val preview = LocalIslandPreview.current
    ShortcutRow(extras.apps, extras.contacts,
        onApp = { if (!preview) Controls.openApp(context, it) },
        onContact = { contact -> if (!preview) {
            val uri = Uri.parse(contact.uri)
            if (uri.scheme == "content" && uri.authority == "com.android.contacts") {
                Controls.collapse()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } })
}

/** One short row under the date; scrolling reveals extra targets without growing the card. */
@Composable
internal fun ShortcutRow(apps: List<String>, contacts: List<ContactShortcut>, onApp: (String) -> Unit,
    onContact: (ContactShortcut) -> Unit) {
    val packages = remember(apps) { apps.distinct() }
    val people = remember(contacts) { contacts.distinctBy { it.uri } }
    if (packages.isEmpty() && people.isEmpty()) return
    val preview = LocalIslandPreview.current
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.isScrollInProgress) { if (!preview) IslandBus.lastTouch = SystemClock.elapsedRealtime() }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).horizontalScroll(scroll)
        .testTag("shortcut-row"), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        @Composable fun target(id: String, label: String, action: () -> Unit, avatar: @Composable () -> Unit) {
            Box(Modifier.size(44.dp).clip(CircleShape).testTag(id).semantics { contentDescription = label }
                .clickable(role = Role.Button) { if (!preview) IslandBus.lastTouch = SystemClock.elapsedRealtime(); action() },
                contentAlignment = Alignment.Center) { avatar() }
        }
        packages.forEach { pkg -> key("app:$pkg") {
            val app = rememberAppShortcut(pkg)
            target("app:$pkg", app.label, { onApp(pkg) }) { ShortcutAvatar(app.icon, null, Modifier.size(36.dp)) }
        } }
        people.forEach { contact -> key("contact:${contact.uri}") {
            target("contact:${contact.uri}", contact.name, { onContact(contact) }) {
                ShortcutAvatar(null, contact.name, Modifier.size(36.dp))
            }
        } }
    }
}
