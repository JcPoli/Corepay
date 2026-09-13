package dev.jcpolicarpio.corepay.domain;

public class AccountUnavailableException extends DomainException {

    public AccountUnavailableException(String accountNumber, AccountStatus status, Direction direction) {
        super("ACCOUNT_UNAVAILABLE",
                "Account " + accountNumber + " is " + status + " and cannot be "
                        + (direction == Direction.DEBIT ? "debited" : "credited"));
    }
}
