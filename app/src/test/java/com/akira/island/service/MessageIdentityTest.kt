package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class MessageIdentityTest {
    private val first = MessageIdentity("Chat", "OK", "Alice", 1_000, 1)

    @Test fun identicalTextWithNewTimestampIsNewMessage() {
        val d = MessageDeduplicator()
        assertTrue(d.shouldShow("chat", "app", first, true, 0))
        assertTrue(d.shouldShow("chat", "app", first.copy(timestamp = 2_000), true, 100))
    }

    @Test fun artworkOrRankingUpdateIsNotNewMessage() {
        val d = MessageDeduplicator()
        assertTrue(d.shouldShow("chat", "app", first, true, 0))
        assertFalse(d.shouldShow("chat", "app", first, true, 100))
    }

    @Test fun historyCountDisambiguatesMessagesWithSameTimestamp() {
        val d = MessageDeduplicator()
        assertTrue(d.shouldShow("chat", "app", first, true, 0))
        assertTrue(d.shouldShow("chat", "app", first.copy(count = 2), true, 100))
    }

    @Test fun sameTextInDifferentAppsIsNotDeduplicated() {
        val d = MessageDeduplicator()
        assertTrue(d.shouldShow("chat1", "app1", first, true, 0))
        assertTrue(d.shouldShow("chat2", "app2", first, true, 100))
    }

    @Test fun duplicateLogicalEventWithAnotherKeyIsSuppressedOnlyWhenEnabled() {
        val d = MessageDeduplicator()
        assertTrue(d.shouldShow("chat1", "app", first, true, 0))
        assertFalse(d.shouldShow("chat2", "app", first, true, 100))
        assertTrue(d.shouldShow("chat3", "app", first, false, 100))
    }
    @Test fun noTimestampDoesNotCauseCrossKeyContentDeduplication() {
        val d = MessageDeduplicator()
        val withoutTime = first.copy(timestamp = 0)
        assertTrue(d.shouldShow("one", "app", withoutTime, true, 0))
        assertTrue(d.shouldShow("two", "app", withoutTime, true, 100))
    }

    @Test fun noTimestampStillUsesAnExplicitMessageCount() {
        val d = MessageDeduplicator()
        val withoutTime = first.copy(timestamp = 0)
        assertTrue(d.shouldShow("chat", "app", withoutTime, true, 0))
        assertFalse(d.shouldShow("chat", "app", withoutTime, true, 100))
        assertTrue(d.shouldShow("chat", "app", withoutTime.copy(count = 2), true, 200))
    }

}
