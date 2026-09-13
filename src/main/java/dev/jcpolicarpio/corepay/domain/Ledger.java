package dev.jcpolicarpio.corepay.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Balance derivation. There is deliberately no mutable balance column
 * anywhere in this system: a balance is a function of the postings, so it can
 * always be recomputed and reconciled. Caching it is an optimisation for
 * later, and one that must never become the source of truth.
 */
public final class Ledger {

    private Ledger() {
    }

    public static Money balanceOf(
            UUID accountId, AccountType type, String currency, List<Posting> postings) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        long total = 0L;
        for (Posting posting : postings) {
            if (!posting.accountId().equals(accountId)) {
                continue;
            }
            if (!posting.amount().currency().equals(currency)) {
                throw new CurrencyMismatchException(currency, posting.amount().currency());
            }
            long signed = Math.multiplyExact(
                    type.signOf(posting.direction()), posting.amount().minorUnits());
            total = Math.addExact(total, signed);
        }
        return Money.of(total, currency);
    }

    /**
     * Guard applied before a debit is posted. Suspense and nostro accounts are
     * allowed to go negative during the day; customer accounts are not, unless
     * an overdraft limit says otherwise.
     */
    public static void requireSufficientFunds(
            AccountType type, Money available, Money requested, long overdraftLimitMinorUnits) {
        if (type != AccountType.CUSTOMER) {
            return;
        }
        Money floor = Money.of(-Math.abs(overdraftLimitMinorUnits), available.currency());
        Money after = available.subtract(requested);
        if (floor.isGreaterThan(after)) {
            throw new InsufficientFundsException(available, requested);
        }
    }
}
