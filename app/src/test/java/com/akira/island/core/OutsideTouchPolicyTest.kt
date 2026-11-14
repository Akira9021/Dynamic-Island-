package com.akira.island.core

import org.junit.Assert.*
import org.junit.Test

class OutsideTouchPolicyTest {
    @Test fun defaultRetainsExistingExpandedCardDismissal() {
        assertEquals(OutsideTouchDecision.COLLAPSE, outsideTouchDecision(true, false, false, false))
        assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(false, false, false, false))
    }
    @Test fun optInKeepsCardButDoesNotChangeReplyAndKeyboardRules() {
        assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(true, false, false, true))
        for (keep in listOf(false, true)) {
            assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(true, true, true, keep))
            assertEquals(OutsideTouchDecision.CLOSE_REPLY, outsideTouchDecision(true, true, false, keep))
        }
    }
}
