package dev.jcpolicarpio.corepay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("arithmetic is exact and stays in minor units")
    void arithmetic() {
        Money ten = Money.of(1000, "AED");
        assertThat(ten.add(Money.of(250, "AED")).minorUnits()).isEqualTo(1250);
        assertThat(ten.subtract(Money.of(1200, "AED")).minorUnits()).isEqualTo(-200);
        assertThat(ten.negate().minorUnits()).isEqualTo(-1000);
        assertThat(Money.of(500, "aed").currency()).isEqualTo("AED");
    }

    @Test
    @DisplayName("mixing currencies is refused, never coerced")
    void currencyMismatch() {
        assertThatThrownBy(() -> Money.of(1, "AED").add(Money.of(1, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("overflow throws instead of silently wrapping")
    void overflow() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "AED").add(Money.of(1, "AED")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("a currency must be an ISO 4217 alpha-3 code")
    void currencyCode() {
        assertThatThrownBy(() -> Money.of(1, "AE")).isInstanceOf(IllegalArgumentException.class);
    }
}
