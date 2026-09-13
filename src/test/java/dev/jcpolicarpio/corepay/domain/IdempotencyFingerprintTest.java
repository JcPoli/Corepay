package dev.jcpolicarpio.corepay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyFingerprintTest {

    @Test
    @DisplayName("same payload hashes the same, one digit changes everything")
    void fingerprint() {
        String a = IdempotencyFingerprint.of("AE01|AE02|125000|AED||");
        String b = IdempotencyFingerprint.of("AE01|AE02|125000|AED||");
        String c = IdempotencyFingerprint.of("AE01|AE02|125001|AED||");

        assertThat(a).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(b).isNotEqualTo(c);
    }
}
