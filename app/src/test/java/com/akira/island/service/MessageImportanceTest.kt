package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class MessageImportanceTest {
    @Test fun minimizedSnapchatStyleMessagesAreIncludedWithSilentNotifications() {
        assertFalse(allowsMessageImportance(1, false, false))
        assertTrue(allowsMessageImportance(1, true, false))
        assertTrue(allowsMessageImportance(2, true, false))
    }
    @Test fun userBlockedChannelsAreNeverOverridden() {
        assertFalse(allowsMessageImportance(0, true, true))
        assertFalse(allowsMessageImportance(-1000, true, true))
    }
    @Test fun normalMessagesAndExplicitAlwaysRulesWork() {
        assertTrue(allowsMessageImportance(3, false, false))
        assertTrue(allowsMessageImportance(1, false, true))
    }
}
