package dev.jcpolicarpio.corepay.domain;

public class InvalidTransitionException extends DomainException {

    public InvalidTransitionException(PaymentStatus from, PaymentStatus to) {
        super("INVALID_TRANSITION", "A payment cannot move from " + from + " to " + to);
    }
}
