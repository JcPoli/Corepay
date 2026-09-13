package dev.jcpolicarpio.corepay.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment lifecycle. Transitions are explicit because in a payments system the
 * illegal ones are the expensive ones: nothing may be posted twice, and a
 * settled payment is corrected by reversal, never by editing history.
 */
public enum PaymentStatus {
    INITIATED,
    VALIDATED,
    POSTED,
    SETTLED,
    REJECTED,
    REVERSED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED;

    static {
        ALLOWED = Map.of(
                INITIATED, Collections.unmodifiableSet(EnumSet.of(VALIDATED, REJECTED)),
                VALIDATED, Collections.unmodifiableSet(EnumSet.of(POSTED, REJECTED)),
                POSTED, Collections.unmodifiableSet(EnumSet.of(SETTLED, REVERSED)),
                SETTLED, Collections.unmodifiableSet(EnumSet.of(REVERSED)),
                REJECTED, Collections.<PaymentStatus>emptySet(),
                REVERSED, Collections.<PaymentStatus>emptySet());
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return ALLOWED.getOrDefault(this, Collections.emptySet()).contains(next);
    }

    public void requireTransitionTo(PaymentStatus next) {
        if (!canTransitionTo(next)) {
            throw new InvalidTransitionException(this, next);
        }
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Collections.emptySet()).isEmpty();
    }
}
