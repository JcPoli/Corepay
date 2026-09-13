package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.domain.DomainException;

public class InvalidPaymentException extends DomainException {

    public InvalidPaymentException(String message) {
        super("INVALID_PAYMENT", message);
    }
}
