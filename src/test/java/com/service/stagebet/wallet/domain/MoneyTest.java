package com.service.stagebet.wallet.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesCurrencyAndScale() {
        Money money = new Money(new BigDecimal("10.00"), "usd");

        assertEquals(new BigDecimal("10.00"), money.amount());
        assertEquals("USD", money.currency());
    }

    @Test
    void addsMoneyWithTheSameCurrency() {
        Money result = new Money(new BigDecimal("10.00"), "USD")
                .add(new Money(new BigDecimal("2.50"), "USD"));

        assertEquals(new BigDecimal("12.50"), result.amount());
    }

    @Test
    void rejectsNegativeMoney() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("-0.01"), "USD"));
    }

    @Test
    void rejectsDifferentCurrencies() {
        Money usd = new Money(new BigDecimal("10.00"), "USD");
        Money eur = new Money(new BigDecimal("2.00"), "EUR");

        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThrows(ArithmeticException.class,
                () -> new Money(new BigDecimal("10.001"), "USD"));
    }
}
