package com.akira.island.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotificationPresentationTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun absentPositionPreservesBothLegacySidesAndIconChoices() {
        for (mode in MessageIconMode.entries) {
            for (right in listOf(false, true)) {
                val saved = """{"messageStyle":"PILL","messageIconMode":"${mode.name}","messageIconRight":$right}"""
                val restored = json.decodeFromString<IslandSettings>(saved)
                assertEquals(mode, restored.messageIconMode)
                assertEquals(MessageStyle.PILL, restored.messageStyle)
                assertNull(restored.messageIconPosition)
                assertEquals(if (right) MessageIconPosition.RIGHT else MessageIconPosition.LEFT, restored.notificationIconPosition)
            }
        }
        assertEquals(MessageIconPosition.LEFT, IslandSettings().notificationIconPosition)
        assertEquals(MessageStyle.CARD, IslandSettings().messageStyle)
    }

    @Test fun choosingAPositionReplacesTheLegacyChoiceAndSurvivesRestart() {
        for (position in MessageIconPosition.entries) {
            val settings = IslandSettings(messageIconRight = true, messageIconMode = MessageIconMode.APP)
                .withMessageIconPosition(position)
            val restored = json.decodeFromString<IslandSettings>(json.encodeToString(settings))
            assertEquals(position, restored.notificationIconPosition)
            assertEquals(position == MessageIconPosition.RIGHT, restored.messageIconRight)
            assertEquals(MessageIconMode.APP, restored.messageIconMode)
        }
        // An explicit choice takes precedence even when an older backup contains a conflicting boolean.
        val conflicting = json.decodeFromString<IslandSettings>("""{"messageIconRight":true,"messageIconPosition":"CENTER"}""")
        assertEquals(MessageIconPosition.CENTER, conflicting.notificationIconPosition)
        val future = json.decodeFromString<IslandSettings>("""{"messageIconRight":true,"messageIconPosition":"FUTURE"}""")
        assertEquals(MessageIconPosition.RIGHT, future.notificationIconPosition)
    }

    @Test fun compactCountRemainsTheSameSavedStyle() {
        val restored = json.decodeFromString<IslandSettings>("""{"messageStyle":"ICON","messageIconRight":true}""")
        assertEquals(MessageStyle.ICON, restored.messageStyle)
        assertEquals(MessageIconPosition.RIGHT, restored.notificationIconPosition)
    }

    @Test fun retainedCountIncludesCurrentNotificationExactlyOnce() {
        assertEquals(1, retainedNotificationCount("current", emptyList()))
        assertEquals(1, retainedNotificationCount("current", listOf("current", "current")))
        assertEquals(3, retainedNotificationCount("current", listOf("other", "current", "third", "other")))
        assertEquals(3, retainedNotificationCount("current", listOf("other", "third")))
        assertEquals(2, retainedNotificationCount("current", listOf("other")))
    }
}
