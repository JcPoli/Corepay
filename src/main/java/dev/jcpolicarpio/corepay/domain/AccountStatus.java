package dev.jcpolicarpio.corepay.domain;

public enum AccountStatus {
    ACTIVE,
    /** Can receive but not send: used while a review is in progress. */
    FROZEN,
    CLOSED;

    public boolean canDebit() {
        return this == ACTIVE;
    }

    public boolean canCredit() {
        return this == ACTIVE || this == FROZEN;
    }
}
