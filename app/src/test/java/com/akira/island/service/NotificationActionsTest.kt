package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class NotificationActionsTest {
    @Test fun clearableGuardProtectsOngoingAndDisappearedNotifications() {
        val cancelled = mutableListOf<String>()
        val actions = NotificationActions({ it == "message" }, { cancelled += it })
        assertFalse(actions.dismiss("ongoing-call"))
        assertFalse(actions.dismiss("gone"))
        assertTrue(actions.dismiss("message"))
        assertEquals(listOf("message"), cancelled)
    }

    @Test fun policyOrDisconnectedListenerFailureDoesNotCrashOrReportSuccess() {
        val actions = NotificationActions({ true }, { throw SecurityException("Disconnected") })
        assertFalse(actions.dismiss("message"))
    }

    @Test fun bulkDeleteDoesNotCancelDuplicatesOrProtectedEntries() {
        val cancelled = mutableListOf<String>()
        val actions = NotificationActions({ it != "service" }, { cancelled += it })
        assertEquals(2, actions.dismissAll(listOf("one", "one", "service", "two")))
        assertEquals(listOf("one", "two"), cancelled)
    }
}
