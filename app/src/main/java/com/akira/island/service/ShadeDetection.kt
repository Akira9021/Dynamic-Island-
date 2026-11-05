package com.akira.island.service

internal object ShadeDetection {
    fun isPanel(viewId: String, className: String): Boolean {
        val id = viewId.substringAfterLast('/').lowercase()
        val cls = className.substringAfterLast('.').lowercase()
        return id in setOf("notification_panel", "notification_shade", "quick_settings_panel", "qs_panel") ||
            cls == "notificationshadewindowview" || cls == "notificationpanelview"
    }
}
