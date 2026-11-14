package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class MediaNotificationEvidenceTest {
    @Test fun categoryAndTokenRetainTheirExistingMeaning() {
        assertTrue(mediaNotificationEvidence("transport", false, null))
        assertTrue(mediaNotificationEvidence(null, true, null))
    }
    @Test fun frameworkAndAndroidxMediaStylesDoNotNeedACategoryOrToken() {
        for (template in listOf("android.app.Notification\$MediaStyle", "android.app.Notification\$DecoratedMediaCustomViewStyle",
            "androidx.media.app.NotificationCompat\$MediaStyle", "androidx.media.app.NotificationCompat\$DecoratedMediaCustomViewStyle"))
            assertTrue(template, mediaNotificationEvidence(null, false, template))
    }
    @Test fun downloadAndUnrecognisedTemplatesDoNotBecomeMusic() {
        for (template in listOf(null, "", "android.app.Notification\$BigTextStyle", "example.MediaStyle", "MediaStyle download"))
            assertFalse(mediaNotificationEvidence("progress", false, template))
    }
}
