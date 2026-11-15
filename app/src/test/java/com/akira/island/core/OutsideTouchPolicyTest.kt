package com.akira.island.core

import org.junit.Assert.*
import org.junit.Test

class OutsideTouchPolicyTest {
    @Test fun defaultRetainsExistingExpandedCardDismissal() {
        assertEquals(OutsideTouchDecision.COLLAPSE, outsideTouchDecision(true, false, false, false, false))
        assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(false, false, false, false, false))
    }

    @Test fun messageDetailCollapsesLikeTheExpandedCard() {
        assertEquals(OutsideTouchDecision.COLLAPSE, outsideTouchDecision(false, true, false, false, false))
        assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(false, true, false, false, true))
    }

    @Test fun optInKeepsCardButDoesNotChangeReplyAndKeyboardRules() {
        assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(true, false, false, false, true))
        for (keep in listOf(false, true)) {
            assertEquals(OutsideTouchDecision.KEEP, outsideTouchDecision(true, false, true, true, keep))
            assertEquals(OutsideTouchDecision.CLOSE_REPLY, outsideTouchDecision(true, false, true, false, keep))
        }
    }
}
