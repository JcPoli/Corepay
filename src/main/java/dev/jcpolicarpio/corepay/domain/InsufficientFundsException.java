package dev.jcpolicarpio.corepay.domain;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(Money available, Money requested) {
        super("INSUFFICIENT_FUNDS",
                "Available balance " + available.minorUnits() + " " + available.currency()
                        + " cannot cover " + requested.minorUnits() + " " + requested.currency());
    }
}
