package com.akira.island.security

import org.junit.Assert.*
import org.junit.Test

class DiagnosticNumbersTest {
    @Test fun cardAndAccountNumbersAreHiddenEvenWhenGrouped() {
        val result = redactDiagnosticNumbers("Оплата 350 ₽, карта 1234 5678 9012 3456, счёт 12345678901234567890")
        assertEquals("Оплата 350 ₽, карта …, счёт …", result)
        assertEquals("…", redactDiagnosticNumbers("1234\u202F5678\u202F9012\u202F3456"))
        assertFalse(redactDiagnosticNumbers("+7 (900) 123-45-67").contains("123-45-67"))
    }
    @Test fun ordinaryAmountsAndTimesRemainUsefulForDiagnosis() {
        val text = "19:15:00 · Оплата 350 ₽, остаток 1250.50 ₽"
        assertEquals(text, redactDiagnosticNumbers(text))
        assertTrue(redactDiagnosticNumbers("x".repeat(10_000)).length <= 4096)
    }
}
