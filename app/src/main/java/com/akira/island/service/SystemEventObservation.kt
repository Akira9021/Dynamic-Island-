package com.akira.island.service

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import kotlinx.coroutines.flow.MutableStateFlow

/** null means that Android has not supplied a reading, never an invented Off state. */
data class SystemEventReadings(
    val observing: Boolean = false,
    val vpn: Boolean? = null,
    val hotspot: Boolean? = null,
    val usbDebug: Boolean? = null,
    val wirelessDebug: Boolean? = null,
)

object SystemEventObservation {
    val state = MutableStateFlow(SystemEventReadings())
}

/** Initial snapshots and repeated OEM broadcasts are not changes. */
internal fun eventChanged(before: Boolean?, after: Boolean?, initial: Boolean = false): Boolean =
    !initial && before != null && after != null && before != after

enum class ConnectionEvent {
    VPN, HOTSPOT, USB_DEBUG, WIRELESS_DEBUG;

    val title: String get() = when (this) {
        VPN -> "VPN"
        HOTSPOT -> tr("Точка доступа", "Hotspot")
        USB_DEBUG -> tr("Отладка USB", "USB debugging")
        WIRELESS_DEBUG -> tr("Отладка Wi-Fi", "Wireless debugging")
    }
    val icon: ImageVector get() = when (this) {
        VPN -> Icons.Rounded.VpnKey
        HOTSPOT -> Icons.Rounded.WifiTethering
        USB_DEBUG -> Icons.Rounded.Usb
        WIRELESS_DEBUG -> Icons.Rounded.Adb
    }
    fun notice(on: Boolean) = Notice(title, if (on) tr("Вкл", "On") else tr("Выкл", "Off"), icon,
        if (!on) Tints.Gray else if (this == VPN || this == HOTSPOT) Tints.Blue else Tints.Teal)
}
