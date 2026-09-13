package dev.jcpolicarpio.corepay.domain;

/**
 * The two sides of a double-entry posting.
 *
 * Debit and credit are not "money in" and "money out" — what they mean to a
 * balance depends on the account's normal side. A customer deposit account is
 * a liability of the bank, so a credit increases what the customer holds.
 */
public enum Direction {
    DEBIT,
    CREDIT;

    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
