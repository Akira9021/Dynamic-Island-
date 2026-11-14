package com.akira.island.service

/** Exact framework/AndroidX templates are media evidence even when category/token is missing. */
internal fun mediaNotificationEvidence(category: String?, mediaSessionPresent: Boolean, template: String?): Boolean =
    category == "transport" || mediaSessionPresent || template in mediaTemplates

private val mediaTemplates = setOf(
    "android.app.Notification\$MediaStyle",
    "android.app.Notification\$DecoratedMediaCustomViewStyle",
    "androidx.media.app.NotificationCompat\$MediaStyle",
    "androidx.media.app.NotificationCompat\$DecoratedMediaCustomViewStyle",
)
