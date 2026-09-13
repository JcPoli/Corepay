package dev.jcpolicarpio.corepay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    @Test
    @DisplayName("the happy path is the only forward path")
    void forward() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.VALIDATED)).isTrue();
        assertThat(PaymentStatus.VALIDATED.canTransitionTo(PaymentStatus.POSTED)).isTrue();
        assertThat(PaymentStatus.POSTED.canTransitionTo(PaymentStatus.SETTLED)).isTrue();
    }

    @Test
    @DisplayName("nothing may be posted twice or skip validation")
    void noDoublePosting() {
        assertThat(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.POSTED)).isFalse();
        assertThat(PaymentStatus.POSTED.canTransitionTo(PaymentStatus.POSTED)).isFalse();
        assertThat(PaymentStatus.SETTLED.canTransitionTo(PaymentStatus.POSTED)).isFalse();
    }

    @Test
    @DisplayName("a settled payment is corrected by reversal, and reversal is final")
    void reversal() {
        assertThat(PaymentStatus.SETTLED.canTransitionTo(PaymentStatus.REVERSED)).isTrue();
        assertThat(PaymentStatus.REVERSED.isTerminal()).isTrue();
        assertThat(PaymentStatus.REJECTED.isTerminal()).isTrue();
        assertThatThrownBy(() -> PaymentStatus.SETTLED.requireTransitionTo(PaymentStatus.VALIDATED))
                .isInstanceOf(InvalidTransitionException.class);
    }
}
