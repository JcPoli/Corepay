package dev.jcpolicarpio.corepay.domain;

/**
 * Which side of the ledger an account normally sits on, and therefore which
 * direction increases its balance.
 */
public enum AccountType {
    /** Customer deposits: a liability of the bank, so credits increase it. */
    CUSTOMER(Direction.CREDIT),
    /** The bank's own holding account at a correspondent: an asset. */
    NOSTRO(Direction.DEBIT),
    /** Parking account for funds in flight or unresolved items. */
    SUSPENSE(Direction.CREDIT),
    /** Fee and charge income. */
    INCOME(Direction.CREDIT);

    private final Direction normalSide;

    AccountType(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }

    /** Signed effect of a posting on this account's balance. */
    public long signOf(Direction direction) {
        return direction == normalSide ? 1L : -1L;
    }
}
