package dev.jcpolicarpio.corepay.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One leg of a journal entry: this account, this side, this amount.
 * Amounts are always positive; the direction carries the sign.
 */
public record Posting(UUID accountId, Direction direction, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Posting amounts must be positive; use the direction to express the side");
        }
    }

    public static Posting debit(UUID accountId, Money amount) {
        return new Posting(accountId, Direction.DEBIT, amount);
    }

    public static Posting credit(UUID accountId, Money amount) {
        return new Posting(accountId, Direction.CREDIT, amount);
    }

    /** Signed amount in minor units, debits positive. */
    public long signedMinorUnits() {
        return direction == Direction.DEBIT ? amount.minorUnits() : -amount.minorUnits();
    }
}
