package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class PaymentDisplayRulesTest {
    @Test fun emptyExpandedTextKeepsThePaymentAmountFromNormalText() {
        for (big in listOf(null, "", "  ")) {
            val body = paymentNotificationBody(big, "Покупка 350 ₽")
            assertEquals("350 ₽", PayScreenParser.amount(body))
        }
        assertEquals("Покупка 500 BYN", paymentNotificationBody("Покупка 500 BYN", "Short"))
    }
    @Test fun fastNewSuccessIsNotAbsorbedAsInitialScreenContent() {
        val base = setOf("Поднесите телефон к терминалу")
        val result = base + setOf("Оплата прошла", "350 ₽")
        assertFalse(paymentBaselinePending(150, base, result))
        val fresh = result - base
        assertTrue(fresh.any(PayScreenParser::paid))
        assertEquals(listOf("350 ₽"), fresh.mapNotNull(PayScreenParser::amount))
    }
    @Test fun oldPaidHistoryAndOrdinaryLayoutUpdatesStillSettle() {
        val base = setOf("Оплачено", "100 ₽")
        assertTrue(paymentBaselinePending(150, base, base + "Поднесите телефон к терминалу"))
        assertFalse(paymentBaselinePending(500, base, base))
    }
    @Test fun failureAndReadyTextNeverBecomeSuccess() {
        val base = setOf("Поднесите телефон к терминалу")
        for (text in listOf("Оплата не прошла", "Payment unsuccessful", "Баланс 350 ₽")) {
            assertTrue(paymentBaselinePending(150, base, base + text))
            assertFalse(PayScreenParser.paid(text))
        }
    }
}
