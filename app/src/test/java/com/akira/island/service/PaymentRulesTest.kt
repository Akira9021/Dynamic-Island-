package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class PaymentRulesTest {
    @Test fun `whole zeros are significant and decimal zeros are not`() {
        assertNotEquals(PaymentRules.amountKey("5 ₽"), PaymentRules.amountKey("50 ₽"))
        assertNotEquals(PaymentRules.amountKey("50 ₽"), PaymentRules.amountKey("500 ₽"))
        assertEquals(PaymentRules.amountKey("500 ₽"), PaymentRules.amountKey("500,00 RUB"))
        assertEquals(PaymentRules.amountKey("1 250,50 руб."), PaymentRules.amountKey("RUB 1250.50"))
    }

    @Test fun `currencies remain distinct`() {
        assertNotEquals(PaymentRules.amountKey("500 ₽"), PaymentRules.amountKey("500 BYN"))
        assertNotEquals(PaymentRules.amountKey("$12.50"), PaymentRules.amountKey("€12.50"))
        assertEquals(PaymentRules.amountKey("Rp 50.000"), PaymentRules.amountKey("50000 IDR"))
    }

    @Test fun `historical notification cannot follow a later payment screen`() {
        assertFalse(PaymentRules.followsReady(1000, 2000, 10_500, 10_000, 180_000))
        assertTrue(PaymentRules.followsReady(2001, 2000, 10_500, 10_000, 180_000))
        assertFalse(PaymentRules.followsReady(2001, 2000, 190_000, 10_000, 180_000))
        assertFalse(PaymentRules.followsReady(2001, 2000, 10_500, 0, 180_000))
    }

    @Test fun `screen and notification coalesce but next same price purchase is allowed`() {
        val dedupe = PaymentDeduplicator()
        assertFalse(dedupe.duplicate("500 ₽", "screen:wallet:1", 1, 1000))
        assertTrue(dedupe.duplicate("500,00 RUB", "notification:bank:1", 1, 1100))
        assertFalse(dedupe.duplicate("500 ₽", "screen:wallet:2", 2, 2000))
        assertFalse(dedupe.duplicate("50 ₽", "notification:bank:2", 2, 2100))
    }

    @Test fun `repeated event is suppressed but independent bank events are not`() {
        val dedupe = PaymentDeduplicator()
        assertFalse(dedupe.duplicate("500 ₽", "notification:key:100", 0, 1000))
        assertTrue(dedupe.duplicate("500 ₽", "notification:key:100", 0, 1100))
        assertFalse(dedupe.duplicate("500 ₽", "notification:key:200", 0, 2000))
        assertFalse(dedupe.duplicate("500 ₽", "notification:key:100", 0, 182_000))
    }

    @Test fun `success requires confirmation and failure overrides positive substring`() {
        assertTrue(PayScreenParser.paid("Оплата прошла"))
        assertTrue(PayScreenParser.paid("Аплата выканана"))
        assertFalse(PayScreenParser.paid("Оплата не прошла"))
        assertFalse(PayScreenParser.paid("Payment unsuccessful"))
        assertFalse(PayScreenParser.paid("Поднесите телефон к терминалу"))
        assertFalse(PayScreenParser.paid("Баланс 500 BYN"))
    }

    @Test fun `amount extraction does not use card suffix as purchase amount`() {
        assertEquals("500 ₽", PayScreenParser.amount("Перевод на 500 ₽, счет карты *2067. Доступно 503,35 ₽"))
        assertEquals("12.50 USD", PayScreenParser.amount("Карта *1234. Покупка 12.50 USD"))
        assertTrue(PayScreenParser.ready("Для оплаты поднесите телефон к терминалу"))
    }
}
